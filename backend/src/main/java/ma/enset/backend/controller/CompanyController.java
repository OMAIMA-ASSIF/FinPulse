package ma.enset.backend.controller;

import ma.enset.backend.dto.CompanyDTO;
import ma.enset.backend.dto.DashboardDTO;
import ma.enset.backend.dto.PriceDTO;
import ma.enset.backend.service.CompanyService;
import ma.enset.backend.service.PriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;
    private final PriceService   priceService;

    @GetMapping
    public ResponseEntity<List<CompanyDTO>> getAll() {
        return ResponseEntity.ok(companyService.getAllCompanies());
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDTO> getDashboard() {
        return ResponseEntity.ok(companyService.getDashboard());
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<CompanyDTO>> getLeaderboard(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(companyService.getLeaderboard(limit));
    }

    @GetMapping("/at-risk")
    public ResponseEntity<List<CompanyDTO>> getAtRisk(@RequestParam(defaultValue = "0.4") double threshold) {
        return ResponseEntity.ok(companyService.getAtRiskCompanies(threshold));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CompanyDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(companyService.getById(id));
    }

    @GetMapping("/ticker/{ticker}")
    public ResponseEntity<CompanyDTO> getByTicker(@PathVariable String ticker) {
        return ResponseEntity.ok(companyService.getByTicker(ticker));
    }

    @GetMapping("/search")
    public ResponseEntity<List<CompanyDTO>> search(@RequestParam String q) {
        return ResponseEntity.ok(companyService.searchCompanies(q));
    }

    /**
     * GET /api/companies/{id}/price
     * Appelle Alpha Vantage côté backend — la clé API n'est jamais exposée au frontend.
     */
    @GetMapping("/{id}/price")
    public ResponseEntity<PriceDTO> getPrice(@PathVariable Long id) {
        return ResponseEntity.ok(priceService.getPrice(id));
    }

    @GetMapping("ticker/{ticker}/id")
    public ResponseEntity<Long> getCompanyId(@PathVariable String ticker){
        return ResponseEntity.ok(companyService.getCompanyId(ticker));
    }
}
