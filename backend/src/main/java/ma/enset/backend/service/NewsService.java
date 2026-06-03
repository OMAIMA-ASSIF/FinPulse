package ma.enset.backend.service;

import ma.enset.backend.dto.NewsDTO;
import ma.enset.backend.p1.P1NewsItem;
import ma.enset.backend.p1.P1ScoreResponse;
import ma.enset.backend.util.SentimentUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NewsService {

    private final IngestionPipelineService ingestionPipelineService;
    private final CompanyService companyService;

    private final AtomicLong syntheticNewsId = new AtomicLong(1);

    public Page<NewsDTO> getNewsByCompany(Long companyId, int page, int size) {
        List<NewsDTO> all = getLatestNews(companyId);
        if (all.isEmpty()) {
            return new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
        }
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        if (from >= to) {
            return new PageImpl<>(List.of(), PageRequest.of(page, size), all.size());
        }
        return new PageImpl<>(all.subList(from, to), PageRequest.of(page, size), all.size());
    }

    public List<NewsDTO> getLatestNews(Long companyId) {
        String ticker = companyService.resolveTicker(companyId);
        P1ScoreResponse score = ingestionPipelineService.getScore(ticker);
        if (score.getRecentNews() == null) {
            return List.of();
        }
        return score.getRecentNews().stream()
                .map(n -> toNewsDto(companyId, ticker, n))
                .toList();
    }

    public Double getAverageSentiment(Long companyId) {
        String ticker = companyService.resolveTicker(companyId);
        P1ScoreResponse score = ingestionPipelineService.getScore(ticker);
        Float avg = SentimentUtils.averageFromNews(score.getRecentNews());
        return avg != null ? avg.doubleValue() : 0.0;
    }

    private NewsDTO toNewsDto(Long companyId, String ticker, P1NewsItem item) {
        Float raw = SentimentUtils.roundRaw(item.getSentimentScore());
        return NewsDTO.builder()
                .id(syntheticNewsId.getAndIncrement())
                .companyId(companyId)
                .ticker(ticker)
                .title(item.getHeadline())
                .url(null)
                .source(item.getSource())
                .sentimentScore(raw)
                .sentimentLabel(sentimentLabel(raw))
                .publishedAt(item.getPublishedAt() != null
                        ? item.getPublishedAt().toLocalDateTime() : null)
                .build();
    }

    private static String sentimentLabel(Float score) {
        if (score == null) return "NEUTRAL";
        if (score > 0.05f) return "POSITIVE";
        if (score < -0.05f) return "NEGATIVE";
        return "NEUTRAL";
    }
}
