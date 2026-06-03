package ma.enset.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.enset.backend.entity.Company;
import ma.enset.backend.entity.StrategyUpdateLog;
import ma.enset.backend.entity.User;
import ma.enset.backend.entity.UserStrategy;
import ma.enset.backend.repository.CompanyRepository;
import ma.enset.backend.repository.StrategyUpdateLogRepository;
import ma.enset.backend.repository.UserRepository;
import ma.enset.backend.repository.UserStrategyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserStrategyService {

    private final UserStrategyRepository strategyRepository;
    private final StrategyUpdateLogRepository updateLogRepository;
    private final UserRepository userRepository;
    private final CompanyService companyService;

    @Transactional
    public UserStrategy createStrategy(
            Long userId,
            String ticker,
            String companyName,
            String userArgument,
            Double nciGlobal,
            Double nciPersonalized,
            Double fConsistency,
            String supportEvidence,
            String redFlags,
            Double marketSentiment,
            String finalConclusion,
            String pdfPath) {

        log.info("createStrategy: user={}, ticker={}", userId, ticker);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable: " + userId));

        Company company = companyService.ensureCompanyReference(ticker);

        UserStrategy strategy = UserStrategy.builder()
                .user(user)
                .company(company)
                .userArgument(userArgument)
                .nciPersonalized(nciPersonalized != null ? nciPersonalized.floatValue() : 0.5f)
                .isActive(true)
                .build();

        UserStrategy saved = strategyRepository.save(strategy);
        log.info("Stratégie créée: id={}", saved.getId());

        // Optionally, we can save an entry in StrategyUpdateLog for audit/traceability!
        try {
            StrategyUpdateLog updateLog = StrategyUpdateLog.builder()
                    .user(user)
                    .strategyId(String.valueOf(saved.getId()))
                    .ticker(ticker.toUpperCase())
                    .updateType("CREATION")
                    .updateContent(finalConclusion)
                    .build();
            updateLogRepository.save(updateLog);
        } catch (Exception e) {
            log.warn("Could not save initial update log: {}", e.getMessage());
        }

        return saved;
    }

    public List<UserStrategy> getActiveStrategiesByUser(Long userId) {
        log.info("getActiveStrategiesByUser: userId={}", userId);
        return strategyRepository.findByUserIdAndIsActiveTrue(userId);
    }

    @Transactional
    public void deactivateStrategy(Long strategyId) {
        log.info("deactivateStrategy: id={}", strategyId);
        strategyRepository.findById(strategyId).ifPresent(strategy -> {
            strategy.setIsActive(false);
            strategyRepository.save(strategy);
            log.info("Stratégie désactivée");
        });
    }
}
