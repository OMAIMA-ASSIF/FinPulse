"""Tests du signal de convergence triplet (RLDS + forward_pessimism + ITA)."""

from datetime import date

import pytest

from app.db.models.filing import Filing
from signals.composite_engine import _build_triplet_convergence_signal


@pytest.fixture
def sample_filing() -> Filing:
    filing = Filing()
    filing.id = 1
    filing.company_id = 1
    filing.filed_at = date(2026, 1, 15)
    return filing


def test_triplet_full_boost_when_three_signals_elevated(sample_filing: Filing) -> None:
    result = _build_triplet_convergence_signal(
        filing=sample_filing,
        model_version="test",
        signal_values={
            "rlds": 0.30,
            "forward_pessimism": 0.35,
            "insider_signal": 0.20,
            "_overall_confidence": 0.8,
        },
    )
    assert result.signal_value == pytest.approx(0.25)
    assert result.detail["triplet_confidence"] == "full"


def test_triplet_boost_blocked_when_low_confidence(sample_filing: Filing) -> None:
    result = _build_triplet_convergence_signal(
        filing=sample_filing,
        model_version="test",
        signal_values={
            "rlds": 0.30,
            "forward_pessimism": 0.35,
            "insider_signal": 0.20,
            "_overall_confidence": 0.30,
        },
    )
    assert result.signal_value == pytest.approx(0.0)
    assert result.detail["triplet_confidence"] == "blocked_low_confidence"
