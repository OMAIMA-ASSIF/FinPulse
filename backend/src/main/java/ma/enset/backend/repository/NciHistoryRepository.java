package ma.enset.backend.repository;

import ma.enset.backend.entity.NciHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.awt.print.Pageable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NciHistoryRepository extends JpaRepository<NciHistory,Long> {

    List<NciHistory> findByCompanyIdOrderByRecordedAtDesc(Long companyId);

    @Query("SELECT h FROM NciHistory h WHERE h.company.id = :companyId AND h.recordedAt >= :from ORDER BY h.recordedAt ASC")
    List<NciHistory> findByCompanyIdSince(@Param("companyId") Long companyId,
                                          @Param("from") LocalDateTime from);

    @Query("SELECT h FROM NciHistory h WHERE h.company.id = :companyId ORDER BY h.recordedAt DESC")
    List<NciHistory> findLatestByCompanyId(@Param("companyId") Long companyId, Pageable pageable);

    Optional<NciHistory> findTopByCompanyIdOrderByRecordedAtDesc(Integer companyId);

    // Trend detection: average NCI for last N entries
    @Query("SELECT AVG(h.nciValue) FROM NciHistory h WHERE h.company.id = :companyId AND h.recordedAt >= :from")
    Float calculateAverageNciSince(@Param("companyId") Long companyId,
                                    @Param("from") LocalDateTime from);

    // Min NCI in period (used for risk assessment)
    @Query("SELECT MIN(h.nciValue) FROM NciHistory h WHERE h.company.id = :companyId AND h.recordedAt >= :from")
    Double findMinNciSince(@Param("companyId") Integer companyId,
                           @Param("from") LocalDateTime from);
}

