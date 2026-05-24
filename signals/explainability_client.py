"""
Explicabilité — Module d'explication des scores NCI via Spring AI / Mistral

Flux complet en 7 étapes :
    1. Collecter les signaux du filing
    2. Construire le SectorProfile
    3. Identifier les déviations par rapport aux normes sectorielles
    4. Générer le prompt structuré
    5a. Appeler le LLM via Spring AI REST (priorité)
    5b. Appeler Mistral API directement (si Spring AI indisponible)
    6. Parser la réponse en objet Explanation
    7. Fallback : template déterministe si tous les LLM échouent

Architecture :
    Option A : Python → Spring AI Backend → LLM → réponse JSON
    Option B : Python → Mistral API directe → réponse JSON
"""

from __future__ import annotations

import json
import logging
import os
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from typing import Any

import requests
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.db.models.company import Company
from app.db.models.embedding import Embedding
from app.db.models.filing import Filing
from app.db.models.nci_score import NciScore
from app.db.models.signal_score import SignalScore

logger = logging.getLogger(__name__)

# ============================================================================
# Configuration
# ============================================================================

SPRING_AI_BASE_URL = os.getenv("SPRING_AI_BASE_URL", "http://localhost:8080")
SPRING_AI_EXPLAIN_ENDPOINT = os.getenv(
    "SPRING_AI_EXPLAIN_ENDPOINT", "/api/v1/explain"
)
SPRING_AI_TIMEOUT_SECONDS = int(os.getenv("SPRING_AI_TIMEOUT_SECONDS", "30"))

# Mistral API (fallback direct si Spring AI indisponible)
MISTRAL_API_KEY = os.getenv("MISTRAL_API_KEY", "")
MISTRAL_API_BASE = os.getenv("MISTRAL_API_BASE", "https://api.mistral.ai")
MISTRAL_CHAT_MODEL = os.getenv("MISTRAL_CHAT_MODEL", "mistral-small-latest")

RISK_LEVELS = {
    (0.0, 0.25): "low",
    (0.25, 0.50): "medium",
    (0.50, 0.75): "high",
    (0.75, 1.01): "critical",
}


# ============================================================================
# Dataclasses
# ============================================================================

@dataclass
class SectorProfile:
    """
    Profil agrégé d'un secteur industriel.

    Utilisé comme contexte dans le prompt LLM pour que l'explication
    puisse comparer le filing au comportement moyen du secteur.
    """
    sector_code: str
    sector_name: str
    company_count: int
    avg_nci: float
    std_nci: float
    avg_rlds: float | None
    avg_fundamental: float | None
    avg_insider: float | None
    avg_market: float | None
    avg_sentiment: float | None
    typical_convergence_tier: str

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass
class Explanation:
    """
    Objet d'explication structuré retourné par le moteur d'explicabilité.

    Contient le résumé en langage naturel, les facteurs clés,
    la comparaison sectorielle, et les actions recommandées.
    """
    filing_id: int
    company_id: int
    company_name: str
    ticker: str
    nci_score: float
    risk_level: str                         # low / medium / high / critical
    summary: str                            # 1-2 phrases en langage naturel
    key_drivers: list[dict[str, Any]]       # Facteurs ordonnés par contribution
    sector_comparison: dict[str, Any]       # Positionnement vs secteur
    recommended_actions: list[str]          # Actions suggérées
    confidence: float                       # Confiance dans l'explication
    model_used: str                         # "spring_ai" ou "fallback_template"
    generated_at: datetime = field(
        default_factory=lambda: datetime.now(timezone.utc)
    )

    def to_dict(self) -> dict[str, Any]:
        d = asdict(self)
        d["generated_at"] = self.generated_at.isoformat()
        return d


# ============================================================================
# ExplicabilityEngine — Orchestrateur des 7 étapes
# ============================================================================

