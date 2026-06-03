from app.db.session import SessionLocal
from sqlalchemy import text
import json

def main():
    db = SessionLocal()
    # On cherche spécifiquement pour le filing_id 31 et le signal_name 'nci_global'
    query = """
    SELECT filing_id, signal_name, detail->>'llm_explanation' as summary
    FROM signal_scores
    WHERE filing_id=31 AND signal_name='nci_global'
    """
    res = db.execute(text(query)).fetchone()
    
    if res:
        print("====== RÉSULTAT POUR LE FILING 31 ======")
        print(f"Filing ID : {res[0]}")
        print(f"Signal Name : {res[1]}")
        print(f"Explication LLM : {res[2]}")
    else:
        print("La ligne nci_global pour le filing 31 n'existe pas dans la base de données.")

if __name__ == "__main__":
    main()
