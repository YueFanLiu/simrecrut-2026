"""Check numeric boundaries and training controls with synthetic fixtures."""

import json
from decimal import Decimal
from pathlib import Path

import pytest
import torch
from torch import nn

from simrecrut_ml.training.engine import (
    ModelPartition, TrainingSettings, fit_development_run, fit_matched_seed_pairs,
)
from simrecrut_ml.training.features import (
    PROFESSIONAL_ORDER, RESEARCH_ORDER, SCHEMA_VERSION, encode_model_inputs, professional_block,
)
from simrecrut_ml.training.metrics import binary_metrics, select_validation_cutoff
from simrecrut_ml.training.network import RecruitmentNetwork
from simrecrut_ml.training.splits import grouped_profile_split


UNKNOWN = {family: "unknown" for family in RESEARCH_ORDER}


def sample_partition(prefix, model_type="neutral", rows=8):
    inputs = []
    labels = []
    for index in range(rows):
        block = professional_block(
            ["0.8" if index % 2 else "0.2"] * 5,
            ["0.2"] * 5, [True] * 5, True,
        )
        neutral, biased = encode_model_inputs(block, UNKNOWN)
        inputs.append(neutral if model_type == "neutral" else biased)
        labels.append([index % 2])
    return ModelPartition(
        torch.tensor(inputs, dtype=torch.float32),
        torch.tensor(labels, dtype=torch.float32),
        tuple(f"{prefix}-{index}" for index in range(rows)),
    )


@pytest.fixture(autouse=True)
def small_cpu_runs():
    original = torch.get_num_threads()
    torch.set_num_threads(1)
    yield
    torch.set_num_threads(original)


def test_shared_contract_matches_numeric_order_and_unknown_encoding():
    root = Path(__file__).resolve().parents[3]
    schema_path = root / "contracts/feature-schemas/professional-research-v1.json"
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    assert schema["schema_version"] == SCHEMA_VERSION
    assert schema["professional_order"] == list(PROFESSIONAL_ORDER)
    assert schema["research_order"] == {key: list(value) for key, value in RESEARCH_ORDER.items()}
    block = professional_block(["0.5"] * 5, ["0.2"] * 5, [True] * 5, True)
    neutral, biased = encode_model_inputs(block, UNKNOWN)
    assert len(neutral) == len(biased) == 32 and neutral[:16] == biased[:16]
    assert neutral[16:] == (0.0,) * 16
    assert sum(biased[16:]) == 4
    assert [index for index, value in enumerate(biased[16:]) if value] == [2, 5, 8, 15]


def test_rule_rounds_each_contribution_half_up_without_normalizing_weights():
    block = professional_block(
        ["0.33333335", "0.0000001", "0", "0", "0"],
        ["0.5", "0.5", "0", "0", "0"], [True, True, False, False, False], True,
    )
    assert block[0] == Decimal("0.3333334")
    assert block[10] == Decimal("0.1666667")
    assert block[11] == Decimal("0.0000001")
    assert sum(block[10:15]) == Decimal("0.1666668")
    with pytest.raises(ValueError, match="precision"):
        professional_block(["1"] * 5, ["0.20000001"] * 5, [True] * 5, True)
    with pytest.raises(ValueError, match="sum"):
        professional_block(["1"] * 5, ["0.1"] * 5, [True] * 5, True)


def test_active_zero_weight_is_valid_but_inactive_observed_match_is_not():
    professional_block(["0.8", "0.9", "0", "0", "0"],
                       ["1", "0", "0", "0", "0"], [True, True, False, False, False], True)
    with pytest.raises(ValueError, match="inactive"):
        professional_block(["0.8", "0.9", "0", "0", "0"],
                           ["1", "0", "0", "0", "0"], [True, False, False, False, False], True)


@pytest.mark.parametrize("value", [None, 0.2, "NaN", "-0.1", "1.1"])
def test_unresolved_or_binary_float_rule_inputs_are_rejected(value):
    with pytest.raises(ValueError):
        professional_block([value] * 5, ["0.2"] * 5, [True] * 5, True)


def test_hand_built_decimal_block_cannot_bypass_feature_contract():
    with pytest.raises(ValueError, match="weights"):
        encode_model_inputs((Decimal(0),) * 16, UNKNOWN)
    block = list(professional_block(["0.5"] * 5, ["0.2"] * 5, [True] * 5, True))
    block[10] = Decimal("0.8")
    with pytest.raises(ValueError, match="products"):
        encode_model_inputs(block, UNKNOWN)


def test_grouped_split_has_500_people_and_keeps_pilot_out_of_final_test():
    ids = [f"profile-{index:03}" for index in range(500)]
    result = grouped_profile_split(ids, 101, ids[:20])
    assert {key: len(value) for key, value in result.items()} == {
        "train": 300, "validation": 100, "test": 100,
    }
    assert result == grouped_profile_split(list(reversed(ids)), 101, ids[:20])
    assert not set(ids[:20]) & set(result["test"])
    assert sum(len(value) for value in result.values()) == len(set().union(*result.values()))
    with pytest.raises(ValueError, match="reserved"):
        grouped_profile_split(ids[:10], 101, ids[:9])


