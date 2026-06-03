"""
Recalculate all anomaly scores using new sigmoid formula.
This processes all filings to update embeddings with corrected scores.
"""

import logging
from app.db.session import get_db
from app.db.models import Filing
from signals.sector_autoencoder import compute_embeddings_anomaly_scores
from sqlalchemy import select

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-8s  %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger(__name__)

def recalculate_all_anomalies():
    with get_db() as db:
        # Get all filings
        filings = db.execute(select(Filing)).scalars().all()
        logger.info(f"Found {len(filings)} filings to process")
        
        success_count = 0
        failed_count = 0
        
        for i, filing in enumerate(filings, 1):
            try:
                logger.info(f"[{i}/{len(filings)}] Processing filing {filing.id}...")
                compute_embeddings_anomaly_scores(db, filing.id, commit=True)
                success_count += 1
                logger.info(f"✓ Filing {filing.id} completed")
            except Exception as e:
                failed_count += 1
                logger.warning(f"✗ Filing {filing.id} failed: {e}")
        
        logger.info(f"\n=== SUMMARY ===")
        logger.info(f"Success: {success_count}/{len(filings)}")
        logger.info(f"Failed: {failed_count}/{len(filings)}")

if __name__ == "__main__":
    recalculate_all_anomalies()