class ExplicabilityEngine:
    """
    Moteur d'explicabilité pour les scores NCI.

    Orchestre le flux complet : collecte → profil sectoriel → prompt →
    appel Spring AI → parsing → fallback.

    Exemple d'utilisation :
        engine = ExplicabilityEngine(db)
        explanation = engine.explain(filing_id=123)
        print(explanation.summary)
    """

    def __init__(self, db: Session):
        self.db = db

    def explain(self, filing_id: int) -> Explanation:
        """Point d'entrée principal — exécute les 7 étapes."""

        # Étape 1 — Collecter les signaux
        filing_data = self._step1_collect_signals(filing_id)

        # Étape 2 — Construire le SectorProfile
        sector_profile = self._step2_build_sector_profile(
            filing_data["sector_code"]
        )

        # Étape 3 — Identifier les déviations
        deviations = self._step3_identify_deviations(
            filing_data, sector_profile
        )

        # Étape 4 — Générer le prompt
        prompt = self._step4_generate_prompt(
            filing_data, sector_profile, deviations
        )

        # Étape 5a — Appeler Spring AI (priorité)
        llm_response = self._step5_call_spring_ai(prompt)

        # Étape 5b — Appeler Mistral directement si Spring AI échoue
        if llm_response is None and MISTRAL_API_KEY:
            logger.info("Spring AI indisponible, appel Mistral direct...")
            llm_response = self._step5b_call_mistral(prompt)

        # Étape 6 — Parser la réponse
        if llm_response is not None:
            explanation = self._step6_parse_response(
                llm_response, filing_data, sector_profile
            )
            if explanation is not None:
                return explanation

        # Étape 7 — Fallback template déterministe
        logger.info("LLM indisponible, utilisation du fallback template")
        return self._step7_fallback(filing_data, sector_profile, deviations)

    # ────────────────────────────────────────────────────────────────────
    # ÉTAPE 1 : Collecter les signaux
    # ────────────────────────────────────────────────────────────────────
    def _step1_collect_signals(self, filing_id: int) -> dict[str, Any]:
        filing = self.db.get(Filing, filing_id)
        if filing is None:
            raise RuntimeError(f"Filing {filing_id} not found")

        company = self.db.get(Company, filing.company_id)

        # Signaux individuels
        signal_rows = self.db.scalars(
            select(SignalScore).where(
                SignalScore.filing_id == filing_id
            )
        ).all()
        signals = {
            row.signal_name: {
                "value": row.signal_value,
                "detail": row.detail,
                "computed_at": row.computed_at,
            }
            for row in signal_rows
            if row.signal_name
        }

        # NCI le plus récent
        nci = self.db.scalar(
            select(NciScore).where(
                NciScore.filing_id == filing_id
            ).order_by(NciScore.computed_at.desc()).limit(1)
        )

        # Top paragraphes anomaux
        top_anomalous = self.db.scalars(
            select(Embedding).where(
                Embedding.filing_id == filing_id,
                Embedding.anomaly_score.is_not(None),
            ).order_by(Embedding.anomaly_score.desc()).limit(5)
        ).all()

        return {
            "filing_id": filing_id,
            "company_id": company.id,
            "company_name": company.name,
            "ticker": company.ticker,
            "sector_code": company.sic_code or "unknown",
            "sector_name": company.sic_description or company.sector or "",
            "form_type": filing.form_type,
            "filed_at": str(filing.filed_at),
            "nci_global": nci.nci_global if nci else None,
            "convergence_tier": nci.convergence_tier if nci else None,
            "confidence": nci.confidence if nci else None,
            "signals": signals,
            "top_anomalous_paragraphs": [
                {
                    "text": e.text[:300] if e.text else "",
                    "anomaly_score": e.anomaly_score,
                    "reconstruction_error": e.reconstruction_error,
                }
                for e in top_anomalous
            ],
        }

    # ────────────────────────────────────────────────────────────────────
    # ÉTAPE 2 : Construire le SectorProfile
    # ────────────────────────────────────────────────────────────────────
    def _step2_build_sector_profile(self, sector_code: str) -> SectorProfile:
        companies = self.db.scalars(
            select(Company).where(Company.sic_code == sector_code)
        ).all()
        company_ids = [c.id for c in companies]

        if not company_ids:
            return SectorProfile(
                sector_code=sector_code, sector_name="Unknown",
                company_count=0, avg_nci=0.0, std_nci=0.0,
                avg_rlds=None, avg_fundamental=None, avg_insider=None,
                avg_market=None, avg_sentiment=None,
                typical_convergence_tier="none",
            )

        sector_name = companies[0].sic_description or companies[0].sector or ""

        # Agrégation NCI
        nci_stats = self.db.execute(
            select(
                func.avg(NciScore.nci_global),
                func.stddev(NciScore.nci_global),
                func.count(NciScore.id),
            ).where(NciScore.company_id.in_(company_ids))
        ).one()

        avg_nci = float(nci_stats[0] or 0.0)
        std_nci = float(nci_stats[1] or 0.0)

        # Agrégation signaux individuels
        def _avg_signal(name: str) -> float | None:
            result = self.db.scalar(
                select(func.avg(SignalScore.signal_value)).where(
                    SignalScore.company_id.in_(company_ids),
                    SignalScore.signal_name == name,
                    SignalScore.signal_value.is_not(None),
                )
            )
            return float(result) if result is not None else None

        # Convergence tier le plus fréquent
        tier_row = self.db.execute(
            select(
                NciScore.convergence_tier,
                func.count(NciScore.id).label("cnt"),
            ).where(
                NciScore.company_id.in_(company_ids),
                NciScore.convergence_tier.is_not(None),
            ).group_by(NciScore.convergence_tier).order_by(
                func.count(NciScore.id).desc()
            ).limit(1)
        ).first()

        return SectorProfile(
            sector_code=sector_code,
            sector_name=sector_name,
            company_count=len(company_ids),
            avg_nci=avg_nci,
            std_nci=std_nci,
            avg_rlds=_avg_signal("rlds"),
            avg_fundamental=_avg_signal("fundamental_deterioration"),
            avg_insider=_avg_signal("insider_signal"),
            avg_market=_avg_signal("market_signal"),
            avg_sentiment=_avg_signal("sentiment_signal"),
            typical_convergence_tier=str(tier_row[0]) if tier_row else "none",
        )

    # ────────────────────────────────────────────────────────────────────
    # ÉTAPE 3 : Identifier les déviations
    # ────────────────────────────────────────────────────────────────────
    def _step3_identify_deviations(
        self,
        filing_data: dict[str, Any],
        sector_profile: SectorProfile,
    ) -> list[dict[str, Any]]:
        deviations = []
        signals = filing_data.get("signals", {})

        signal_sector_map = {
            "rlds": sector_profile.avg_rlds,
            "fundamental_deterioration": sector_profile.avg_fundamental,
            "insider_signal": sector_profile.avg_insider,
            "market_signal": sector_profile.avg_market,
            "sentiment_signal": sector_profile.avg_sentiment,
        }

        for signal_name, sector_avg in signal_sector_map.items():
            sig = signals.get(signal_name)
            if sig is None or sig.get("value") is None or sector_avg is None:
                continue
            value = float(sig["value"])
            delta = value - sector_avg
            if abs(delta) > 0.10:  # Déviation significative > 10 points
                deviations.append({
                    "signal": signal_name,
                    "value": round(value, 4),
                    "sector_avg": round(sector_avg, 4),
                    "delta": round(delta, 4),
                    "direction": "above" if delta > 0 else "below",
                })

        deviations.sort(key=lambda d: abs(d["delta"]), reverse=True)
        return deviations

    # ────────────────────────────────────────────────────────────────────
    # ÉTAPE 4 : Générer le prompt Spring AI
    # ────────────────────────────────────────────────────────────────────
    def _step4_generate_prompt(
        self,
        filing_data: dict[str, Any],
        sector_profile: SectorProfile,
        deviations: list[dict[str, Any]],
    ) -> dict[str, Any]:
        """
        Construit le prompt structuré envoyé au backend Spring AI.

        Exemple concret de prompt Spring AI (Java) :

            @Service
            public class ExplicabilityEngine {

                private final ChatClient chatClient;

                public ExplicabilityEngine(ChatClient chatClient) {
                    this.chatClient = chatClient;
                }

                public Explanation explain(ExplainRequest request) {
                    String prompt = buildPrompt(request);
                    ChatResponse response = chatClient.call(
                        new Prompt(prompt,
                            OpenAiChatOptions.builder()
                                .withModel("gpt-4")
                                .withTemperature(0.3f)
                                .build()
                        )
                    );
                    return parseResponse(response.getResult().getOutput().getContent());
                }

                private String buildPrompt(ExplainRequest req) {
                    return String.format(
                        "Tu es un analyste financier expert. "
                        + "Analyse le score NCI de %.2f pour %s (secteur: %s).\\n"
                        + "Signaux élevés: %s\\n"
                        + "Moyenne sectorielle NCI: %.2f\\n"
                        + "Réponds en JSON avec: summary, key_drivers, recommended_actions.",
                        req.getNciScore(), req.getCompanyName(),
                        req.getSectorName(), req.getDeviations(),
                        req.getSectorAvgNci()
                    );
                }
            }
        """
        nci = filing_data.get("nci_global") or 0.0
        risk_level = _classify_risk(nci)

        # Construire les top drivers
        signal_labels = {
            "rlds": "Risk Lexical Drift Score (changement textuel)",
            "fundamental_deterioration": "Détérioration fondamentale",
            "insider_signal": "Signal insider (ITA)",
            "market_signal": "Signal marché",
            "sentiment_signal": "Sentiment médiatique",
            "forward_pessimism": "Pessimisme prospectif (GCE)",
            "balance_sheet_stress": "Stress bilan",
        }

        top_signals = []
        for sig_name, sig_data in filing_data.get("signals", {}).items():
            if sig_data.get("value") is not None and sig_data["value"] > 0.15:
                top_signals.append({
                    "name": sig_name,
                    "label": signal_labels.get(sig_name, sig_name),
                    "value": round(sig_data["value"], 4),
                })
        top_signals.sort(key=lambda s: s["value"], reverse=True)

        prompt_text = (
            f"Tu es un analyste financier expert spécialisé dans la détection "
            f"d'anomalies dans les filings SEC.\n\n"
            f"CONTEXTE:\n"
            f"- Entreprise: {filing_data['company_name']} ({filing_data['ticker']})\n"
            f"- Secteur: {sector_profile.sector_name} (SIC: {sector_profile.sector_code})\n"
            f"- Filing: {filing_data['form_type']} du {filing_data['filed_at']}\n"
            f"- Score NCI global: {nci:.4f} (niveau: {risk_level})\n"
            f"- NCI moyen du secteur: {sector_profile.avg_nci:.4f} "
            f"(écart-type: {sector_profile.std_nci:.4f})\n\n"
            f"SIGNAUX ÉLEVÉS (top contributeurs):\n"
        )
        for sig in top_signals[:5]:
            prompt_text += f"  - {sig['label']}: {sig['value']:.2f}\n"

        if deviations:
            prompt_text += "\nDÉVIATIONS PAR RAPPORT AU SECTEUR:\n"
            for dev in deviations[:3]:
                prompt_text += (
                    f"  - {dev['signal']}: {dev['value']:.2f} "
                    f"(secteur: {dev['sector_avg']:.2f}, "
                    f"delta: {dev['delta']:+.2f})\n"
                )

        anomalous = filing_data.get("top_anomalous_paragraphs", [])
        if anomalous:
            prompt_text += "\nPARAGRAPHES ANOMAUX (autoencoder):\n"
            for p in anomalous[:3]:
                prompt_text += (
                    f"  - Score anomalie: {p['anomaly_score']:.2f} — "
                    f"\"{p['text'][:150]}...\"\n"
                )

        prompt_text += (
            "\nINSTRUCTION:\n"
            "Réponds UNIQUEMENT en JSON valide avec cette structure:\n"
            "{\n"
            '  "summary": "1-2 phrases résumant le risque",\n'
            '  "key_drivers": [{"signal": "nom", "contribution": "explication"}],\n'
            '  "sector_comparison": "positionnement vs secteur",\n'
            '  "recommended_actions": ["action1", "action2"]\n'
            "}\n"
        )

        return {
            "prompt": prompt_text,
            "filing_id": filing_data["filing_id"],
            "company_name": filing_data["company_name"],
            "nci_score": nci,
            "risk_level": risk_level,
            "top_signals": top_signals[:5],
            "deviations": deviations[:3],
        }

    # ────────────────────────────────────────────────────────────────────
    # ÉTAPE 5 : Appeler Spring AI
    # ────────────────────────────────────────────────────────────────────
    def _step5_call_spring_ai(
        self, prompt_payload: dict[str, Any]
    ) -> dict[str, Any] | None:
        url = f"{SPRING_AI_BASE_URL}{SPRING_AI_EXPLAIN_ENDPOINT}"
        try:
            response = requests.post(
                url,
                json=prompt_payload,
                timeout=SPRING_AI_TIMEOUT_SECONDS,
                headers={"Content-Type": "application/json"},
            )
            response.raise_for_status()
            return response.json()
        except requests.exceptions.ConnectionError:
            logger.warning("Spring AI unreachable at %s — using fallback", url)
            return None
        except requests.exceptions.Timeout:
            logger.warning("Spring AI timeout after %ds", SPRING_AI_TIMEOUT_SECONDS)
            return None
        except Exception as exc:
            logger.error("Spring AI call failed: %s", exc)
            return None

    # ────────────────────────────────────────────────────────────────────
    # ÉTAPE 5b : Appeler Mistral directement (fallback)
    # ────────────────────────────────────────────────────────────────────
    def _step5b_call_mistral(
        self, prompt_payload: dict[str, Any]
    ) -> dict[str, Any] | None:
        """
        Appel direct à l'API Mistral AI quand Spring AI est indisponible.
        Utilise MISTRAL_API_KEY depuis le .env.
        Modèle : mistral-small-latest (configurable via MISTRAL_CHAT_MODEL).
        """
        url = f"{MISTRAL_API_BASE}/v1/chat/completions"
        prompt_text = prompt_payload.get("prompt", "")

        try:
            response = requests.post(
                url,
                json={
                    "model": MISTRAL_CHAT_MODEL,
                    "messages": [
                        {
                            "role": "system",
                            "content": (
                                "Tu es un analyste financier expert. "
                                "Réponds UNIQUEMENT en JSON valide."
                            ),
                        },
                        {"role": "user", "content": prompt_text},
                    ],
                    "temperature": 0.3,
                    "max_tokens": 1024,
                    "response_format": {"type": "json_object"},
                },
                timeout=SPRING_AI_TIMEOUT_SECONDS,
                headers={
                    "Content-Type": "application/json",
                    "Authorization": f"Bearer {MISTRAL_API_KEY}",
                },
            )
            response.raise_for_status()
            data = response.json()

            # Extraire le contenu de la réponse Mistral
            content_str = (
                data.get("choices", [{}])[0]
                .get("message", {})
                .get("content", "{}")
            )
            return json.loads(content_str)

        except requests.exceptions.ConnectionError:
            logger.warning("Mistral API unreachable")
            return None
        except requests.exceptions.Timeout:
            logger.warning("Mistral API timeout")
            return None
        except (json.JSONDecodeError, IndexError, KeyError) as exc:
            logger.warning("Mistral response parse error: %s", exc)
            return None
        except Exception as exc:
            logger.error("Mistral API call failed: %s", exc)
            return None

    # ────────────────────────────────────────────────────────────────────
    # ÉTAPE 6 : Parser la réponse LLM
    # ────────────────────────────────────────────────────────────────────
    def _step6_parse_response(
        self,
        llm_response: dict[str, Any],
        filing_data: dict[str, Any],
        sector_profile: SectorProfile,
    ) -> Explanation | None:
        try:
            content = llm_response
            # Si la réponse contient un champ 'content' (Spring AI format)
            if isinstance(content, dict) and "content" in content:
                raw = content["content"]
                if isinstance(raw, str):
                    content = json.loads(raw)

            nci = filing_data.get("nci_global") or 0.0
            return Explanation(
                filing_id=filing_data["filing_id"],
                company_id=filing_data["company_id"],
                company_name=filing_data["company_name"],
                ticker=filing_data["ticker"],
                nci_score=nci,
                risk_level=_classify_risk(nci),
                summary=str(content.get("summary", "")),
                key_drivers=content.get("key_drivers", []),
                sector_comparison={
                    "text": content.get("sector_comparison", ""),
                    "sector_avg_nci": sector_profile.avg_nci,
                    "company_nci": nci,
                },
                recommended_actions=content.get("recommended_actions", []),
                confidence=0.85,
                model_used="spring_ai",
            )
        except (json.JSONDecodeError, KeyError, TypeError) as exc:
            logger.warning("Failed to parse Spring AI response: %s", exc)
            return None

    # ────────────────────────────────────────────────────────────────────
    # ÉTAPE 7 : Fallback — template déterministe
    # ────────────────────────────────────────────────────────────────────
    def _step7_fallback(
        self,
        filing_data: dict[str, Any],
        sector_profile: SectorProfile,
        deviations: list[dict[str, Any]],
    ) -> Explanation:
        """
        Règle de fallback : si le LLM est indisponible ou retourne
        une réponse invalide, générer une explication déterministe
        basée sur les top 3 signaux et les déviations sectorielles.
        """
        nci = filing_data.get("nci_global") or 0.0
        risk_level = _classify_risk(nci)

        # Top 3 signaux contributeurs
        signals = filing_data.get("signals", {})
        ranked = sorted(
            [
                (name, data.get("value", 0.0))
                for name, data in signals.items()
                if data.get("value") is not None and data["value"] > 0.1
            ],
            key=lambda x: x[1],
            reverse=True,
        )[:3]

        # Générer le résumé
        if not ranked:
            summary = (
                f"{filing_data['company_name']} présente un score NCI de "
                f"{nci:.2f} ({risk_level}). Données insuffisantes pour "
                f"identifier les facteurs de risque principaux."
            )
        else:
            driver_names = ", ".join(
                _SIGNAL_LABELS.get(name, name) for name, _ in ranked
            )
            summary = (
                f"{filing_data['company_name']} présente un score NCI de "
                f"{nci:.2f} ({risk_level}), principalement porté par : "
                f"{driver_names}."
            )

        # Key drivers
        key_drivers = [
            {
                "signal": name,
                "label": _SIGNAL_LABELS.get(name, name),
                "value": round(val, 4),
                "contribution": f"Score de {val:.2f} — au-dessus du seuil d'alerte",
            }
            for name, val in ranked
        ]

        # Comparaison sectorielle
        delta_nci = nci - sector_profile.avg_nci
        if delta_nci > 0.15:
            sector_text = (
                f"Significativement au-dessus de la moyenne sectorielle "
                f"({sector_profile.avg_nci:.2f}). Écart: +{delta_nci:.2f}."
            )
        elif delta_nci < -0.15:
            sector_text = (
                f"En dessous de la moyenne sectorielle "
                f"({sector_profile.avg_nci:.2f}). Écart: {delta_nci:.2f}."
            )
        else:
            sector_text = (
                f"Dans la norme sectorielle "
                f"(moyenne: {sector_profile.avg_nci:.2f})."
            )

        # Actions recommandées
        actions = _generate_recommended_actions(risk_level, ranked)

        return Explanation(
            filing_id=filing_data["filing_id"],
            company_id=filing_data["company_id"],
            company_name=filing_data["company_name"],
            ticker=filing_data["ticker"],
            nci_score=nci,
            risk_level=risk_level,
            summary=summary,
            key_drivers=key_drivers,
            sector_comparison={
                "text": sector_text,
                "sector_avg_nci": sector_profile.avg_nci,
                "company_nci": nci,
                "delta": round(delta_nci, 4),
            },
            recommended_actions=actions,
            confidence=0.60,
            model_used="fallback_template",
        )


