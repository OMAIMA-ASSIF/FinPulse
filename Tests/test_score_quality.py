"""Tests du sentinel de qualité des scores NCI."""

from datetime import datetime, timedelta, timezone

import pytest

from signals.score_quality import run_sentinel_checks


def test_sentinel_blocks_stale_filing() -> None:
    old_date = datetime.now(timezone.utc) - timedelta(days=200)
    report = run_sentinel_checks(
        ticker="TEST",
        nci_value=70.0,
        confidence=0.8,
        coverage=0.9,
        last_filing_date=old_date,
        previous_nci=65.0,
        signal_details={"ita": {"sell_ratio": 0.2, "has_unplanned_sales": False}},
    )
    assert report.quality_grade == "F"
    assert not report.is_publishable()
    assert any("trop anciennes" in issue for issue in report.blocking_issues)


def test_sentinel_warns_on_nci_delta() -> None:
    recent = datetime.now(timezone.utc) - timedelta(days=30)
    report = run_sentinel_checks(
        ticker="TEST",
        nci_value=80.0,
        confidence=0.75,
        coverage=0.85,
        last_filing_date=recent,
        previous_nci=60.0,
        signal_details={"ita": {"sell_ratio": 0.2, "has_unplanned_sales": False}},
    )
    assert report.nci_delta == pytest.approx(20.0)
    assert any("Variation" in w for w in report.warnings)


def test_sentinel_blocks_low_coverage() -> None:
    recent = datetime.now(timezone.utc) - timedelta(days=10)
    report = run_sentinel_checks(
        ticker="TEST",
        nci_value=50.0,
        confidence=0.8,
        coverage=0.30,
        last_filing_date=recent,
        previous_nci=None,
        signal_details={"ita": {"sell_ratio": 0.0, "has_unplanned_sales": False}},
    )
    assert not report.is_publishable()
    assert any("Couverture insuffisante" in issue for issue in report.blocking_issues)
