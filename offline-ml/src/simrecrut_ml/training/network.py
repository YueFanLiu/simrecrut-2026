"""Define the separate 673-parameter networks required by the specification."""

import torch
from torch import nn


class RecruitmentNetwork(nn.Module):
    """Map a float32 B-by-32 batch to B-by-1 raw logits.

    Each instance owns its learned parameters. The saved feature schema
    determines order; input construction belongs in features.py. Dropout
    0.1 is the source starting value; zero is its validation-search option.
    Sigmoid is applied for scores, never before BCEWithLogitsLoss.
    """

    def __init__(self, dropout: float = 0.1) -> None:
        super().__init__()
        if isinstance(dropout, bool) or dropout not in (0.0, 0.1):
            raise ValueError("Use a source-specified dropout candidate: zero or 0.1.")
        self.layers = nn.Sequential(
            nn.Linear(32, 16), nn.ReLU(), nn.Dropout(dropout),
            nn.Linear(16, 8), nn.ReLU(), nn.Linear(8, 1),
        )

    def forward(self, inputs: torch.Tensor) -> torch.Tensor:
        """Return raw logits; reject a wrong shape, type, or nonfinite input."""
        if inputs.ndim != 2 or inputs.shape[1] != 32 or inputs.dtype != torch.float32:
            raise ValueError("Network inputs must be float32 tensors shaped B by 32.")
        if not bool(torch.isfinite(inputs).all()):
            raise ValueError("Network inputs must be finite.")
        return self.layers(inputs)
