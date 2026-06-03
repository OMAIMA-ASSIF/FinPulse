package ma.enset.backend.controller;

import ma.enset.backend.dto.NciHistoryDTO;
import ma.enset.backend.service.NciHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/nci-history")
@RequiredArgsConstructor
public class NciHistoryController {

    private final NciHistoryService nciHistoryService;

    /** GET /api/nci-history/{companyId} — full history */
    @GetMapping("/{companyId}")
    public ResponseEntity<List<NciHistoryDTO>> getHistory(@PathVariable Long companyId) {
        return ResponseEntity.ok(nciHistoryService.getHistoryByCompany(companyId));
    }

    /** GET /api/nci-history/{companyId}/months/{months} — last N months */
    @GetMapping("/{companyId}/months/{months}")
    public ResponseEntity<List<NciHistoryDTO>> getHistorySince(@PathVariable Long companyId,
                                                               @PathVariable int months) {
        return ResponseEntity.ok(nciHistoryService.getHistorySince(companyId, months));
    }

    /** GET /api/nci-history/{companyId}/latest?count=20 */
    @GetMapping("/{companyId}/latest")
    public ResponseEntity<List<NciHistoryDTO>> getLatest(@PathVariable Long companyId,
                                                         @RequestParam(defaultValue = "20") int count) {
        return ResponseEntity.ok(nciHistoryService.getLatestHistory(companyId, count));
    }

    /** GET /api/nci-history/{companyId}/trend */
    @GetMapping("/{companyId}/trend")
    public ResponseEntity<Map<String, String>> getTrend(@PathVariable Long companyId) {
        String trend = nciHistoryService.assessTrend(companyId);
        return ResponseEntity.ok(Map.of("companyId", companyId.toString(), "trend", trend));
    }
}
