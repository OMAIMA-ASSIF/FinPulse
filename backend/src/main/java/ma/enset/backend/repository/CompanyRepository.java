package ma.enset.backend.repository;

import ma.enset.backend.dto.CompanyDTO;
import ma.enset.backend.entity.Company;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.awt.print.Pageable;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByTickerIgnoreCase(String ticker);

    Optional<Company> findByIngestionCompanyId(Long ingestionCompanyId);

    boolean existsByTickerIgnoreCase(String ticker);

    @Nullable List<CompanyDTO> findBySectorIgnoreCase(String sector);

    // Leaderboard: sorted by NCI descending
    Page<Company> findAllByOrderByNciGlobalDesc(org.springframework.data.domain.Pageable pageable);

    // Companies at risk (low NCI)
    @Query("SELECT c FROM Company c WHERE c.nciGlobal < :threshold ORDER BY c.nciGlobal ASC")
    @Nullable
    List<Company> findAtRiskCompanies(@Param("threshold") double threshold);

    // Global market sentiment
    @Query("SELECT AVG(c.sentimentAvg) FROM Company c")
    Double calculateGlobalSentimentAverage();

    // Global NCI average
    @Query("SELECT AVG(c.nciGlobal) FROM Company c")
    Double calculateGlobalNciAverage();

    // Top N companies by NCI
    @Query("SELECT c FROM Company c ORDER BY c.nciGlobal DESC")
    List<Company> findTopByNci(Pageable pageable);

    // Update NCI and sentiment for a company
    @Modifying
    @Query("UPDATE Company c SET c.nciGlobal = :nci, c.sentimentAvg = :sentiment, c.lastUpdate = CURRENT_TIMESTAMP WHERE c.id = :id")
    int updateNciAndSentiment(@Param("id") Integer id, @Param("nci") double nci, @Param("sentiment") double sentiment);

    // Search by name or ticker
    @Query("SELECT c FROM Company c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(c.ticker) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Company> searchByNameOrTicker(@Param("query") String query);
}
