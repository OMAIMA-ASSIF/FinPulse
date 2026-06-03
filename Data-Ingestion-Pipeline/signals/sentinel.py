"""
Sentinel — Monitoring de qualité et alertes sur les scores NCI

Ce module implémente :
    1. Score Quality : évaluation de la qualité d'un score NCI sur 4 dimensions
    2. Filtre 10b5-1 : exclusion des transactions insider pré-programmées
    3. Calcul ITA ajusté : ITA filtré des transactions 10b5-1
    4. Alerte delta NCI > 15 points : détection de variations brutales

Architecture :
    - SentinelMonitor : orchestrateur principal
    - ScoreQuality : dataclass du score qualité (4 dimensions)
    - SentinelAlert : dataclass d'alerte
"""

from __future__ import annotations

import logging
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from typing import Any

from sqlalchemy import select, func
from sqlalchemy.orm import Session

from app.db.models.company import Company
from app.db.models.filing import Filing
from app.db.models.insider_transaction import InsiderTransaction
from app.db.models.nci_score import NciScore
from app.db.models.signal_score import SignalScore
from signals.common import clip01

logger = logging.getLogger(__name__)


# ============================================================================
# Constantes
# ============================================================================

# Poids des 4 dimensions du Score Quality
QUALITY_WEIGHTS = {
    "coverage": 0.30,      # Ratio signaux disponibles / attendus
    "freshness": 0.25,     # Pénalité si données carry-forward (stale)
    "confidence": 0.25,    # Moyenne des confidences individuelles
    "consistency": 0.20,   # Variance inter-signaux (faible = consistant)
}

# Seuil d'alerte pour variation de NCI
NCI_DELTA_ALERT_THRESHOLD = 0.15   # 15 points sur l'échelle [0, 1]

# Signaux attendus pour le calcul de couverture
EXPECTED_SIGNAL_NAMES = (
    "rlds", "mda_drift", "forward_pessimism",
    "fundamental_deterioration", "balance_sheet_stress",
    "revenue_growth_deceleration", "earnings_quality",
    "insider_signal", "market_signal", "sentiment_signal",
)

# Codes de transaction 10b5-1 (pré-programmées)
RULE_10B5_1_INDICATORS = {"10b5-1", "10b5", "rule10b5", "plan"}


# ============================================================================
# Dataclasses
# ============================================================================

@dataclass
class ScoreQuality:
    """
    Score de qualité d'un NCI calculé sur 4 dimensions.

    Dimensions et poids :
        coverage   (0.30) : ratio signaux disponibles / signaux attendus
        freshness  (0.25) : 1.0 si données fraîches, pénalisé si stale
        confidence (0.25) : moyenne des confidences des signaux individuels
        consistency(0.20) : 1 - variance normalisée des signaux (faible = bon)

    quality_score = Σ(dimension_i × poids_i)
    """
    filing_id: int
    company_id: int
    coverage: float         # [0, 1]
    freshness: float        # [0, 1]
    confidence: float       # [0, 1]
    consistency: float      # [0, 1]
    quality_score: float    # [0, 1] — score composite pondéré
    detail: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass
class SentinelAlert:
    """
    Alerte générée par le Sentinel lorsqu'un seuil est dépassé.

    Types d'alerte :
        - delta_nci : variation NCI > 15 points entre deux filings
        - low_quality : score qualité < 0.4
        - ita_10b5_1_bias : ITA potentiellement biaisé par transactions 10b5-1
    """
    company_id: int
    filing_id: int
    alert_type: str           # delta_nci / low_quality / ita_10b5_1_bias
    severity: str             # info / warning / critical
    message: str
    detail: dict[str, Any] = field(default_factory=dict)
    triggered_at: datetime = field(
        default_factory=lambda: datetime.now(timezone.utc)
    )

    def to_dict(self) -> dict[str, Any]:
        d = asdict(self)
        d["triggered_at"] = self.triggered_at.isoformat()
        return d


# ============================================================================
# SentinelMonitor — Orchestrateur
# ============================================================================

