package ma.enset.backend.scheduler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.enset.backend.dto.NciUpdateDTO;
import ma.enset.backend.entity.Company;
import ma.enset.backend.p1.P1ScoreResponse;
import ma.enset.backend.repository.CompanyRepository;
import ma.enset.backend.service.AlertService;
import ma.enset.backend.service.IngestionPipelineService;
import ma.enset.backend.sse.SseManager;
import ma.enset.backend.util.SentimentUtils;

/**
 * Poll P1 scores for cached company references and push SSE updates (remplace la simulation locale).
 */
@Component
@ConditionalOnProperty(name = "finpulse.simulation.enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class P1ScorePoller {

    private final CompanyRepository companyRepository;
    private final IngestionPipelineService ingestionPipelineService;
    private final SseManager sseManager;
    private final AlertService alertService;

    private final Map<String, CachedScore> lastScores = new HashMap<>();

    @Scheduled(fixedDelayString = "${finpulse.p1.poll-interval:120000}")
    public void pollScores() {
        Set<String> tickers = new HashSet<>();
        for (Company company : companyRepository.findAll()) {
            if (company.getTicker() != null) {
                tickers.add(company.getTicker().toUpperCase());
            }
        }
        if (tickers.isEmpty()) {
            return;
        }

        for (String ticker : tickers) {
            try {
                pollOne(ticker);
            } catch (Exception e) {
                log.debug("P1 poll failed for {}: {}", ticker, e.getMessage());
            }
        }
    }

    private void pollOne(String ticker) {
        P1ScoreResponse score = ingestionPipelineService.getScore(ticker);
        if (score.getCompositeRiskScore() == null) {
            return;
        }

        float nci = score.getCompositeRiskScore().floatValue();
        float sentiment = averageSentiment(score);

        CachedScore previous = lastScores.get(ticker);
        if (previous != null && Math.abs(previous.nci - nci) < 0.01f
                && Math.abs(previous.sentiment - sentiment) < 0.01f) {
            return;
        }

        float prevNci = previous != null ? previous.nci : nci;
        lastScores.put(ticker, new CachedScore(nci, sentiment));

        companyRepository.findByTickerIgnoreCase(ticker).ifPresent(company -> {
            company.setNciGlobal(nci);
            company.setSentimentAvg(sentiment);
            company.setLastUpdate(LocalDateTime.now());
            companyRepository.save(company);

            Long publicId = company.getIngestionCompanyId() != null
                    ? company.getIngestionCompanyId() : company.getId();

            if (previous != null && Math.abs(prevNci - nci) >= 0.03f) {
                alertService.generateAlertsForNciChange(company.getId(), nci, prevNci, 0.4);
            }

            NciUpdateDTO event = NciUpdateDTO.builder()
                    .companyId(publicId)
                    .ticker(ticker)
                    .name(company.getName())
                    .nciValue(nci)
                    .previousNci(prevNci)
                    .sentimentAvg(sentiment)
                    .trend(resolveTrend(nci, prevNci))
                    .timestamp(LocalDateTime.now())
                    .build();
            sseManager.broadcastNciUpdate(event);
            log.debug("P1 poll update {} NCI {} -> {}", ticker, prevNci, nci);
        });
    }

    private static float averageSentiment(P1ScoreResponse score) {
        Float avg = SentimentUtils.averageFromNews(score.getRecentNews());
        return avg != null ? avg : SentimentUtils.neutralScore();
    }

    private static String resolveTrend(float nci, float prev) {
        float delta = nci - prev;
        if (delta > 0.02f) return "UP";
        if (delta < -0.02f) return "DOWN";
        return "STABLE";
    }

    private record CachedScore(float nci, float sentiment) {}
}
