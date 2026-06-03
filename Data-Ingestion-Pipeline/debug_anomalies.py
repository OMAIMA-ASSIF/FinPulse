from sqlalchemy import create_engine, text

engine = create_engine('postgresql://finpulse:finpulse_secret@localhost:5432/finpulse')
with engine.connect() as conn:
    result = conn.execute(text('''
SELECT e.filing_id, e.anomaly_score
FROM embeddings e
JOIN filings f ON e.filing_id = f.id  
JOIN companies c ON f.company_id = c.id
WHERE c.ticker = 'AAPL' AND e.anomaly_score IS NOT NULL
ORDER BY e.anomaly_score DESC
LIMIT 15
    '''))
    print("\n=== DB: Top 15 anomaly scores for AAPL ===")
    for i, (fid, score) in enumerate(result, 1):
        print(f'{i}. Filing {fid}: {score}')
