"""
Sentinel de qualité pour les scores NCI.

Valide qu'un score NCI est fiable avant publication via 4 dimensions :
1. Fraîcheur — âge du dernier filing
2. Couverture — ratio de signaux disponibles
3. Confiance — confiance moyenne des signaux
4. Cohérence — variation vs score précédent

Ce module implémente le contrôle qualité final avant la publication.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field, asdict
from datetime import datetime, timezone
from typing import Optional

logger = logging.getLogger(__name__)


@dataclass
class QualityReport:
    """Rapport de qualité pour un score NCI."""
    
    ticker: str
    score_valid: bool          # False = score ne doit pas être publié
    quality_grade: str         # "A", "B", "C", "D", "F"
    warnings: list[str] = field(default_factory=list)        # Avertissements non-bloquants
    blocking_issues: list[str] = field(default_factory=list) # Raisons de bloquer le score
    freshness_days: int = 0        # Âge du dernier filing en jours
    coverage_ratio: float = 0.0    # Ratio de couches de signaux disponibles
    nci_delta: Optional[float] = None # Variation vs score précédent
    confidence_avg: float = 0.0    # Confiance moyenne
    timestamp: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())
    
    def is_publishable(self) -> bool:
        """Retourne True si le score peut être publié."""
        return self.score_valid and len(self.blocking_issues) == 0
    
    def to_dict(self) -> dict:
        """Convertir en dictionnaire pour JSON."""
        return asdict(self)


# ============================================================================
# Seuils Sentinel
# ============================================================================

FRESHNESS_WARN_DAYS = 90
FRESHNESS_BLOCK_DAYS = 180

COVERAGE_WARN = 0.60
COVERAGE_BLOCK = 0.40

CONFIDENCE_WARN = 0.50
CONFIDENCE_BLOCK = 0.30

NCI_DELTA_WARN = 15.0  # 15 points (sur échelle 0-100)
NCI_DELTA_ALERT = 30.0  # 30 points

INSIDER_SELL_RATIO_WARN = 0.80


def run_sentinel_checks(
    ticker: str,
    nci_value: float,
    confidence: float,
    coverage: float,
    last_filing_date: datetime,
    previous_nci: Optional[float],
    signal_details: dict,
) -> QualityReport:
    """
    Lance toutes les vérifications de qualité sur un score NCI.
    Retourne un rapport avec grade et liste des problèmes.
    
    Args:
        ticker: Code boursier (ex: AAPL)
        nci_value: Score NCI normalisé [0, 100]
        confidence: Confiance globale [0, 1]
        coverage: Ratio de couverture des signaux [0, 1]
        last_filing_date: Date du dernier filing
        previous_nci: Score NCI précédent (optionnel)
        signal_details: Dictionnaire avec détails des signaux (ita, rlds, etc.)
    
    Returns:
        QualityReport avec grade et liste des problèmes
    """
    warnings = []
    blocking = []
    
    # ── VÉRIFICATION 1 : Fraîcheur des données ────────────────────────────
    now = datetime.now(timezone.utc) if not last_filing_date.tzinfo else datetime.now(timezone.utc)
    if not last_filing_date.tzinfo:
        last_filing_date = last_filing_date.replace(tzinfo=timezone.utc)
    
    days_since_filing = (now - last_filing_date).days
    
    if days_since_filing > FRESHNESS_BLOCK_DAYS:
        blocking.append(
            f"Données trop anciennes ({days_since_filing} jours, seuil: {FRESHNESS_BLOCK_DAYS}). "
            f"Score NCI non fiable."
        )
    elif days_since_filing > FRESHNESS_WARN_DAYS:
        warnings.append(
            f"Dernier 10-Q/10-K date de {days_since_filing} jours "
            f"(seuil recommandé: {FRESHNESS_WARN_DAYS}). Le score peut être légèrement périmé."
        )
    
    # ── VÉRIFICATION 2 : Couverture des signaux ─────────────────────────
    if coverage < COVERAGE_BLOCK:
        blocking.append(
            f"Couverture insuffisante: {coverage:.0%} des couches disponibles "
            f"(minimum requis: {COVERAGE_BLOCK:.0%})."
        )
    elif coverage < COVERAGE_WARN:
        warnings.append(
            f"Couverture faible: {coverage:.0%}. "
            f"Le score manque d'informations complètes (seuil optimal: {COVERAGE_WARN:.0%})."
        )
    
    # ── VÉRIFICATION 3 : Confiance ───────────────────────────────────────
    if confidence < CONFIDENCE_BLOCK:
        blocking.append(
            f"Confiance trop faible ({confidence:.0%}). "
            f"Le score ne peut pas être publié (minimum: {CONFIDENCE_BLOCK:.0%})."
        )
    elif confidence < CONFIDENCE_WARN:
        warnings.append(
            f"Confiance modérée ({confidence:.0%}, optimal: {CONFIDENCE_WARN:.0%})."
        )
    
    # ── VÉRIFICATION 4 : Delta NCI (stabilité) ───────────────────────────
    nci_delta = None
    if previous_nci is not None:
        nci_delta = abs(nci_value - previous_nci)
        
        if nci_delta > NCI_DELTA_ALERT:
            warnings.append(
                f"Variation extrême du NCI: {nci_delta:.1f} points "
                f"({previous_nci:.1f} → {nci_value:.1f}). "
                f"Vérifier les données sources."
            )
        elif nci_delta > NCI_DELTA_WARN:
            warnings.append(
                f"Variation importante du NCI: {nci_delta:.1f} points. "
                f"Alerte delta NCI déclenchée."
            )
    
    # ── VÉRIFICATION 5 : Transactions insiders (ITA non planifiées) ──────
    ita_signal = signal_details.get("ita", {})
    sell_ratio = ita_signal.get("sell_ratio", 0.0)
    unplanned_flag = ita_signal.get("has_unplanned_sales", False)
    
    if unplanned_flag and sell_ratio > INSIDER_SELL_RATIO_WARN:
        warnings.append(
            f"Ventes insiders non planifiées détectées "
            f"(sell_ratio={sell_ratio:.0%}). Signal ITA élevé."
        )
    
    # ── GRADE FINAL ───────────────────────────────────────────────────────
    score_valid = len(blocking) == 0
    
    if not score_valid:
        grade = "F"
    elif len(warnings) == 0 and confidence >= 0.70 and coverage >= 0.80:
        grade = "A"
    elif len(warnings) <= 1 and confidence >= 0.50:
        grade = "B"
    elif len(warnings) <= 2:
        grade = "C"
    else:
        grade = "D"
    
    return QualityReport(
        ticker=ticker,
        score_valid=score_valid,
        quality_grade=grade,
        warnings=warnings,
        blocking_issues=blocking,
        freshness_days=days_since_filing,
        coverage_ratio=coverage,
        nci_delta=nci_delta,
        confidence_avg=confidence,
    )


def compute_signal_coverage(
    signal_layers: dict[str, dict],
    required_layers: list[str],
) -> float:
    """
    Calcule le ratio de couverture des signaux.
    
    Args:
        signal_layers: Dictionnaire des signaux avec leurs scores
        required_layers: Couches requises (text, numeric, behavior, market, sentiment)
    
    Returns:
        Ratio [0, 1] de couches disponibles avec score > 0
    """
    if not required_layers:
        return 1.0
    
    available = sum(
        1 for layer in required_layers
        if layer in signal_layers and signal_layers[layer].get("signal_value") is not None
    )
    
    return available / len(required_layers)


def compute_average_confidence(
    signal_layers: dict[str, dict],
) -> float:
    """
    Calcule la confiance moyenne entre tous les signaux disponibles.
    
    Args:
        signal_layers: Dictionnaire des signaux avec leurs confidences
    
    Returns:
        Confiance moyenne [0, 1]
    """
    confidences = [
        signal.get("confidence", 0.5)
        for signal in signal_layers.values()
        if signal.get("signal_value") is not None
    ]
    
    if not confidences:
        return 0.0
    
    return sum(confidences) / len(confidences)