def test_network_has_independent_parameters_raw_logits_and_eval_dropout_off():
    first, second = RecruitmentNetwork(), RecruitmentNetwork()
    assert sum(parameter.numel() for parameter in first.parameters()) == 673
    assert not {id(parameter) for parameter in first.parameters()} & {
        id(parameter) for parameter in second.parameters()
    }
    with torch.no_grad():
        first.layers[-1].weight.zero_()
        first.layers[-1].bias.fill_(5)
    first.eval()
    inputs = sample_partition("train").inputs
    logits = first(inputs)
    assert bool((logits == 5).all())
    assert torch.equal(logits, first(inputs))
    assert bool(torch.isfinite(nn.BCEWithLogitsLoss()(logits, torch.ones_like(logits))))


def test_confusion_metrics_and_disconnected_optima_do_not_get_averaged():
    metrics = binary_metrics([0, 0, 1, 1], [0, 1, 0, 1])
    assert metrics["balanced_accuracy"] == metrics["macro_f1"] == 0.5
    result = select_validation_cutoff([0.1, 0.2, 0.3, 0.4], [0, 1, 0, 1])
    assert result.threshold is None
    assert len(result.optimal_intervals) == 2
    assert not any(lower < 0.25 < upper for lower, upper in result.optimal_intervals)
    with pytest.raises(ValueError, match="classes"):
        select_validation_cutoff([0.2, 0.3], [1, 1])


def test_single_optimal_interval_uses_its_midpoint():
    result = select_validation_cutoff([0.1, 0.2, 0.8, 0.9], [0, 0, 1, 1])
    assert result.threshold == 0.5
    assert result.optimal_intervals == ((0.2, 0.8),)
    assert len(result.candidates) >= 5


def test_true_balanced_accuracy_tie_is_resolved_by_macro_f1_despite_float_error():
    result = select_validation_cutoff(
        [0.1, 0.4, 0.4, 0.4, 0.4, 0.8, 0.8, 0.8],
        [0, 1, 0, 0, 0, 1, 0, 0],
    )
    assert result.threshold == pytest.approx(0.6)
    assert result.optimal_intervals == ((0.4, 0.8),)
    earlier = next(row for row in result.candidates if row["threshold"] == 0.25)
    later = next(row for row in result.candidates if row["threshold"] == result.threshold)
    assert earlier["balanced_accuracy"] == pytest.approx(later["balanced_accuracy"])
    assert earlier["macro_f1"] < later["macro_f1"]


def test_training_restores_minimum_validation_loss_and_stops_after_ten_stale(monkeypatch):
    snapshots = []
    losses = iter([1.0, 0.8] + [0.9] * 20)

    def validation_loss(model, data, loss):
        snapshots.append({name: value.clone() for name, value in model.state_dict().items()})
        model.eval()
        return next(losses)

    monkeypatch.setattr("simrecrut_ml.training.engine._validation_loss", validation_loss)
    result = fit_development_run(
        sample_partition("train"), sample_partition("validation"), "neutral", 11,
        TrainingSettings(max_epochs=20),
    )
    assert result.best_epoch == 2 and len(result.history) == 12
    assert result.settings == TrainingSettings(max_epochs=20)
    assert result.dependency_versions["torch"] == str(torch.__version__)
    assert not result.model.training
    assert all(torch.equal(value, snapshots[1][name])
               for name, value in result.model.state_dict().items())
    assert any(not torch.equal(snapshots[1][name], snapshots[-1][name]) for name in snapshots[1])


def test_leakage_research_mask_and_unusable_class_are_blocked():
    train, validation = sample_partition("train"), sample_partition("validation")
    leaked = ModelPartition(validation.inputs, validation.labels, train.profile_ids)
    with pytest.raises(ValueError, match="person"):
        fit_development_run(train, leaked, "neutral", 11, TrainingSettings(max_epochs=1))
    with pytest.raises(ValueError, match="withhold"):
        sample_partition("train", "biased").validate("neutral")
    absent = ModelPartition(train.inputs, torch.ones_like(train.labels), train.profile_ids)
    with pytest.raises(ValueError, match="classes"):
        absent.validate("neutral")


def test_failed_mandatory_condition_cannot_have_positive_rule_target():
    data = sample_partition("train")
    inputs = data.inputs.clone()
    inputs[1, 15] = 0
    with pytest.raises(ValueError, match="mandatory"):
        ModelPartition(inputs, data.labels, data.profile_ids).validate("neutral")
    labels = data.labels.clone()
    labels[1, 0] = 0
    ModelPartition(inputs, labels, data.profile_ids).validate("neutral")


def test_five_matched_runs_keep_separate_models_and_identical_professional_order():
    results = fit_matched_seed_pairs(
        sample_partition("train"), sample_partition("validation"),
        sample_partition("train", "biased"), sample_partition("validation", "biased"),
        (11, 23, 37, 53, 71), TrainingSettings(max_epochs=1),
    )
    assert len(results) == 5
    assert all(neutral.seed == biased.seed for neutral, biased in results)
    assert len({id(run.model) for pair in results for run in pair}) == 10
    assert all(len(run.history) == 1 for pair in results for run in pair)
