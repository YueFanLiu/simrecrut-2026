"""Fit independent development runs without loading or selecting on final test."""

from copy import deepcopy
from dataclasses import dataclass
import platform

import torch
from torch import nn
from torch.utils.data import DataLoader, TensorDataset

from .metrics import CutoffSelection, binary_metrics, select_validation_cutoff
from .network import RecruitmentNetwork


@dataclass(frozen=True)
class TrainingSettings:
    """Describe source starting settings or its limited validation search.

    Epoch limits can be shortened for synthetic checks. Real experiments
    record this object with the run; the source maximum is 100 and
    no-improvement patience is 10. HR weights are input values, not these
    optimizer parameters. CPU is used for this small development runner.
    """

    learning_rate: float = 0.001
    weight_decay: float = 0.0001
    dropout: float = 0.1
    batch_size: int = 32
    max_epochs: int = 100
    patience: int = 10

    def validate(self) -> None:
        """Reject settings outside the source's recorded search candidates."""
        if any(isinstance(value, bool) for value in (
            self.learning_rate, self.weight_decay, self.dropout,
        )):
            raise ValueError("Optimizer settings and dropout must be numeric values.")
        if self.learning_rate not in (0.0003, 0.001, 0.003):
            raise ValueError("Learning rate is outside the source's proposed validation search.")
        if self.weight_decay not in (0.0, 0.0001) or self.dropout not in (0.0, 0.1):
            raise ValueError("Weight decay or dropout is outside the proposed validation search.")
        if self.batch_size != 32 or self.patience != 10:
            raise ValueError("The starting protocol uses batches of 32 and patience 10.")
        if isinstance(self.max_epochs, bool) or not isinstance(self.max_epochs, int):
            raise ValueError("Epoch count must be an integer between one and 100.")
        if not 1 <= self.max_epochs <= 100:
            raise ValueError("Epoch count must be an integer between one and 100.")


@dataclass(frozen=True)
class ModelPartition:
    """Hold one model's float32 inputs/labels and aligned base identities.

    Inputs are N-by-32 and labels N-by-1, already generated from reviewed
    facts and a frozen label protocol. Multiple rows may share a person;
    the identity sequence has one entry per row. Source text has no slot.
    Validation checks shape and numerical schema, not human truth.
    """

    inputs: torch.Tensor
    labels: torch.Tensor
    profile_ids: tuple[str, ...]

    def validate(self, model_type: str) -> None:
        """Reject malformed features, absent classes, and row misalignment."""
        x, y = self.inputs, self.labels
        if x.ndim != 2 or x.shape[1] != 32 or x.shape[0] == 0:
            raise ValueError("A partition needs nonempty N-by-32 input tensors.")
        if y.shape != (x.shape[0], 1) or x.dtype != torch.float32 or y.dtype != torch.float32:
            raise ValueError("Use float32 N-by-32 inputs and N-by-1 binary labels.")
        if x.device.type != "cpu" or y.device.type != "cpu":
            raise ValueError("This development runner expects CPU tensors.")
        if not bool(torch.isfinite(x).all() and torch.isfinite(y).all()):
            raise ValueError("Features and labels must be finite.")
        if not bool(((0 <= x) & (x <= 1)).all()) or set(y.flatten().tolist()) != {0.0, 1.0}:
            raise ValueError("Features must lie in [0,1] and labels must support both classes.")
        if len(self.profile_ids) != x.shape[0] or any(
            not isinstance(value, str) or not value for value in self.profile_ids
        ):
            raise ValueError("Partition identities must align with every feature row.")
        if not bool(((x[:, 15] == 0) | (x[:, 15] == 1)).all()):
            raise ValueError("Mandatory status must be resolved to zero or one.")
        if bool(((x[:, 15] == 0) & (y[:, 0] == 1)).any()):
            raise ValueError("Rule-generated labels cannot accept a failed mandatory condition.")
        if not torch.allclose(x[:, 5:10].sum(dim=1), torch.ones(x.shape[0]), atol=1e-6, rtol=0):
            raise ValueError("Fixed HR weights must sum to one.")
        if not torch.allclose(
            x[:, 10:15], x[:, :5] * x[:, 5:10], atol=1e-6, rtol=0,
        ):
            raise ValueError("Contributions must agree with the fixed matches and weights.")
        if model_type == "neutral":
            if bool(torch.count_nonzero(x[:, 16:])):
                raise ValueError("Neutral inputs must withhold all research values.")
        elif model_type == "biased":
            for start, end in ((16, 19), (19, 22), (22, 25), (25, 32)):
                family = x[:, start:end]
                if not bool(((family == 0) | (family == 1)).all()):
                    raise ValueError("Biased categories must be one-hot values.")
                if not bool((family.sum(dim=1) == 1).all()):
                    raise ValueError(
                        "Every research family needs one category, including unknown.",
                    )
        else:
            raise ValueError("Model type must be neutral or biased.")


@dataclass
class DevelopmentRun:
    """Hold the restored best model and validation-only run evidence.

    Cutoff may remain unresolved when equally good regions are separated.
    No final-test score or release approval is created by this runner.
    Checkpoints and evidence must be saved only in ignored run/package paths.
    """

    model: RecruitmentNetwork
    model_type: str
    seed: int
    settings: TrainingSettings
    dependency_versions: dict[str, str]
    best_epoch: int
    history: list[dict]
    validation_metrics_at_half: dict
    cutoff: CutoffSelection


