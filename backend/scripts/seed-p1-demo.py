import requests
import time
import sys
import subprocess
import os
from pathlib import Path

base = "http://localhost:8000/api/v1"
tickers = ["AAPL", "MSFT", "TSLA"]
script_dir = Path(__file__).parent
p1_root = (script_dir / "../../Data-Ingestion-Pipeline").resolve()
if not p1_root.exists():
    p1_root = Path("C:/Users/4B/Downloads/GLSID S4/projet_innovation/FinPulse/FinPulse/Data-Ingestion-Pipeline")

def wait_pipeline_job(job_id, timeout_sec=900):
    deadline = time.time() + timeout_sec
    while time.time() < deadline:
        try:
            resp = requests.get(f"{base}/pipelines/jobs/{job_id}", timeout=30)
            if resp.status_code == 200:
                data = resp.json()
                status = data.get('status')
                print(f"    job {job_id} -> {status}")
                if status == "completed":
                    return True
                if status == "failed":
                    print(f"    job failed: {data.get('error')}")
                    return False
            else:
                print(f"    poll error: HTTP {resp.status_code}")
        except Exception as e:
            print(f"    poll error: {e}")
        time.sleep(8)
    print(f"    timeout waiting for job {job_id}")
    return False

def test_ticker_sentiment(ticker):
    try:
        resp = requests.get(f"{base}/score/{ticker}", timeout=30)
        if resp.status_code != 200:
            print(f"  {ticker}: API error {resp.status_code}")
            return False
        data = resp.json()
        news = data.get("recent_news", [])
        if not news:
            print(f"  {ticker}: no recent_news")
            return False
        scored = [n for n in news if n.get("sentiment_score") is not None]
        print(f"  {ticker}: {len(scored)}/{len(news)} news with sentiment_score")
        if scored:
            avg = sum(n["sentiment_score"] for n in scored) / len(scored)
            print(f"  {ticker}: avg sentiment (raw FinBERT) = {avg:.3f}")
            return True
        return False
    except Exception as e:
        print(f"  {ticker} score check failed: {e}")
        return False

print("=== FinPulse P1 demo seed ===")

# Check P1 health
print("1) P1 health...")
try:
    r = requests.get("http://localhost:8000/health", timeout=10)
    r.raise_for_status()
except Exception as e:
    print(f"ERROR: P1 not reachable on port 8000. Start it first:")
    print(f"  cd \"{p1_root}\"")
    print("  docker compose up -d")
    print("  py -m uvicorn main:app --port 8000")
    sys.exit(1)

# Backfill jobs
job_ids = []
print("2) Backfill jobs...")
for t in tickers:
    print(f"  Backfill {t} ...")
    payload = {
        "identifier": t,
        "ten_k_max": 1,
        "ten_q_max": 1,
        "form4_max": 5,
        "news_limit": 15,
        "run_signals": True
    }
    try:
        resp = requests.post(f"{base}/pipelines/backfill/company", json=payload, timeout=60)
        resp.raise_for_status()
        data = resp.json()
        job_id = data.get("job_id")
        print(f"    started job {job_id}")
        job_ids.append(job_id)
    except Exception as e:
        print(f"    backfill failed for {t}: {e}")

if not job_ids:
    print("ERROR: No backfill jobs started.")
    sys.exit(1)

print("3) Waiting for backfill jobs (can take 5-15 min)...")
for jid in job_ids:
    wait_pipeline_job(jid)

print("4) FinBERT sentiment backfill on existing news...")
os.chdir(p1_root)
for t in tickers:
    print(f"  sentiment backfill {t} ...")
    try:
        # Run the backfill script (assuming it exists)
        subprocess.run(["py", "run_news_sentiment_backfill.py", "--ticker", t], check=False, capture_output=True, text=True)
    except Exception as e:
        print(f"    sentiment backfill failed for {t}: {e}")
        print("    (FinBERT needs: pip install transformers torch — first run downloads the model)")

os.chdir(script_dir)

print("5) Verification...")
ok = True
for t in tickers:
    if not test_ticker_sentiment(t):
        ok = False

print()
if ok:
    print("OK — sentiment present in P1. Refresh Discover in the browser.")
    print("If Discover still shows —, restart backend: cd backend; docker compose up -d --build")
else:
    print("Sentiment still missing in P1.")
    print("Common causes:")
    print("  - FinBERT not installed (cd Data-Ingestion-Pipeline; pip install -r requirements.txt)")
    print("  - No RSS news fetched for ticker (check backfill job logs in P1 terminal)")
    print("  - P1 API not restarted after code changes (Ctrl+C then uvicorn again)")
    print()
    print("Manual check:")
    print("  curl http://localhost:8000/api/v1/score/AAPL")