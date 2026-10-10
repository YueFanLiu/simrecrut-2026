"""Assign reviewed base identities before constructing experiment variants."""

import random
from collections.abc import Sequence


def grouped_profile_split(
    base_profile_ids: Sequence[str], seed: int, pilot_profile_ids: Sequence[str] = (),
) -> dict[str, list[str]]:
    """Assign unique people 60/20/20 while reserving calibration people.

    IDs must already represent reviewed, deduplicated people. Sort them
    before seeded sampling so source row order cannot change assignment.
    Calibration pilot people can enter train or validation, never test.
    Return partition ID lists; all later variants inherit this mapping.
    Fractions use floor for train and validation and the remainder for
    test. Reject too few people, unknown pilot IDs, and an infeasible
    reservation. This function cannot resolve identity across sources.
    """
    ids = list(base_profile_ids)
    if any(not isinstance(value, str) or not value.strip() for value in ids):
        raise ValueError("Reviewed base profile IDs must be nonempty strings.")
    if len(set(ids)) != len(ids) or len(ids) < 5:
        raise ValueError("Provide at least five distinct reviewed people.")
    if isinstance(seed, bool) or not isinstance(seed, int):
        raise ValueError("The split seed must be an integer.")
    pilots = set(pilot_profile_ids)
    if not pilots.issubset(ids):
        raise ValueError("Calibration pilot IDs must belong to the selected corpus.")
    train_count, validation_count = 3 * len(ids) // 5, len(ids) // 5
    test_count = len(ids) - train_count - validation_count
    available = sorted(set(ids) - pilots)
    if len(available) < test_count:
        raise ValueError("Too many pilot people are reserved to retain the planned test fraction.")
    generator = random.Random(seed)
    generator.shuffle(available)
    test = available[:test_count]
    development = sorted(set(ids) - set(test))
    generator.shuffle(development)
    return {
        "train": development[:train_count],
        "validation": development[train_count:],
        "test": test,
    }
