package ma.enset.backend.service;

import ma.enset.backend.dto.AlertDTO;
import ma.enset.backend.entity.Alert;
import ma.enset.backend.entity.UserStrategy;
import ma.enset.backend.enums.AlertType;
import ma.enset.backend.exception.ApiException;
import ma.enset.backend.exception.ResourceNotFoundException;
import ma.enset.backend.repository.AlertRepository;
import ma.enset.backend.repository.UserStrategyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.print.Pageable;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AlertService {

    private final AlertRepository alertRepository;
    private final UserStrategyRepository strategyRepository;

    // ── READ ──────────────────────────────────────────────────────────────────

    public Page<AlertDTO> getAlertsForUser(Long userId, int page, int size) {
        return alertRepository.findByUserId(userId,  PageRequest.of(page, size))
                .map(AlertDTO::from);
    }

    public List<AlertDTO> getUnreadAlertsForUser(Long userId) {
        return alertRepository.findUnreadByUserId(userId).stream()
                .map(AlertDTO::from)
                .toList();
    }

    public long countUnreadAlerts(Long userId) {
        return alertRepository.countUnreadByUserId(userId);
    }

    // ── WRITE ─────────────────────────────────────────────────────────────────

    @Transactional
    public AlertDTO markAsRead(Long alertId, Long userId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", alertId));

        // Ownership check
        if (!alert.getUserStrategy().getUser().getId().equals(userId)) {
            throw new ApiException("You do not own this alert", HttpStatus.FORBIDDEN);
        }

        alert.setIsRead(true);
        alert = alertRepository.save(alert);
        return AlertDTO.from(alert);
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        int updated = alertRepository.markAllAsReadForUser(userId);
        log.info("Marked {} alerts as read for user {}", updated, userId);
        return updated;
    }

    // ── AUTO-GENERATION (called by SimulationScheduler) ───────────────────────

    /**
     * Evaluate a company's new NCI and generate alerts for all affected strategies.
     * This is the core "smart alert" logic.
     *
     * @param companyId   company that was updated
     * @param newNci      fresh NCI value
     * @param previousNci NCI before the update
     * @param threshold   alert threshold from config
     */
    @Transactional
    public void generateAlertsForNciChange(Long companyId, double newNci,
                                           double previousNci, double threshold) {
        List<UserStrategy> strategies = strategyRepository.findActiveByCompanyWithUser(companyId);
        if (strategies.isEmpty()) return;

        double delta = newNci - previousNci;
        boolean dropped = delta < -0.05;
        boolean rose    = delta >  0.05;
        boolean critical = newNci < threshold;

        for (UserStrategy strategy : strategies) {
            if (critical && dropped) {
                createAlert(strategy, AlertType.NCI_DROP,
                        String.format("⚠️ %s NCI has dropped to %.2f (critical zone). Immediate review recommended.",
                                strategy.getCompany().getTicker(), newNci));
            } else if (dropped) {
                createAlert(strategy, AlertType.NCI_DROP,
                        String.format("%s NCI decreased from %.2f to %.2f.",
                                strategy.getCompany().getTicker(), previousNci, newNci));
            } else if (rose) {
                createAlert(strategy, AlertType.NCI_RISE,
                        String.format("✅ %s NCI improved from %.2f to %.2f.",
                                strategy.getCompany().getTicker(), previousNci, newNci));
            }
        }
    }

    @Transactional
    public void generateSentimentAlert(Long companyId, double sentimentScore) {
        List<UserStrategy> strategies = strategyRepository.findActiveByCompanyWithUser(companyId);

        for (UserStrategy strategy : strategies) {
            if (sentimentScore < 0.3) {
                createAlert(strategy, AlertType.SENTIMENT_NEGATIVE,
                        String.format("📉 Negative news sentiment detected for %s (score: %.2f). Market perception may be deteriorating.",
                                strategy.getCompany().getTicker(), sentimentScore));
            } else if (sentimentScore > 0.7) {
                createAlert(strategy, AlertType.SENTIMENT_POSITIVE,
                        String.format("📈 Positive market sentiment for %s (score: %.2f).",
                                strategy.getCompany().getTicker(), sentimentScore));
            }
        }
    }

    @Transactional
    public void generateCommunicationCrisisAlert(Long companyId) {
        List<UserStrategy> strategies = strategyRepository.findActiveByCompanyWithUser(companyId);
        for (UserStrategy strategy : strategies) {
            createAlert(strategy, AlertType.COMMUNICATION_CRISIS,
                    String.format("🚨 Communication crisis risk detected for %s. Multiple conflicting statements in recent disclosures.",
                            strategy.getCompany().getTicker()));
        }
    }

    // ── INTERNAL ─────────────────────────────────────────────────────────────

    private Alert createAlert(UserStrategy strategy, AlertType type, String message) {
        Alert alert = Alert.builder()
                .userStrategy(strategy)
                .alertType(type)
                .message(message)
                .isRead(false)
                .build();
        Alert saved = alertRepository.save(alert);
        log.debug("Alert created: [{}] {} -> user {}",
                type, strategy.getCompany().getTicker(), strategy.getUser().getUsername());
        return saved;
    }
}
