from app.db.session import SessionLocal
from sqlalchemy import text
import json

def main():
    db = SessionLocal()
    query = """
    SELECT filing_id, detail->>'llm_explanation' as summary, detail->>'llm_explanation_meta' as meta
    FROM signal_scores
    WHERE signal_name='nci_global' AND detail->>'llm_explanation' IS NOT NULL
    ORDER BY computed_at DESC
    LIMIT 1
    """
    res = db.execute(text(query)).fetchone()
    
    if res:
        print("====== DERNIÈRE EXPLICATION SAUVEGARDÉE EN BASE ======")
        print(f"Filing ID : {res[0]}")
        print(f"\n--- Résumé (llm_explanation) ---\n{res[1]}")
        print(f"\n--- Métadonnées (llm_explanation_meta) ---\n{json.dumps(json.loads(res[2]), indent=2, ensure_ascii=False)}")
    else:
        print("Aucune explication n'a encore été sauvegardée dans la table signal_scores.")

if __name__ == "__main__":
    main()
