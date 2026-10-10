"""Build the source-specified numeric blocks from already resolved matches."""

from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
from typing import Mapping, Sequence


SCHEMA_VERSION = "professional-research-v1"
CRITERIA = ("skills", "experience", "education", "languages", "projects")
PROFESSIONAL_ORDER = tuple(
    f"{prefix}_{area}" for prefix in ("match", "weight", "contribution") for area in CRITERIA
) + ("mandatory_pass",)
RESEARCH_ORDER = {
    "school": ("prestigious", "ordinary", "unknown"),
    "referral": ("yes", "no", "unknown"),
    "gender": ("woman", "man", "unknown"),
    "ethnicity": (
        "east_asian", "south_asian", "black", "white",
        "middle_eastern_or_north_african", "mixed_or_other", "unknown",
    ),
}
QUANTUM = Decimal("0.0000001")


def _decimal(value: str | int | Decimal) -> Decimal:
    if isinstance(value, bool) or not isinstance(value, (str, int, Decimal)):
        raise ValueError("Rule inputs must be decimal text, integers, or Decimal values.")
    try:
        number = Decimal(value)
        if not number.is_finite() or not Decimal(0) <= number <= Decimal(1):
            raise ValueError("Rule inputs must be finite values between zero and one.")
        return number
    except InvalidOperation as error:
        raise ValueError("Rule inputs must contain a valid decimal value.") from error


def professional_block(
    matches: Sequence[str | int | Decimal],
    weights: Sequence[str | int | Decimal],
    active: Sequence[bool],
    mandatory_pass: bool,
) -> tuple[Decimal, ...]:
    """Create the 16-value professional block in the fixed schema order.

    Matches are rounded to seven places using HALF_UP. Weights must
    already have at most seven nonzero decimal places and sum to one;
    they are never normalized. Each product is rounded separately.
    Explicit inactive areas require a zero match and weight. An active
    area may have weight zero. All active matches must be resolved first.
    Reject binary floats, unknowns, invalid flags, and invalid ranges.
    This function does not infer matches from CVs or job descriptions.
    """
    if len(matches) != 5 or len(weights) != 5 or len(active) != 5:
        raise ValueError("Matches, weights, and active flags must each contain five values.")
    if any(not isinstance(flag, bool) for flag in active) or not isinstance(mandatory_pass, bool):
        raise ValueError("Active and resolved mandatory flags must be booleans.")
    if not any(active):
        raise ValueError("At least one assessment area must be active.")
    raw_matches = tuple(_decimal(value) for value in matches)
    raw_weights = tuple(_decimal(value) for value in weights)
    fixed_weights = tuple(value.quantize(QUANTUM, rounding=ROUND_HALF_UP) for value in raw_weights)
    if raw_weights != fixed_weights:
        raise ValueError("Weights contain excess nonzero decimal precision.")
    if sum(fixed_weights) != Decimal(1):
        raise ValueError("HR weights must sum to one exactly.")
    for index, assessed in enumerate(active):
        if not assessed and (raw_matches[index] != 0 or fixed_weights[index] != 0):
            raise ValueError("An inactive area requires a zero match and weight.")
    fixed_matches = tuple(value.quantize(QUANTUM, rounding=ROUND_HALF_UP) for value in raw_matches)
    contributions = tuple(
        (match * weight).quantize(QUANTUM, rounding=ROUND_HALF_UP)
        for match, weight in zip(fixed_matches, fixed_weights, strict=True)
    )
    return fixed_matches + fixed_weights + contributions + (Decimal(int(mandatory_pass)),)


def encode_model_inputs(
    professional: Sequence[Decimal], categories: Mapping[str, str],
) -> tuple[tuple[float, ...], tuple[float, ...]]:
    """Return Neutral and Biased 32-value inputs with identical first blocks.

    The professional argument must be the resolved 16-value block.
    Research values must be explicitly provided study categories in the
    fixed family order. Neutral receives sixteen zeros; Biased receives
    four one-hot families. Unknown selects a category rather than a mask.
    Labels, synthetic effects, source text, and names have no input slot.
    Return floating values for subsequent float32 tensor construction.
    """
    if len(professional) != 16 or any(not isinstance(value, Decimal) for value in professional):
        raise ValueError("Use the validated 16-value decimal professional block.")
    if set(categories) != set(RESEARCH_ORDER):
        raise ValueError("Provide all four research families explicitly.")
    fixed = tuple(_decimal(value) for value in professional)
    if any(value != value.quantize(QUANTUM, rounding=ROUND_HALF_UP) for value in fixed[:15]):
        raise ValueError("Professional matches, weights, and products require fixed precision.")
    if sum(fixed[5:10]) != 1 or fixed[15] not in (0, 1):
        raise ValueError("Professional weights or mandatory status violate the schema.")
    expected = tuple(
        (match * weight).quantize(QUANTUM, rounding=ROUND_HALF_UP)
        for match, weight in zip(fixed[:5], fixed[5:10], strict=True)
    )
    if fixed[10:15] != expected:
        raise ValueError("Professional products disagree with rounded rule contributions.")
    values = tuple(float(value) for value in fixed)
    research = []
    for family, options in RESEARCH_ORDER.items():
        selected = categories[family]
        if not isinstance(selected, str) or selected not in options:
            raise ValueError("A research value is outside the fixed category schema.")
        research.extend(float(option == selected) for option in options)
    return values + (0.0,) * 16, values + tuple(research)