class SentinelMonitor:
    """
    Moniteur de qualité et d'alertes pour les scores NCI.

    Usage :
        monitor = SentinelMonitor(db)
        quality = monitor.compute_score_quality(filing_id=123)
        alerts = monitor.check_alerts(filing_id=123)
    """

    def __init__(self, db: Session):
        self.db = db

    # ────────────────────────────────────────────────────────────────
    # Score Quality — 4 dimensions
    # ────────────────────────────────────────────────────────────────
    def compute_score_quality(self, filing_id: int) -> ScoreQuality:
        """Calcule le score de qualité d'un NCI sur 4 dimensions."""

        filing = self.db.get(Filing, filing_id)
        if filing is None:
            raise RuntimeError(f"Filing {filing_id} not found")

        signal_rows = self.db.scalars(
            select(SignalScore).where(
                SignalScore.filing_id == filing_id,
                SignalScore.signal_name.in_(EXPECTED_SIGNAL_NAMES),
            )
        ).all()

        # Dimension 1 : Coverage
        available = [r for r in signal_rows if r.signal_value is not None]
        coverage = clip01(len(available) / max(len(EXPECTED_SIGNAL_NAMES), 1))

        # Dimension 2 : Freshness
        nci = self.db.scalar(
            select(NciScore).where(
                NciScore.filing_id == filing_id
            ).order_by(NciScore.computed_at.desc()).limit(1)
        )
        if nci is not None and not nci.data_fresh:
            freshness = 0.5  # Pénalité pour données stale
        else:
            freshness = 1.0

        # Dimension 3 : Confidence
        confidences = []
        for row in available:
            if isinstance(row.detail, dict):
                conf = row.detail.get("confidence")
                if isinstance(conf, (int, float)):
                    confidences.append(float(conf))
        confidence = clip01(
            sum(confidences) / len(confidences)
        ) if confidences else 0.5

        # Dimension 4 : Consistency
        values = [float(r.signal_value) for r in available]
        if len(values) >= 2:
            import numpy as np
            variance = float(np.var(values))
            # Normaliser : variance max théorique = 0.25 (signaux [0,1])
            consistency = clip01(1.0 - (variance / 0.25))
        else:
            consistency = 0.5

        # Score composite pondéré
        quality_score = clip01(
            QUALITY_WEIGHTS["coverage"] * coverage
            + QUALITY_WEIGHTS["freshness"] * freshness
            + QUALITY_WEIGHTS["confidence"] * confidence
            + QUALITY_WEIGHTS["consistency"] * consistency
        )

        return ScoreQuality(
            filing_id=filing_id,
            company_id=filing.company_id,
            coverage=round(coverage, 4),
            freshness=round(freshness, 4),
            confidence=round(confidence, 4),
            consistency=round(consistency, 4),
            quality_score=round(quality_score, 4),
            detail={
                "available_signals": len(available),
                "expected_signals": len(EXPECTED_SIGNAL_NAMES),
                "signal_names_available": [r.signal_name for r in available],
                "data_fresh": nci.data_fresh if nci else True,
                "weights": dict(QUALITY_WEIGHTS),
            },
        )

    # ────────────────────────────────────────────────────────────────
    # Filtre 10b5-1
    # ────────────────────────────────────────────────────────────────
    def filter_10b5_1_transactions(
        self, company_id: int
    ) -> dict[str, Any]:
        """
        Identifie les transactions insider pré-programmées (Rule 10b5-1).

        Détection via le champ raw_detail/footnotes des InsiderTransactions.
        Ces transactions doivent être exclues du calcul ITA pour éviter
        les faux positifs.

        Returns:
            {
                "total_transactions": int,
                "rule_10b5_1_count": int,
                "organic_count": int,
                "filtered_transaction_ids": list[int],
                "organic_transaction_ids": list[int],
            }
        """
        transactions = self.db.scalars(
            select(InsiderTransaction).where(
                InsiderTransaction.company_id == company_id,
                InsiderTransaction.transaction_type_normalized == "sell",
            )
        ).all()

        filtered_ids = []
        organic_ids = []

        for tx in transactions:
            is_10b5_1 = False
            # Vérifier dans raw_detail
            if isinstance(tx.raw_detail, dict):
                footnotes = str(tx.raw_detail.get("footnotes", "")).lower()
                for indicator in RULE_10B5_1_INDICATORS:
                    if indicator in footnotes:
                        is_10b5_1 = True
                        break
                # Vérifier aussi dans d'autres champs
                if not is_10b5_1:
                    notes = str(tx.raw_detail.get("notes", "")).lower()
                    for indicator in RULE_10B5_1_INDICATORS:
                        if indicator in notes:
                            is_10b5_1 = True
                            break

            if is_10b5_1:
                filtered_ids.append(tx.id)
            else:
                organic_ids.append(tx.id)

        return {
            "total_transactions": len(transactions),
            "rule_10b5_1_count": len(filtered_ids),
            "organic_count": len(organic_ids),
            "filtered_transaction_ids": filtered_ids,
            "organic_transaction_ids": organic_ids,
        }

    # ────────────────────────────────────────────────────────────────
    # Calcul ITA ajusté (sans 10b5-1)
    # ────────────────────────────────────────────────────────────────
    def compute_adjusted_ita(self, company_id: int) -> dict[str, Any]:
        """
        Calcule l'ITA en excluant les transactions 10b5-1.

        ITA = weighted_sell / (weighted_sell + weighted_buy)
        Pondéré par le rôle du dirigeant (CEO=1.0, CFO=0.9, etc.)
        """
        filter_result = self.filter_10b5_1_transactions(company_id)
        organic_ids = filter_result["organic_transaction_ids"]

        if not organic_ids:
            return {
                "ita_raw": 0.0,
                "ita_adjusted": 0.0,
                "organic_sell_count": 0,
                "filtered_10b5_1": filter_result["rule_10b5_1_count"],
            }

        organic_sells = self.db.scalars(
            select(InsiderTransaction).where(
                InsiderTransaction.id.in_(organic_ids)
            )
        ).all()

        total_sell_value = sum(
            float(tx.transaction_value or 0) for tx in organic_sells
        )

        # Achats organiques
        buys = self.db.scalars(
            select(InsiderTransaction).where(
                InsiderTransaction.company_id == company_id,
                InsiderTransaction.transaction_type_normalized == "buy",
            )
        ).all()
        total_buy_value = sum(float(tx.transaction_value or 0) for tx in buys)

        total = total_sell_value + total_buy_value
        ita_adjusted = (total_sell_value / total) if total > 0 else 0.0

        return {
            "ita_adjusted": round(clip01(ita_adjusted), 4),
            "organic_sell_value": total_sell_value,
            "organic_buy_value": total_buy_value,
            "organic_sell_count": len(organic_sells),
            "filtered_10b5_1": filter_result["rule_10b5_1_count"],
        }

    # ────────────────────────────────────────────────────────────────
    # Alerte delta NCI > 15 points
    # ────────────────────────────────────────────────────────────────
    def check_alerts(self, filing_id: int) -> list[SentinelAlert]:
        """
        Vérifie toutes les conditions d'alerte pour un filing :
            1. Delta NCI > 15 points vs précédent
            2. Score qualité faible
            3. ITA biaisé par transactions 10b5-1
        """
        alerts: list[SentinelAlert] = []

        filing = self.db.get(Filing, filing_id)
        if filing is None:
            return alerts

        # ── Alerte 1 : delta NCI > 15 points ──
        delta_alert = self._check_delta_nci(filing)
        if delta_alert:
            alerts.append(delta_alert)

        # ── Alerte 2 : score qualité faible ──
        quality = self.compute_score_quality(filing_id)
        if quality.quality_score < 0.4:
            alerts.append(SentinelAlert(
                company_id=filing.company_id,
                filing_id=filing_id,
                alert_type="low_quality",
                severity="warning",
                message=(
                    f"Score qualité faible ({quality.quality_score:.2f}) "
                    f"pour le filing {filing_id}"
                ),
                detail=quality.to_dict(),
            ))

        # ── Alerte 3 : biais ITA 10b5-1 ──
        ita_result = self.compute_adjusted_ita(filing.company_id)
        if ita_result["filtered_10b5_1"] > 0:
            alerts.append(SentinelAlert(
                company_id=filing.company_id,
                filing_id=filing_id,
                alert_type="ita_10b5_1_bias",
                severity="info",
                message=(
                    f"{ita_result['filtered_10b5_1']} transactions 10b5-1 "
                    f"détectées et exclues du calcul ITA"
                ),
                detail=ita_result,
            ))

        return alerts

    def _check_delta_nci(self, filing: Filing) -> SentinelAlert | None:
        """
        Compare le NCI actuel au NCI précédent de la même entreprise.
        Si |delta| > 15 points → alerte.
        """
        # NCI actuel
        current_nci = self.db.scalar(
            select(NciScore).where(
                NciScore.filing_id == filing.id
            ).order_by(NciScore.computed_at.desc()).limit(1)
        )
        if current_nci is None:
            return None

        # NCI précédent (même entreprise, filing différent)
        previous_nci = self.db.scalar(
            select(NciScore).where(
                NciScore.company_id == filing.company_id,
                NciScore.filing_id != filing.id,
                NciScore.filing_id.is_not(None),
            ).order_by(NciScore.computed_at.desc()).limit(1)
        )
        if previous_nci is None:
            return None

        delta = current_nci.nci_global - previous_nci.nci_global

        if abs(delta) > NCI_DELTA_ALERT_THRESHOLD:
            direction = "hausse" if delta > 0 else "baisse"
            severity = "critical" if abs(delta) > 0.25 else "warning"

            return SentinelAlert(
                company_id=filing.company_id,
                filing_id=filing.id,
                alert_type="delta_nci",
                severity=severity,
                message=(
                    f"Variation NCI de {delta:+.2f} points ({direction}) "
                    f"pour filing {filing.id} "
                    f"(précédent: {previous_nci.nci_global:.2f} → "
                    f"actuel: {current_nci.nci_global:.2f})"
                ),
                detail={
                    "delta_nci": round(delta, 4),
                    "previous_nci": round(previous_nci.nci_global, 4),
                    "current_nci": round(current_nci.nci_global, 4),
                    "previous_filing_id": previous_nci.filing_id,
                    "current_filing_id": filing.id,
                    "threshold": NCI_DELTA_ALERT_THRESHOLD,
                    "direction": direction,
                },
            )

        return None