def _validation_loss(model: RecruitmentNetwork, data: ModelPartition, loss: nn.Module) -> float:
    model.eval()
    with torch.no_grad():
        return float(loss(model(data.inputs), data.labels))


def fit_development_run(
    train: ModelPartition, validation: ModelPartition, model_type: str,
    seed: int, settings: TrainingSettings | None = None,
) -> DevelopmentRun:
    """Train one seed on reviewed matrices and restore its best checkpoint.

    This low-level runner accepts no raw records and no final-test data.
    Its caller must first pass the separate approval/readiness gates and
    freeze the label protocol. Tests use synthetic matrices explicitly.
    Require disjoint original people, both label classes, finite schema
    values, and float32 tensors. Use raw logits with BCEWithLogitsLoss,
    Adam, shuffled batches, and validation with dropout/gradients disabled.
    Keep the minimum validation-loss state and stop after ten stale epochs.
    Return the model, history, validation metrics, and cutoff candidates;
    do not write files or choose a winning seed. No scientific validity
    follows solely from passing the numerical checks here.
    """
    settings = settings or TrainingSettings()
    settings.validate()
    train.validate(model_type)
    validation.validate(model_type)
    if set(train.profile_ids) & set(validation.profile_ids):
        raise ValueError("Every version of a person must remain in one partition.")
    if isinstance(seed, bool) or not isinstance(seed, int) or seed < 0:
        raise ValueError("Record a nonnegative integer initialization seed.")
    with torch.random.fork_rng(devices=[]):
        torch.manual_seed(seed)
        model = RecruitmentNetwork(settings.dropout)
        optimizer = torch.optim.Adam(
            model.parameters(), lr=settings.learning_rate, weight_decay=settings.weight_decay,
        )
        loss = nn.BCEWithLogitsLoss()
        generator = torch.Generator().manual_seed(seed)
        batches = DataLoader(
            TensorDataset(train.inputs.detach(), train.labels.detach()),
            batch_size=settings.batch_size, shuffle=True, generator=generator,
        )
        best_loss, best_epoch, stale = float("inf"), 0, 0
        best_state, history = None, []
        for epoch in range(1, settings.max_epochs + 1):
            model.train()
            total_loss = 0.0
            for inputs, labels in batches:
                optimizer.zero_grad(set_to_none=True)
                batch_loss = loss(model(inputs), labels)
                if not bool(torch.isfinite(batch_loss)):
                    raise ValueError("Training loss is nonfinite; no valid checkpoint exists.")
                batch_loss.backward()
                optimizer.step()
                total_loss += float(batch_loss.detach()) * inputs.shape[0]
            validation_loss = _validation_loss(model, validation, loss)
            if not torch.isfinite(torch.tensor(validation_loss)):
                raise ValueError("Validation loss is nonfinite; no valid checkpoint exists.")
            history.append({
                "epoch": epoch, "train_loss": total_loss / train.inputs.shape[0],
                "validation_loss": validation_loss,
            })
            if validation_loss < best_loss:
                best_loss, best_epoch, stale = validation_loss, epoch, 0
                # A shallow state copy would change as the next epochs train.
                best_state = deepcopy(model.state_dict())
            else:
                stale += 1
            if stale == settings.patience:
                break
        model.load_state_dict(best_state)
        model.eval()
        with torch.no_grad():
            scores = torch.sigmoid(model(validation.inputs)).flatten().tolist()
    labels = [int(value) for value in validation.labels.flatten().tolist()]
    return DevelopmentRun(
        model, model_type, seed, settings,
        {"python": platform.python_version(), "torch": str(torch.__version__)},
        best_epoch, history,
        binary_metrics(labels, [int(score >= 0.5) for score in scores]),
        select_validation_cutoff(scores, labels),
    )


def fit_matched_seed_pairs(
    neutral_train: ModelPartition, neutral_validation: ModelPartition,
    biased_train: ModelPartition, biased_validation: ModelPartition,
    seeds: tuple[int, ...], settings: TrainingSettings | None = None,
) -> list[tuple[DevelopmentRun, DevelopmentRun]]:
    """Fit separate models on one shared partition and five recorded seeds.

    Corresponding professional blocks and profile row order must match.
    Each model uses its own labels and parameters. Return all five pairs
    without selecting a best seed. Protocol approval and data readiness
    remain caller requirements; final test is a separate frozen stage.
    """
    if len(seeds) != 5 or len(set(seeds)) != 5:
        raise ValueError("Use five distinct matched seeds for the selected-settings comparison.")
    for neutral, biased in (
        (neutral_train, biased_train), (neutral_validation, biased_validation),
    ):
        if neutral.profile_ids != biased.profile_ids or not torch.equal(
            neutral.inputs[:, :16], biased.inputs[:, :16],
        ):
            raise ValueError(
                "Neutral and Biased must share professional inputs and row identities.",
            )
    return [(
        fit_development_run(neutral_train, neutral_validation, "neutral", seed, settings),
        fit_development_run(biased_train, biased_validation, "biased", seed, settings),
    ) for seed in seeds]
