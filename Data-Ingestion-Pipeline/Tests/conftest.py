from __future__ import annotations

import sys
from pathlib import Path
from datetime import date

import pytest
import numpy as np
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, Session

from app.db.base import Base
from app.db.models.company import Company
from app.db.models.filing import Filing


ROOT = Path(__file__).resolve().parents[1]
root_str = str(ROOT)

if root_str not in sys.path:
    sys.path.insert(0, root_str)


@pytest.fixture
def db_session() -> Session:
    """Create an in-memory SQLite database for testing."""
    engine = create_engine("sqlite:///:memory:", echo=False)
    Base.metadata.create_all(engine)
    SessionLocal = sessionmaker(bind=engine)
    session = SessionLocal()
    yield session
    session.close()


@pytest.fixture
def sample_company(db_session: Session) -> Company:
    """Sample company with SIC code for sector classification."""
    company = Company(
        cik="0001318605",
        ticker="TSLA",
        name="Tesla, Inc.",
        sic_code="3711",
        sector="Automotive"
    )
    db_session.add(company)
    db_session.flush()
    return company


@pytest.fixture
def sample_filing(db_session: Session, sample_company: Company) -> Filing:
    """Sample 10-K filing."""
    filing = Filing(
        company_id=sample_company.id,
        accession_number="0001318605-26-000001",
        form_type="10-K",
        filed_at=date(2026, 1, 31),
        period_of_report=date(2025, 12, 31),
        raw_s3_key="tsla/2026-10k",
        is_signal_scored=False,
    )
    db_session.add(filing)
    db_session.flush()
    return filing


@pytest.fixture
def sample_embeddings_array() -> np.ndarray:
    """Generate 100 synthetic embeddings (1024 dims matching Mistral)."""
    np.random.seed(42)
    return np.random.randn(100, 1024).astype(np.float32)
