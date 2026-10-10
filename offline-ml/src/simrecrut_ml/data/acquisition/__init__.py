"""Retrieve declared publisher sources and preserve local provenance."""

from .sources import SOURCE_IDS, acquire_all, acquire_dataset

__all__ = ["SOURCE_IDS", "acquire_all", "acquire_dataset"]
