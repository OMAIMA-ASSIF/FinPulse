package ma.enset.backend.service;

import ma.enset.backend.dto.NciHistoryDTO;
import ma.enset.backend.p1.P1SignalHistoryPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NciHistoryService {

    private final IngestionPipelineService ingestionPipelineService;
    private final CompanyService companyService;

    public List<NciHistoryDTO> getHistoryByCompany(Long companyId) {
        return fetchHistory(companyId, 200);
    }

    public List<NciHistoryDTO> getHistorySince(Long companyId, int months) {
        LocalDateTime from = LocalDateTime.now().minusMonths(months);
        return fetchHistory(companyId, 500).stream()
                .filter(h -> h.getRecordedAt() != null && !h.getRecordedAt().isBefore(from))
                .toList();
    }

    public List<NciHistoryDTO> getLatestHistory(Long companyId, int count) {
        return fetchHistory(companyId, count);
    }

    public String assessTrend(Long companyId) {
        List<NciHistoryDTO> points = getHistorySince(companyId, 6);
        if (points.size() < 2) {
            return "STABLE";
        }
        List<NciHistoryDTO> sorted = points.stream()
                .sorted(Comparator.comparing(NciHistoryDTO::getRecordedAt))
                .toList();
        int mid = sorted.size() / 2;
        double older = sorted.subList(0, mid).stream()
                .mapToDouble(h -> h.getNciValue() != null ? h.getNciValue() : 0)
                .average().orElse(0);
        double recent = sorted.subList(mid, sorted.size()).stream()
                .mapToDouble(h -> h.getNciValue() != null ? h.getNciValue() : 0)
                .average().orElse(0);
        double delta = recent - older;
        if (delta > 0.05) return "IMPROVING";
        if (delta < -0.05) return "DECLINING";
        return "STABLE";
    }

    private List<NciHistoryDTO> fetchHistory(Long companyId, int limit) {
        String ticker = companyService.resolveTicker(companyId);
        List<P1SignalHistoryPoint> points = ingestionPipelineService.getSignalHistory(ticker, limit);
        if (points == null) {
            return List.of();
        }
        return points.stream()
                .map(p -> NciHistoryDTO.fromP1(companyId, ticker, p))
                .toList();
    }
}
