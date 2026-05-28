package ma.enset.backend.service;

import ma.enset.backend.dto.StrategyDTO;
import ma.enset.backend.dto.StrategyRequestDTO;
import ma.enset.backend.dto.StrategyReportDTO;
import ma.enset.backend.entity.Company;
import ma.enset.backend.entity.User;
import ma.enset.backend.entity.UserStrategy;
import ma.enset.backend.exception.ApiException;
import ma.enset.backend.exception.DuplicateResourceException;
import ma.enset.backend.exception.ResourceNotFoundException;
import ma.enset.backend.repository.CompanyRepository;
import ma.enset.backend.repository.UserStrategyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StrategyService {

    private final UserStrategyRepository strategyRepository;
    private final UserService            userService;
    private final CompanyRepository      companyRepository;
    private final WatchlistService       watchlistService;   // ← auto-pin
    private final ChatClient             chatClient;

    // ── READ ───────────────────────────────────────────────────────────────────

    public List<StrategyDTO> getUserStrategies(Long userId) {
        return strategyRepository.findByUserIdAndIsActiveTrue(userId)
                .stream().map(StrategyDTO::from).toList();
    }

    public List<StrategyDTO> getAllUserStrategies(Long userId) {
        return strategyRepository.findByUserId(userId)
                .stream().map(StrategyDTO::from).toList();
    }

    public StrategyDTO getStrategyById(Long id) {
        return strategyRepository.findById(id)
                .map(StrategyDTO::from)
                .orElseThrow(() -> new ResourceNotFoundException("Strategy", id));
    }

    // ── WRITE ──────────────────────────────────────────────────────────────────

    /**
     * Crée une stratégie ET ajoute automatiquement l'entreprise à la watchlist.
     * La watchlist et les stratégies restent indépendants — unpin watchlist
     * ne supprime PAS la stratégie.
     */
    @Transactional
    public StrategyDTO createStrategy(Long userId, StrategyRequestDTO request) {
        User user = userService.findEntityById(userId);
        Company company = companyRepository.findById(request.getCompanyId().longValue())
                .orElseThrow(() -> new ResourceNotFoundException("Company", request.getCompanyId()));

        if (strategyRepository.existsByUserIdAndCompanyId(userId, request.getCompanyId())) {
            throw new DuplicateResourceException("Strategy", "company",
                    company.getTicker() + " already has a strategy for user " + userId);
        }

        Float personalizedNci = computePersonalizedNci(user, company, request.getUserArgument());

        UserStrategy strategy = UserStrategy.builder()
                .user(user)
                .company(company)
                .userArgument(request.getUserArgument())
                .nciPersonalized(personalizedNci)
                .isActive(true)
                .build();

        UserStrategy saved = strategyRepository.save(strategy);

        // ── Auto-pin dans la watchlist (idempotent) ──────────────────────────
        // Règle métier : Save Strategy → ajoute à la Watchlist automatiquement.
        // Mais unpin Watchlist NE supprimera jamais cette stratégie.
        try {
            watchlistService.pin(userId, company.getId().longValue());
            log.info("Auto-pinned company {} to watchlist after strategy creation", company.getTicker());
        } catch (Exception e) {
            // Ne pas faire échouer la création de stratégie si le pin échoue
            log.warn("Could not auto-pin company {} to watchlist: {}", company.getTicker(), e.getMessage());
        }

        return StrategyDTO.from(saved);
    }

    @Transactional
    public void deactivateStrategy(Long strategyId, Long userId) {
        UserStrategy s = getAndVerifyOwnership(strategyId, userId);
        s.setIsActive(false);
        strategyRepository.save(s);
        log.info("Strategy {} deactivated by user {}", strategyId, userId);
        // NE PAS toucher à la watchlist
    }

    @Transactional
    public void reactivateStrategy(Long strategyId, Long userId) {
        UserStrategy s = getAndVerifyOwnership(strategyId, userId);
        s.setIsActive(true);
        strategyRepository.save(s);
        log.info("Strategy {} reactivated by user {}", strategyId, userId);
    }

    @Transactional
    public void deleteStrategy(Long strategyId, Long userId) {
        UserStrategy s = getAndVerifyOwnership(strategyId, userId);
        strategyRepository.delete(s);
        log.info("Strategy {} deleted by user {}", strategyId, userId);
        // NE PAS toucher à la watchlist
    }

    // ── REPORT ────────────────────────────────────────────────────────────────

    public StrategyReportDTO generateReport(Long strategyId, Long userId) {
        UserStrategy s = getAndVerifyOwnership(strategyId, userId);

        String prompt = """
            Generate an investment strategy report for %s (%s).
            User thesis: %s
            NCI Global: %.1f/100 | Personalized NCI: %.1f/100 | Sector: %s | Sentiment: %.1f%%
            
            Respond with a structured analysis including:
            1. Bull case (3 points)
            2. Key risks (3 points)
            3. SEC narrative contradictions (if any)
            4. Historical insight
            5. Final recommendation: BUY, HOLD, or AVOID
            
            Rule: NCI >= 65 → BUY, 40-64 → HOLD, < 40 → AVOID
            """.formatted(
                s.getCompany().getName(), s.getCompany().getTicker(),
                s.getUserArgument() != null ? s.getUserArgument() : "General watchlist monitoring",
                s.getCompany().getNciGlobal()  != null ? s.getCompany().getNciGlobal()  * 100 : 50.0,
                s.getNciPersonalized()         != null ? s.getNciPersonalized()         * 100 : 50.0,
                s.getCompany().getSector(),
                s.getCompany().getSentimentAvg() != null ? s.getCompany().getSentimentAvg() * 100 : 50.0
        );

        String aiResponse;
        try {
            aiResponse = chatClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            log.warn("Spring AI unavailable for report generation: {}", e.getMessage());
            aiResponse = "AI service temporarily unavailable.";
        }

        return StrategyReportDTO.builder()
                .id(s.getId())
                .ticker(s.getCompany().getTicker())
                .companyName(s.getCompany().getName())
                .thesis(s.getUserArgument() != null ? s.getUserArgument() : "General watchlist monitoring")
                .bullCase(List.of("Strong narrative consistency", "Market position", "Sector momentum"))
                .risks(List.of("Regulatory exposure", "Market volatility", "NCI fluctuation risk"))
                .secContradictions(List.of())
                .historicalInsight("NCI score of " + Math.round(
                        (s.getCompany().getNciGlobal() != null ? s.getCompany().getNciGlobal() : 0.5f) * 100)
                        + " reflects current narrative consistency level.")
                .recommendation(computeRecommendation(s.getNciPersonalized()))
                .nciPersonalized(s.getNciPersonalized())
                .rawAiResponse(aiResponse)
                .generatedAt(LocalDateTime.now().toString())
                .build();
    }

    public byte[] generateReportPdf(Long strategyId, Long userId) {
        StrategyReportDTO report = generateReport(strategyId, userId);
        String html = """
            <html><body style="font-family:Arial;padding:32px;">
            <h1>FinPulse Strategy Report</h1>
            <h2>%s — %s</h2>
            <p><strong>Recommendation:</strong> %s</p>
            <p><strong>NCI Personalized:</strong> %.1f/100</p>
            <h3>Thesis</h3><p>%s</p>
            <p style="color:gray;font-size:12px">Generated: %s</p>
            </body></html>
            """.formatted(
                report.getCompanyName(), report.getTicker(),
                report.getRecommendation(),
                report.getNciPersonalized() != null ? report.getNciPersonalized() * 100 : 0,
                report.getThesis(), report.getGeneratedAt()
        );
        return html.getBytes(StandardCharsets.UTF_8);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    @Transactional
    public void recalculatePersonalizedNci(Long companyId) {
        List<UserStrategy> strategies = strategyRepository.findActiveByCompanyWithUser(companyId);
        for (UserStrategy s : strategies) {
            s.setNciPersonalized(computePersonalizedNci(s.getUser(), s.getCompany(), s.getUserArgument()));
        }
        if (!strategies.isEmpty()) strategyRepository.saveAll(strategies);
    }

    public UserStrategy findEntityById(Long id) {
        return strategyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Strategy", id));
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private UserStrategy getAndVerifyOwnership(Long strategyId, Long userId) {
        UserStrategy s = strategyRepository.findById(strategyId)
                .orElseThrow(() -> new ResourceNotFoundException("Strategy", strategyId));
        if (!s.getUser().getId().equals(userId)) {
            throw new ApiException("You do not own this strategy", HttpStatus.FORBIDDEN);
        }
        return s;
    }

    float computePersonalizedNci(User user, Company company, String argument) {
        double base       = company.getNciGlobal() != null ? company.getNciGlobal() : 0.5;
        double multiplier = switch (user.getProfileType()) {
            case PRUDENT     -> 1.0;
            case SPECULATEUR -> 1.15;
        };
        double boost = analyzeArgumentSentiment(argument);
        return (float) Math.max(0.0, Math.min(1.0,
                Math.round((base * multiplier + boost) * 1000.0) / 1000.0));
    }

    private double analyzeArgumentSentiment(String argument) {
        if (argument == null || argument.isBlank()) return 0.0;
        String lower = argument.toLowerCase();
        double score = 0.0;
        for (String w : new String[]{"strong","growth","innovative","leader","profit","bullish"})
            if (lower.contains(w)) score += 0.02;
        for (String w : new String[]{"risk","debt","volatile","lawsuit","investigation","bearish"})
            if (lower.contains(w)) score -= 0.02;
        return Math.max(-0.1, Math.min(0.1, score));
    }

    private String computeRecommendation(Float nci) {
        if (nci == null) return "HOLD";
        if (nci >= 0.65f) return "BUY";
        if (nci >= 0.40f) return "HOLD";
        return "AVOID";
    }
}
