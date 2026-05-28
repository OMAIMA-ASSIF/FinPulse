package ma.enset.backend.service;

import ma.enset.backend.dto.CompanyDTO;
import ma.enset.backend.dto.DashboardDTO;
import ma.enset.backend.entity.Company;
import ma.enset.backend.exception.DuplicateResourceException;
import ma.enset.backend.exception.ResourceNotFoundException;
import ma.enset.backend.repository.CompanyRepository;
import ma.enset.backend.repository.NciHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final NciHistoryRepository nciHistoryRepository;

    // ── READ ──────────────────────────────────────────────────────────────────

    public List<CompanyDTO> getLeaderboard(int limit) {
        Pageable pageable = PageRequest.of(0, Math.min(limit, 100));
        return companyRepository.findAllByOrderByNciGlobalDesc(pageable)
                .getContent()
                .stream()
                .map(CompanyDTO::from)
                .toList();
    }

    public List<CompanyDTO> getAllCompanies() {
        return companyRepository.findAll().stream()
                .map(CompanyDTO::from)
                .toList();
    }

    public CompanyDTO getById(Long id) {
        return companyRepository.findById(id)
                .map(CompanyDTO::from)
                .orElseThrow(() -> new ResourceNotFoundException("Company", id));
    }

    public CompanyDTO getByTicker(String ticker) {
        return companyRepository.findByTickerIgnoreCase(ticker)
                .map(CompanyDTO::from)
                .orElseThrow(() -> new ResourceNotFoundException("Company with ticker: " + ticker));
    }

    public List<CompanyDTO> getAtRiskCompanies(double threshold) {
        return companyRepository.findAtRiskCompanies(threshold)
                .stream()
                .map(CompanyDTO::from)
                .toList();
    }

    public List<CompanyDTO> searchCompanies(String query) {
        return companyRepository.searchByNameOrTicker(query)
                .stream()
                .map(CompanyDTO::from)
                .toList();
    }

    public DashboardDTO getDashboard() {
        Double globalNci = companyRepository.calculateGlobalNciAverage();
        Double globalSentiment = companyRepository.calculateGlobalSentimentAverage();
        List<CompanyDTO> top5 = getLeaderboard(5);
        List<CompanyDTO> atRisk = getAtRiskCompanies(0.4);

        return DashboardDTO.builder()
                .globalNciAverage(globalNci != null ? Math.round(globalNci * 1000.0) / 1000.0 : 0.0)
                .globalSentimentAverage(globalSentiment != null ? Math.round(globalSentiment * 1000.0) / 1000.0 : 0.0)
                .topCompanies(top5)
                .atRiskCompanies(atRisk)
                .totalCompanies(companyRepository.count())
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public Long getCompanyId(String ticker){
        return  companyRepository.findByTickerIgnoreCase(ticker).get().getId();
    }

    // ── WRITE ─────────────────────────────────────────────────────────────────

    @Transactional
    public CompanyDTO createCompany(String ticker, String name, String sector) {
        ticker = ticker.toUpperCase();
        if (companyRepository.existsByTickerIgnoreCase(ticker)) {
            throw new DuplicateResourceException("Company", "ticker", ticker);
        }
        Company company = Company.builder()
                .ticker(ticker)
                .name(name)
                .sector(sector)
                .nciGlobal(0.0F)
                .sentimentAvg(0.0F)
                .build();
        company = companyRepository.save(company);
        log.info("Company created: {} ({})", name, ticker);
        return CompanyDTO.from(company);
    }

    @Transactional
    public CompanyDTO updateNci(Long companyId, double newNci, double newSentiment) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        Float clampedNci = (float) Math.max(0.0, Math.min(1.0, newNci));
        Float clampedSentiment = (float) Math.max(0.0, Math.min(1.0, newSentiment));

        company.setNciGlobal(clampedNci);
        company.setSentimentAvg(clampedSentiment);
        company.setLastUpdate(LocalDateTime.now());

        company = companyRepository.save(company);
        log.debug("NCI updated for {}: {}", company.getTicker(), clampedNci);
        return CompanyDTO.from(company);
    }

    // Used internally by simulation scheduler
    @Transactional
    public void updateNciInternal(Integer companyId, double newNci, double newSentiment) {
        companyRepository.updateNciAndSentiment(companyId, newNci, newSentiment);
    }

    // Resolve company entity by ID (internal use)
    public Company findEntityById(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company", id));
    }


}
