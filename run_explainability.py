"""Petit script CLI pour tester `ExplicabilityEngine.explain()`.

Usage:
  python run_explainability.py --filing-id 123

Le script utilise la configuration DB du projet (voir `app.db.session`).
"""
from __future__ import annotations

import argparse
import json
import logging
import os
import sys


def _load_env_file(path: str = ".env") -> None:
    """Charge un fichier .env simple dans os.environ (ne remplace pas les vars existantes)."""
    if not os.path.exists(path):
        return
    try:
        with open(path, "r", encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line or line.startswith("#"):
                    continue
                if "=" not in line:
                    continue
                key, val = line.split("=", 1)
                key = key.strip()
                val = val.strip().strip('"').strip("'")
                if key and key not in os.environ:
                    os.environ[key] = val
    except Exception:
        pass

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def main(argv: list[str] | None = None) -> int:
    # Charger .env avant les imports qui lisent os.environ (Spring AI, Mistral).
    _load_env_file()

    from app.db.session import get_db
    from signals.explainability_client import ExplicabilityEngine

    parser = argparse.ArgumentParser(description="Run explainability for a filing")
    parser.add_argument("--filing-id", type=int, required=True, help="Filing ID to explain")
    args = parser.parse_args(argv)

    filing_id = args.filing_id

    try:
        with get_db() as db:
            engine = ExplicabilityEngine(db)
            explanation = engine.explain(filing_id=filing_id)
            print(json.dumps(explanation.to_dict(), ensure_ascii=False, indent=2))
        return 0
    except Exception as exc:
        logger.exception("Explainability run failed: %s", exc)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
