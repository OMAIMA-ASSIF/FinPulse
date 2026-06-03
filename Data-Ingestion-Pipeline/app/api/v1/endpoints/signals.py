from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.v1.schemas import QualityResponse, SignalHistoryPoint, SignalRow
from app.api.v1.endpoints.score import _build_signal_row, _get_company_or_404
from app.db.models.filing import Filing
from app.db.models.signal_score import SignalScore
from app.db.session import get_db_dependency

router = APIRouter()


@router.get(
    "/{ticker}",
    response_model=list[SignalRow],
    status_code=status.HTTP_200_OK,
)
def get_signals(
    ticker: str,
    signal_name: str | None = Query(default=None),
    form_type: str | None = Query(default=None),
    limit: int = Query(default=50, ge=1, le=500),
    db: Session = Depends(get_db_dependency),
) -> list[SignalRow]:
    company = _get_company_or_404(db, ticker)

    query = (
        select(SignalScore, Filing)
        .join(Filing, Filing.id == SignalScore.filing_id)
        .where(SignalScore.company_id == company.id)
    )

    if signal_name:
        query = query.where(SignalScore.signal_name == signal_name)
    if form_type:
        query = query.where(Filing.form_type == form_type.upper())

    rows = db.execute(
        query.order_by(Filing.filed_at.desc(), SignalScore.computed_at.desc(), SignalScore.id.desc()).limit(limit)
    ).all()

    return [_build_signal_row(signal_row, filing) for signal_row, filing in rows]


@router.get(
    "/{ticker}/history",
    response_model=list[SignalHistoryPoint],
    status_code=status.HTTP_200_OK,
)
def get_signal_history(
    ticker: str,
    limit: int = Query(default=200, ge=1, le=1000),
    db: Session = Depends(get_db_dependency),
) -> list[SignalHistoryPoint]:
    company = _get_company_or_404(db, ticker)

    rows = db.execute(
        select(SignalScore, Filing)
        .join(Filing, Filing.id == SignalScore.filing_id)
        .where(
            SignalScore.company_id == company.id,
            SignalScore.signal_name == "composite_filing_risk",
        )
        .order_by(Filing.filed_at.asc(), Filing.id.asc(), SignalScore.computed_at.asc())
        .limit(limit)
    ).all()

    return [
        SignalHistoryPoint(
            filing_id=filing.id,
            accession_number=filing.accession_number,
            form_type=filing.form_type,
            filed_at=filing.filed_at,
            period_of_report=filing.period_of_report,
            signal_value=float(signal_row.signal_value) if signal_row.signal_value is not None else None,
            computed_at=signal_row.computed_at,
        )
        for signal_row, filing in rows
    ]


@router.get(
    "/{ticker}/quality",
    response_model=QualityResponse,
    status_code=status.HTTP_200_OK,
)
def get_signal_quality(
    ticker: str,
    filing_id: int = Query(..., description="ID du filing"),
    db: Session = Depends(get_db_dependency),
) -> QualityResponse:
    company = _get_company_or_404(db, ticker)

    filing = db.scalar(
        select(Filing).where(
            Filing.id == filing_id,
            Filing.company_id == company.id,
        )
    )
    if filing is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Filing {filing_id} not found for ticker {ticker.upper()}",
        )

    nci_row = db.scalar(
        select(SignalScore).where(
            SignalScore.filing_id == filing_id,
            SignalScore.signal_name == "nci_global",
        )
    )
    if nci_row is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"No nci_global score for filing {filing_id}",
        )

    detail = nci_row.detail if isinstance(nci_row.detail, dict) else {}
    triplet_detail = detail.get("triplet_boost_detail")
    nci_delta = None
    if isinstance(triplet_detail, dict):
        pre = triplet_detail.get("pre_boost_nci")
        post = triplet_detail.get("post_boost_nci")
        if pre is not None and post is not None:
            nci_delta = abs(float(post) - float(pre))

    return QualityResponse(
        ticker=company.ticker,
        filing_id=filing_id,
        nci_value=float(nci_row.signal_value) if nci_row.signal_value is not None else None,
        quality_grade=detail.get("quality_grade"),
        score_publishable=bool(detail.get("score_publishable", False)),
        freshness_days=detail.get("freshness_days"),
        coverage_ratio=detail.get("coverage_ratio"),
        nci_delta=nci_delta if nci_delta is not None else detail.get("nci_delta"),
        confidence_avg=detail.get("confidence_avg"),
        warnings=list(detail.get("quality_warnings") or []),
        blocking_issues=list(detail.get("quality_blocking") or []),
    )


@router.get(
    "/{ticker}/llm-explanation",
    response_model=dict,
    status_code=status.HTTP_200_OK,
)
def get_llm_explanation(
    ticker: str,
    db: Session = Depends(get_db_dependency),
) -> dict:
    company = _get_company_or_404(db, ticker)
    
    nci_row = db.scalar(
        select(SignalScore).where(
            SignalScore.company_id == company.id,
            SignalScore.signal_name == "nci_global",
        )
        .order_by(SignalScore.computed_at.desc())
        .limit(1)
    )
    
    if nci_row is None or nci_row.detail is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"No nci_global signal with explanation for ticker {ticker.upper()}"
        )
    
    detail = nci_row.detail if isinstance(nci_row.detail, dict) else {}
    return {
        "llm_explanation": detail.get("llm_explanation"),
        "llm_explanation_meta": detail.get("llm_explanation_meta")
    }
