import requests
import sys
import json

base = "http://localhost:8000/api/v1"
ticker = sys.argv[1] if len(sys.argv) > 1 else "AAPL"

print(f"=== Sentiment diagnostic ({ticker}) ===")

# Test health
try:
    r = requests.get("http://localhost:8000/health", timeout=5)
    r.raise_for_status()
    print("[OK] P1 API port 8000")
except Exception as e:
    print(f"[FAIL] P1 not running on 8000: {e}")
    sys.exit(1)

# Get score
try:
    r = requests.get(f"{base}/score/{ticker}", timeout=30)
    r.raise_for_status()
    score = r.json()
    news = score.get("recent_news", [])
    print(f"Company: {score.get('company_name')} ({score.get('ticker')})")
    print(f"recent_news count: {len(news)}")
    if not news:
        print("[FAIL] No news in DB — run seed_p1_demo.py")
        sys.exit(1)
    for n in news[:5]:
        s = n.get('sentiment_score')
        headline = n.get('headline', '')
        print(f"  - [{s}] {headline[:60]}...")
    scored = [n for n in news if n.get('sentiment_score') is not None]
    if not scored:
        print("[FAIL] News exists but sentiment_score is null — run FinBERT backfill")
        print("  cd Data-Ingestion-Pipeline")
        print(f"  py run_news_sentiment_backfill.py --ticker {ticker}")
        sys.exit(1)
    avg = sum(n['sentiment_score'] for n in scored) / len(scored)
    print(f"[OK] Avg sentiment (source FinBERT): {avg:.3f}")
    print("Discover should show this value (not —). Refresh browser after backend is up.")
except Exception as e:
    print(f"[FAIL] GET /score/{ticker} : {e}")
    print("Company may not exist in P1 yet — run seed_p1_demo.py")
    sys.exit(1)