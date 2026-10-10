"""Compare binary predictions and retain every tested cutoff candidate."""

from dataclasses import dataclass
from fractions import Fraction
from math import isfinite
from typing import Sequence


@dataclass(frozen=True)
class CutoffSelection:
    """Describe optimal cutoff regions, including unresolved equal optima.

    ``threshold`` is None when disconnected optimal regions require a
    protocol choice. Candidates retain counts and metrics. A chosen
    midpoint is rescored so a gap between optima cannot be averaged into
    a worse cutoff. This object never supplies the separate rule cutoff.
    """

    threshold: float | None
    optimal_intervals: tuple[tuple[float, float], ...]
    candidates: tuple[dict, ...]


def binary_metrics(labels: Sequence[int], decisions: Sequence[int]) -> dict:
    """Return counts, accuracy, balanced accuracy, and two-class macro-F1.

    Labels and decisions are same-order binary sequences. Accept is the
    positive class. Both reference classes are required for meaningful
    balanced accuracy; reject empty, mismatched, or nonbinary sequences.
    """
    if len(labels) != len(decisions) or not labels:
        raise ValueError("Binary metrics need equally sized, nonempty sequences.")
    if any(value not in (0, 1) for value in (*labels, *decisions)):
        raise ValueError("Metrics need binary labels and predictions.")
    if set(labels) != {0, 1}:
        raise ValueError("Both reference classes are required; expand the reviewed sample.")
    tp = sum(label == 1 and decision == 1 for label, decision in zip(labels, decisions))
    tn = sum(label == 0 and decision == 0 for label, decision in zip(labels, decisions))
    fp = sum(label == 0 and decision == 1 for label, decision in zip(labels, decisions))
    fn = sum(label == 1 and decision == 0 for label, decision in zip(labels, decisions))
    return {
        "samples": len(labels), "tp": tp, "tn": tn, "fp": fp, "fn": fn,
        "accuracy": (tp + tn) / len(labels),
        "balanced_accuracy": (tp / (tp + fn) + tn / (tn + fp)) / 2,
        "macro_f1": (2 * tp / (2 * tp + fp + fn) + 2 * tn / (2 * tn + fp + fn)) / 2,
    }


def _ranking(metrics: dict) -> tuple[Fraction, Fraction]:
    tp, tn, fp, fn = (metrics[key] for key in ("tp", "tn", "fp", "fn"))
    # Float representations can break true ties before macro-F1 is checked.
    balanced = (Fraction(tp, tp + fn) + Fraction(tn, tn + fp)) / 2
    macro_f1 = (Fraction(2 * tp, 2 * tp + fp + fn)
                + Fraction(2 * tn, 2 * tn + fp + fn)) / 2
    return balanced, macro_f1


def select_validation_cutoff(scores: Sequence[float], labels: Sequence[int]) -> CutoffSelection:
    """Find balanced-accuracy optima, then macro-F1 optima, on validation.

    Test midpoints of adjacent distinct scores and boundaries zero/one.
    Equality accepts. Join only adjacent tied optimal regions. Select
    their midpoint when there is one region; report disconnected equal
    optima instead of inventing a tie policy. Scores must be finite and
    within [0,1], and labels must support both classes. Never pass test
    scores or rule-calibration reviewer labels to this model-cutoff call.
    Rank using exact fractions from confusion counts to preserve true
    ties; retain floating metrics and full-precision model cutoffs.
    """
    if len(scores) != len(labels) or not scores:
        raise ValueError("Cutoff selection needs same-order scores and labels.")
    if any(not isfinite(score) or not 0 <= score <= 1 for score in scores):
        raise ValueError("Validation scores must be finite values between zero and one.")
    distinct = sorted(set(scores))
    intervals = [(0.0, distinct[0])]
    intervals.extend(zip(distinct[:-1], distinct[1:]))
    if distinct[-1] < 1:
        intervals.append((distinct[-1], 1.0))
    thresholds = {0.0, 1.0}
    thresholds.update((lower + upper) / 2 for lower, upper in intervals)
    candidates = tuple(
        {"threshold": threshold, **binary_metrics(labels, [int(s >= threshold) for s in scores])}
        for threshold in sorted(thresholds)
    )
    best = max(_ranking(row) for row in candidates)
    optimal = []
    for lower, upper in intervals:
        threshold = (lower + upper) / 2
        metrics = binary_metrics(labels, [int(score >= threshold) for score in scores])
        if _ranking(metrics) == best:
            if optimal and optimal[-1][1] == lower:
                optimal[-1] = (optimal[-1][0], upper)
            else:
                optimal.append((lower, upper))
    for boundary in (0.0, 1.0):
        row = next(item for item in candidates if item["threshold"] == boundary)
        if _ranking(row) == best:
            if not any(lower <= boundary <= upper for lower, upper in optimal):
                optimal.append((boundary, boundary))
    optimal.sort()
    threshold = sum(optimal[0]) / 2 if len(optimal) == 1 else None
    if threshold is not None:
        metrics = binary_metrics(labels, [int(score >= threshold) for score in scores])
        if _ranking(metrics) != best:
            raise ValueError("The tied-interval midpoint does not preserve the selected metrics.")
    return CutoffSelection(threshold, tuple(optimal), candidates)
