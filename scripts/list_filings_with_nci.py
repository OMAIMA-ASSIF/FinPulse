"""Liste quelques filings disposant d'un NCI récent pour tests.

Usage: python scripts/list_filings_with_nci.py
"""
from __future__ import annotations

import json
from sqlalchemy import select
from app.db.session import get_db
from app.db.models.nci_score import NciScore
from app.db.models.filing import Filing
from app.db.models.company import Company


def main() -> int:
    rows = []
    with get_db() as db:
        stmt = (
            select(NciScore, Filing, Company)
            .join(Filing, NciScore.filing_id == Filing.id)
            .join(Company, Filing.company_id == Company.id)
            .where(NciScore.nci_global.is_not(None))
            .order_by(NciScore.computed_at.desc())
            .limit(20)
        )
        for nci, filing, company in db.execute(stmt):
            rows.append(
                {
                    "filing_id": filing.id,
                    "company_id": company.id,
                    "company_name": company.name,
                    "ticker": company.ticker,
                    "nci_global": float(nci.nci_global) if nci.nci_global is not None else None,
                    "computed_at": nci.computed_at.isoformat() if nci.computed_at is not None else None,
                    "form_type": filing.form_type,
                    "filed_at": str(filing.filed_at),
                }
            )

    print(json.dumps(rows, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