# ============================================================================
# Helpers
# ============================================================================

_SIGNAL_LABELS = {
    "rlds": "Dérive lexicale de risque (RLDS)",
    "mda_drift": "Dérive MD&A",
    "forward_pessimism": "Pessimisme prospectif (GCE)",
    "fundamental_deterioration": "Détérioration fondamentale",
    "balance_sheet_stress": "Stress bilan",
    "revenue_growth_deceleration": "Décélération de croissance",
    "earnings_quality": "Qualité des résultats",
    "insider_signal": "Signal insider (ITA)",
    "market_signal": "Signal marché",
    "sentiment_signal": "Sentiment médiatique",
}


def _classify_risk(nci: float) -> str:
    for (low, high), level in RISK_LEVELS.items():
        if low <= nci < high:
            return level
    return "critical"


def _generate_recommended_actions(
    risk_level: str, top_signals: list[tuple[str, float]]
) -> list[str]:
    actions = []
    signal_names = {name for name, _ in top_signals}

    if risk_level in ("high", "critical"):
        actions.append(
            "Examiner en priorité les sections Risk Factors et MD&A du filing"
        )
    if "insider_signal" in signal_names:
        actions.append(
            "Vérifier les transactions insider récentes (Form 4) "
            "et exclure les transactions 10b5-1 pré-programmées"
        )
    if "fundamental_deterioration" in signal_names:
        actions.append(
            "Analyser les marges opérationnelles et la tendance des revenus"
        )
    if "rlds" in signal_names:
        actions.append(
            "Comparer le langage de risque avec le filing précédent "
            "pour identifier les nouveaux facteurs"
        )
    if "market_signal" in signal_names:
        actions.append(
            "Évaluer le momentum de prix et la volatilité récente"
        )
    if not actions:
        actions.append("Monitoring standard — pas d'action immédiate requise")

    return actions
