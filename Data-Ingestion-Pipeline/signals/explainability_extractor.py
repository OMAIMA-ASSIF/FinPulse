"""
Extrait les paragraphes les plus anormaux d'un filing pour l'explication LLM.

Ce module fournit les outils pour :
1. Identifier les top-k paragraphes avec les scores d'anomalie les plus élevés
2. Construire le payload HTTP pour le service Spring AI
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Optional
from datetime import datetime

from sqlalchemy.orm import Session

from app.db.models.embedding import Embedding
from app.db.models.filing import Filing
from app.db.models.company import Company


@dataclass
class AnomalousPararaph:
    """Représente un paragraphe identifié comme anormal."""
    text: str
    section: str
    anomaly_score: float
    mse: float
    paragraph_index: int


def extract_anomalous_paragraphs(
    db: Session,
    filing_id: int,
    top_k: int = 5,
    min_anomaly_score: float = 0.7
) -> list[AnomalousPararaph]:
    """
    Retourne les top_k paragraphes avec les scores d'anomalie les plus élevés.
    Filtrés sur anomaly_score >= min_anomaly_score.
    
    Args:
        db: SQLAlchemy session
        filing_id: ID du filing
        top_k: Nombre maximum de paragraphes à retourner
        min_anomaly_score: Score d'anomalie minimum (filtre)
    
    Returns:
        Liste de AnomalousPararaph triée par score décroissant
        
    Raises:
        ValueError: Si filing_id ne correspond à aucun filing
    """
    filing = db.query(Filing).get(filing_id)
    if not filing:
        raise ValueError(f"Filing {filing_id} not found")
    
    rows = (
        db.query(Embedding)
        .filter(
            Embedding.filing_id == filing_id,
            Embedding.anomaly_score >= min_anomaly_score,
            Embedding.anomaly_score.isnot(None)
        )
        .order_by(Embedding.anomaly_score.desc())
        .limit(top_k)
        .all()
    )
    
    return [
        AnomalousPararaph(
            text=row.text or "",
            section=row.filing_section.section if row.filing_section else "unknown",
            anomaly_score=row.anomaly_score,
            mse=row.reconstruction_error or 0.0,
            paragraph_index=row.chunk_index or 0
        )
        for row in rows
    ]


def build_explanation_request(
    ticker: str,
    sector_name: str,
    filing_period: str,
    paragraphs: list[AnomalousPararaph]
) -> dict:
    """
    Construit le payload à envoyer au service Spring AI.
    
    Args:
        ticker: Code boursier (ex: AAPL)
        sector_name: Nom ou code du secteur
        filing_period: Période du filing (ex: 2024-Q3)
        paragraphs: Liste de paragraphes anormaux
    
    Returns:
        Dictionnaire prêt à être sérialisé en JSON
    """
    return {
        "ticker": ticker,
        "sector": sector_name,
        "filing_period": filing_period,
        "paragraphs": [
            {
                "text": p.text,
                "section": p.section,
                "anomaly_score": p.anomaly_score,
                "mse": p.mse,
                "paragraph_index": p.paragraph_index
            }
            for p in paragraphs
        ],
        "context": {
            "task": "anomaly_explanation",
            "language": "fr",  # réponse en français
            "timestamp": datetime.utcnow().isoformat(),
        }
    }
