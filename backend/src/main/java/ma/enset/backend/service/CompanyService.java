package ma.enset.backend.service;

import ma.enset.backend.dto.CompanyDTO;
import ma.enset.backend.dto.DashboardDTO;
import ma.enset.backend.entity.Company;
import ma.enset.backend.exception.DuplicateResourceException;
import ma.enset.backend.exception.ResourceNotFoundException;
import ma.enset.backend.p1.P1CompanyIdentity;
import ma.enset.backend.p1.P1ScoreResponse;
import ma.enset.backend.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyService {

    private final IngestionPipelineService ingestionPipelineService;
    private final CompanyRepository companyRepository;

    public List<CompanyDTO> getLeaderboard(int limit) {
        return getAllCompanies().stream()
                .filter(c -> c.getNciGlobal() != null)
                .sorted(Comparator.comparing(CompanyDTO::getNciGlobal).reversed())
                .limit(Math.min(limit, 100))
                .toList();
    }

    public List<CompanyDTO> getAllCompanies() {
        List<P1CompanyIdentity> identities = ingestionPipelineService.listCompanies();
        if (identities == null || identities.isEmpty()) {
            return List.of();
        }
        return identities.stream()
                .map(this::toCompanyDtoForList)
                .toList();
    }

    /** Discover list: score optionnel, jamais d'appel DB dans une transaction. */
    private CompanyDTO toCompanyDtoForList(P1CompanyIdentity identity) {
        try {
            P1ScoreResponse score = ingestionPipelineService.getScore(identity.getTicker());
            return CompanyDTO.fromP1(score);
        } catch (Exception e) {
            log.debug("Score unavailable for {}: {}", identity.getTicker(), e.getMessage());
            return CompanyDTO.fromP1Identity(identity, null);
        }
    }

    public CompanyDTO getById(Long id) {
        return loadDtoByP1CompanyId(id);
    }

    public CompanyDTO getByTicker(String ticker) {
        return loadDtoByTicker(ticker);
    }

    public List<CompanyDTO> getAtRiskCompanies(double threshold) {
        return getAllCompanies().stream()
                .filter(c -> c.getNciGlobal() != null && c.getNciGlobal() < threshold)
                .sorted(Comparator.comparing(CompanyDTO::getNciGlobal))
                .toList();
    }

    public List<CompanyDTO> searchCompanies(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty()) {
            return getAllCompanies();
        }
        return getAllCompanies().stream()
                .filter(c -> c.getName().toLowerCase().contains(q)
                        || c.getTicker().toLowerCase().contains(q))
                .toList();
    }

    public DashboardDTO getDashboard() {
        List<CompanyDTO> all = getAllCompanies().stream()
                .filter(c -> c.getNciGlobal() != null)
                .toList();

        double globalNci = all.stream()
                .mapToDouble(c -> c.getNciGlobal())
                .average()
                .orElse(0.0);

        double globalSentiment = all.stream()
                .filter(c -> c.getSentimentAvg() != null)
                .mapToDouble(c -> c.getSentimentAvg())
                .average()
                .orElse(0.0);

        return DashboardDTO.builder()
                .globalNciAverage(Math.round(globalNci * 1000.0) / 1000.0)
                .globalSentimentAverage(Math.round(globalSentiment * 1000.0) / 1000.0)
                .topCompanies(getLeaderboard(5))
                .atRiskCompanies(getAtRiskCompanies(0.4))
                .totalCompanies(all.size())
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public Long getCompanyId(String ticker) {
        return loadDtoByTicker(ticker).getId();
    }

    /**
     * Référence locale minimale pour FK watchlist/strategies (id = P1 company_id).
     */
    @Transactional
    public Company ensureCompanyReference(String ticker) {
        CompanyDTO dto = loadDtoByTicker(ticker);
        return companyRepository.findByIngestionCompanyId(dto.getId())
                .map(existing -> syncFromDto(existing, dto))
                .orElseGet(() -> companyRepository.findByTickerIgnoreCase(dto.getTicker())
                        .map(existing -> {
                            existing.setIngestionCompanyId(dto.getId());
                            return syncFromDto(existing, dto);
                        })
                        .orElseGet(() -> companyRepository.save(buildCompanyFromDto(dto))));
    }

    /** {@code id} exposé au frontend = {@code ingestion_company_id} P1. */
    public Company findEntityByP1Id(Long p1CompanyId) {
        return companyRepository.findByIngestionCompanyId(p1CompanyId)
                .orElseGet(() -> ensureCompanyReference(loadDtoByP1CompanyId(p1CompanyId).getTicker()));
    }

    public Company findEntityById(Long id) {
        return findEntityByP1Id(id);
    }

    public String resolveTicker(Long p1CompanyId) {
        return companyRepository.findByIngestionCompanyId(p1CompanyId)
                .map(Company::getTicker)
                .orElseGet(() -> loadDtoByP1CompanyId(p1CompanyId).getTicker());
    }

    // ── Legacy simulation hooks (no-op on market data; local row may exist for FK) ──

    @Transactional
    public void updateNciInternal(Integer companyId, double newNci, double newSentiment) {
        companyRepository.findById(companyId.longValue()).ifPresent(c -> {
            c.setNciGlobal((float) newNci);
            c.setSentimentAvg((float) newSentiment);
            c.setLastUpdate(LocalDateTime.now());
            companyRepository.save(c);
        });
    }

    @Transactional
    public CompanyDTO updateNci(Long companyId, double newNci, double newSentiment) {
        updateNciInternal(companyId.intValue(), newNci, newSentiment);
        return getById(companyId);
    }

    @Transactional
    public CompanyDTO createCompany(String ticker, String name, String sector) {
        ticker = ticker.toUpperCase();
        if (companyRepository.existsByTickerIgnoreCase(ticker)) {
            throw new DuplicateResourceException("Company", "ticker", ticker);
        }
        return loadDtoByTicker(ticker);
    }

    private CompanyDTO loadDtoByTicker(String ticker) {
        try {
            P1ScoreResponse score = ingestionPipelineService.getScore(ticker);
            return CompanyDTO.fromP1(score);
        } catch (WebClientResponseException.NotFound e) {
            throw new ResourceNotFoundException("Company with ticker: " + ticker);
        }
    }

    private CompanyDTO loadDtoByP1CompanyId(Long id) {
        List<P1CompanyIdentity> list = ingestionPipelineService.listCompanies();
        if (list != null) {
            for (P1CompanyIdentity identity : list) {
                if (id.equals(identity.getId())) {
                    return toCompanyDtoForList(identity);
                }
            }
        }
        throw new ResourceNotFoundException("Company", id);
    }

    private CompanyDTO toCompanyDtoSafe(P1CompanyIdentity identity) {
        return toCompanyDtoForList(identity);
    }

    private Company buildCompanyFromDto(CompanyDTO dto) {
        return Company.builder()
                .ticker(dto.getTicker())
                .name(dto.getName())
                .sector(dto.getSector())
                .nciGlobal(dto.getNciGlobal())
                .sentimentAvg(dto.getSentimentAvg())
                .lastUpdate(parseLastUpdate(dto.getLastUpdate()))
                .ingestionCompanyId(dto.getId())
                .build();
    }

    private Company syncFromDto(Company company, CompanyDTO dto) {
        company.setName(dto.getName());
        company.setSector(dto.getSector());
        company.setNciGlobal(dto.getNciGlobal());
        company.setSentimentAvg(dto.getSentimentAvg());
        company.setLastUpdate(parseLastUpdate(dto.getLastUpdate()));
        if (dto.getId() != null) {
            company.setIngestionCompanyId(dto.getId());
        }
        return companyRepository.save(company);
    }

    private static LocalDateTime parseLastUpdate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return LocalDateTime.now();
        }
    }
}
