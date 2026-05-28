package ma.enset.backend.simulation;

import ma.enset.backend.config.FinPulseProperties;
import ma.enset.backend.dto.NciUpdateDTO;
import ma.enset.backend.entity.Company;
import ma.enset.backend.repository.CompanyRepository;
import ma.enset.backend.service.AlertService;
import ma.enset.backend.service.NewsService;
import ma.enset.backend.service.NciHistoryService;
import ma.enset.backend.service.StrategyService;
import ma.enset.backend.sse.SseManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Component
@ConditionalOnProperty(name = "finpulse.simulation.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class SimulationScheduler {

    private final CompanyRepository companyRepository;
    private final NciHistoryService nciHistoryService;
    private final AlertService alertService;
    private final NewsService newsService;
    private final StrategyService strategyService;
    private final SseManager sseManager;
    private final FinPulseProperties properties;

    private final Random random = new Random();

    // Simulated news headlines templates
    private static final String[][] NEWS_TEMPLATES = {
            {"reports record quarterly earnings", "0.75"},
            {"faces regulatory scrutiny over data practices", "0.25"},
            {"announces major product launch", "0.70"},
            {"CEO speaks at investor day, reaffirms guidance", "0.65"},
            {"misses revenue expectations for Q3", "0.30"},
            {"acquires startup in strategic move", "0.60"},
            {"under investigation by SEC", "0.15"},
            {"expands to new international markets", "0.68"},
            {"lays off 5% of workforce amid restructuring", "0.35"},
            {"beats earnings, raises full-year outlook", "0.80"},
    };

    /**
     * Main NCI simulation loop — runs every 30 seconds (configurable).
     * Picks 1-3 random companies, updates their NCI, saves history, triggers alerts.
     */
    @Scheduled(fixedDelayString = "${finpulse.simulation.scheduler-interval:30000}")
    public void simulateNciUpdates() {
        List<Company> companies = companyRepository.findAll();
        if (companies.isEmpty()) return;

        int updatesCount = 1 + random.nextInt(Math.min(3, companies.size()));
        List<Company> toUpdate = companies.stream()
                .sorted((a, b) -> random.nextInt(3) - 1)
                .limit(updatesCount)
                .toList();

        for (Company company : toUpdate) {
            simulateCompanyUpdate(company);
        }

        log.debug("Simulation cycle complete — updated {} companies, {} SSE clients connected",
                updatesCount, sseManager.getActiveClientCount());
    }

    /**
     * Heartbeat every 15 seconds to keep SSE connections alive.
     */
    @Scheduled(fixedDelayString = "${sse.heartbeat-interval:15000}")
    public void sendHeartbeat() {
        sseManager.sendHeartbeat();
    }

    /**
     * Simulate news every 2 minutes.
     */
    @Scheduled(fixedDelay = 120_000)
    public void simulateNews() {
        List<Company> companies = companyRepository.findAll();
        if (companies.isEmpty()) return;

        // Pick a random company
        Company company = companies.get(random.nextInt(companies.size()));
        String[] template = NEWS_TEMPLATES[random.nextInt(NEWS_TEMPLATES.length)];
        String headline = company.getName() + " " + template[0];
        double sentiment = Double.parseDouble(template[1]) + (random.nextDouble() * 0.1 - 0.05);
        sentiment = Math.max(0.0, Math.min(1.0, sentiment));

        newsService.saveNews(company.getId(), headline, null,
                simulatedSource(), sentiment);
        newsService.updateCompanySentimentAverage(company.getId());

        // Trigger sentiment alert if extreme
        alertService.generateSentimentAlert(company.getId(), sentiment);

        log.debug("Simulated news: [{}] {} (sentiment: {})",
                company.getTicker(), headline, String.format("%.2f", sentiment));
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void simulateCompanyUpdate(Company company) {
        Float previousNci = company.getNciGlobal() != null ? company.getNciGlobal() : (float) 0.5;
        Float previousSentiment = company.getSentimentAvg() != null ? company.getSentimentAvg() : (float) 0.5;

        // Simulate realistic NCI drift: small random walk with mean-reversion
        Float nciDrift = (float) (random.nextGaussian() * 0.04);  // ~4% std dev per tick
        Float meanReversion = (float) ((0.65 - previousNci) * 0.05); // pull toward 0.65
        Float newNci = (float) (previousNci + nciDrift + meanReversion);
        newNci = (float) Math.max(0.05, Math.min(0.98, newNci));
        newNci = (float) (Math.round(newNci * 1000.0) / 1000.0);

        Float sentimentDrift = (float) (random.nextGaussian() * 0.03);
        Float newSentiment = (float) (previousSentiment + sentimentDrift);
        newSentiment = (float) Math.max(0.05, Math.min(0.95, newSentiment));
        newSentiment = (float) (Math.round(newSentiment * 1000.0) / 1000.0);

        // Determine reason
        String reason = determineReason(previousNci, newNci);

        // Persist
        company.setNciGlobal(newNci);
        company.setSentimentAvg(newSentiment);
        companyRepository.save(company);

        // Save history entry
        nciHistoryService.recordNci(company.getId(), newNci, reason);

        // Recalculate personalized NCI for all users watching this company
        strategyService.recalculatePersonalizedNci(company.getId());

        // Generate alerts
        alertService.generateAlertsForNciChange(company.getId(), newNci,
                previousNci, properties.getNci().getAlertThreshold());

        // Special: communication crisis if NCI drops below 0.25
        if (newNci < 0.25 && previousNci >= 0.25) {
            alertService.generateCommunicationCrisisAlert(company.getId());
        }

        // Broadcast SSE event to watching clients
        String trend = resolveTrend(newNci, previousNci);
        NciUpdateDTO event = NciUpdateDTO.builder()
                .companyId(company.getId())
                .ticker(company.getTicker())
                .name(company.getName())
                .nciValue(newNci)
                .previousNci(previousNci)
                .sentimentAvg(newSentiment)
                .trend(trend)
                .timestamp(LocalDateTime.now())
                .build();

        sseManager.broadcastNciUpdate(event);

        log.debug("Updated {}: NCI {} → {} [{}]",
                company.getTicker(), String.format("%.3f", previousNci),
                String.format("%.3f", newNci), trend);
    }

    private String determineReason(double prev, double next) {
        double delta = next - prev;
        if (delta < -0.07) return "Significant narrative inconsistency detected";
        if (delta < -0.03) return "Minor narrative shift observed";
        if (delta > 0.07)  return "Strong improvement in communication consistency";
        if (delta > 0.03)  return "Slight improvement in narrative stability";
        return "Regular NCI update";
    }

    private String resolveTrend(double newNci, double prev) {
        double delta = newNci - prev;
        if (delta > 0.02)  return "UP";
        if (delta < -0.02) return "DOWN";
        return "STABLE";
    }

    private String simulatedSource() {
        String[] sources = {"Reuters", "Bloomberg", "CNBC", "WSJ", "Financial Times",
                "MarketWatch", "Seeking Alpha", "Yahoo Finance"};
        return sources[random.nextInt(sources.length)];
    }
}
