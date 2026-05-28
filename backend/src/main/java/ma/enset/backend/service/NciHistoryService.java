package ma.enset.backend.service;

import ma.enset.backend.dto.NciHistoryDTO;
import ma.enset.backend.entity.Company;
import ma.enset.backend.entity.NciHistory;
import ma.enset.backend.exception.ResourceNotFoundException;
import ma.enset.backend.repository.CompanyRepository;
import ma.enset.backend.repository.NciHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.print.Pageable;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NciHistoryService {

    private final NciHistoryRepository nciHistoryRepository;
    private final CompanyRepository companyRepository;

    public List<NciHistoryDTO> getHistoryByCompany(Long companyId) {
        if (!companyRepository.existsById(companyId)) {
            throw new ResourceNotFoundException("Company", companyId);
        }
        return nciHistoryRepository.findByCompanyIdOrderByRecordedAtDesc(companyId)
                .stream()
                .map(NciHistoryDTO::from)
                .toList();
    }

    public List<NciHistoryDTO> getHistorySince(Long companyId, int months) {
        if (!companyRepository.existsById(companyId)) {
            throw new ResourceNotFoundException("Company", companyId);
        }
        LocalDateTime from = LocalDateTime.now().minusMonths(months);
        return nciHistoryRepository.findByCompanyIdSince(companyId, from)
                .stream()
                .map(NciHistoryDTO::from)
                .toList();
    }

    public List<NciHistoryDTO> getLatestHistory(Long companyId, int count) {
        if (!companyRepository.existsById(companyId)) {
            throw new ResourceNotFoundException("Company", companyId);
        }
        return nciHistoryRepository.findLatestByCompanyId(companyId, (Pageable) PageRequest.of(0, count))
                .stream()
                .map(NciHistoryDTO::from)
                .toList();
    }

    @Transactional
    public NciHistory recordNci(Long companyId, Float nciValue, String reason) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        NciHistory history = NciHistory.builder()
                .company(company)
                .nciValue(nciValue)
                .recordedAt(LocalDateTime.now())
                .reason(reason)
                .build();

        NciHistory saved = nciHistoryRepository.save(history);
        log.debug("NCI history recorded: company={} nci={}", company.getTicker(), nciValue);
        return saved;
    }

    /**
     * Compute a risk assessment: trend over last 30 days.
     * Returns: "IMPROVING", "DECLINING", or "STABLE"
     */
    public String assessTrend(Long companyId) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        Float recent = nciHistoryRepository.calculateAverageNciSince(companyId, thirtyDaysAgo);
        Float sixtyDaysAgo = nciHistoryRepository.calculateAverageNciSince(companyId,
                LocalDateTime.now().minusDays(60));

        if (recent == null || sixtyDaysAgo == null) return "STABLE";

        double delta = recent - sixtyDaysAgo;
        if (delta > 0.05) return "IMPROVING";
        if (delta < -0.05) return "DECLINING";
        return "STABLE";
    }
}
