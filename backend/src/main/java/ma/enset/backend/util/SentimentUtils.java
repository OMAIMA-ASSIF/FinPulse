package ma.enset.backend.util;

import ma.enset.backend.p1.P1NewsItem;

import java.util.List;

/**
 * Sentiment scores from P1 / FinBERT: raw value in [-1, 1] (positive − negative).
 * No rescaling — values are passed through exactly as stored in the pipeline.
 */
public final class SentimentUtils {

    private SentimentUtils() {}

    public static Float roundRaw(Double raw) {
        if (raw == null) {
            return null;
        }
        return (float) Math.round(raw * 1000.0) / 1000.0f;
    }

    public static Float averageFromNews(List<P1NewsItem> news) {
        if (news == null || news.isEmpty()) {
            return null;
        }
        double avg = news.stream()
                .map(P1NewsItem::getSentimentScore)
                .filter(score -> score != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(Double.NaN);
        if (Double.isNaN(avg)) {
            return null;
        }
        return (float) Math.round(avg * 1000.0) / 1000.0f;
    }

    public static float neutralScore() {
        return 0.0f;
    }
}
