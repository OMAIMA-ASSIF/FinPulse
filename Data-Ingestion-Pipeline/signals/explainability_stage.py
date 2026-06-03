"""
Stage pipeline — explicabilité NCI (Spring AI / Mistral / fallback template).
"""

from __future__ import annotations

import logging
from typing import Any

from sqlalchemy.orm import Session

logger = logging.getLogger(__name__)


def run_explainability_stage(filing_id: int, db: Session) -> dict[str, Any]:
    """
    Génère une explication LLM et la persiste dans signal_scores.detail (nci_global).

    Ne lève pas d'exception : la pipeline principale continue en cas d'échec.
    """
    from app.core.config import get_settings
    from signals.explainability_client import ExplicabilityEngine

    settings = get_settings()
    if not settings.pipeline_explain_enabled:
        return {
            "status": "skipped",
            "reason": "pipeline_explain_enabled=false",
            "filing_id": filing_id,
        }

    try:
        engine = ExplicabilityEngine(db)
        result = engine.explain_and_persist(filing_id=filing_id)
        logger.info(
            "Explainability stored for filing %d (model=%s)",
            filing_id,
            result.get("model_used"),
        )
        return {"status": "scored", "filing_id": filing_id, **result}
    except Exception as exc:
        logger.warning("Explainability stage skipped for filing %d: %s", filing_id, exc)
        return {
            "status": "skipped",
            "reason": str(exc),
            "filing_id": filing_id,
        }
