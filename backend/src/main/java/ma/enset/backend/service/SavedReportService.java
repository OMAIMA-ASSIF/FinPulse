package ma.enset.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ma.enset.backend.entity.SavedReport;
import ma.enset.backend.entity.User;
import ma.enset.backend.repository.SavedReportRepository;
import ma.enset.backend.dto.SavedReportDTO;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavedReportService {

    private final SavedReportRepository reportRepository;
    private final CurrentUserService currentUserService;

    @CacheEvict(value = "user-reports", key = "#userId")
    public SavedReportDTO saveReport(Long userId, String ticker, String title, byte[] pdfContent) {
        User user = currentUserService.getUserById(userId);

        SavedReport report = SavedReport.builder()
                .user(user)
                .ticker(ticker.toUpperCase())
                .reportTitle(title)
                .pdfContent(pdfContent)
                .build();

        SavedReport saved = reportRepository.save(report);
        log.info("Saved report for ticker {} by user {}", ticker, userId);

        return toDTO(saved);
    }

    @Cacheable(value = "user-reports", key = "#userId")
    public List<SavedReportDTO> getUserReports(Long userId) {
        List<SavedReport> reports = reportRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        return reports.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public SavedReportDTO getReportDetail(Long userId, Long reportId) {
        User user = currentUserService.getUserById(userId);
        SavedReport report = reportRepository.findByIdAndUser(reportId, user)
                .orElseThrow(() -> new RuntimeException("Rapport non trouvé"));
        return toDTO(report);
    }

    @CacheEvict(value = "user-reports", key = "#userId")
    public void deleteReport(Long userId, Long reportId) {
        User user = currentUserService.getUserById(userId);
        SavedReport report = reportRepository.findByIdAndUser(reportId, user)
                .orElseThrow(() -> new RuntimeException("Rapport non trouvé"));

        reportRepository.delete(report);
        log.info("Deleted report {} for user {}", reportId, userId);
    }

    private SavedReportDTO toDTO(SavedReport report) {
        return SavedReportDTO.builder()
                .id(report.getId())
                .ticker(report.getTicker())
                .reportTitle(report.getReportTitle())
                .createdAt(report.getCreatedAt())
                .build();
    }
}