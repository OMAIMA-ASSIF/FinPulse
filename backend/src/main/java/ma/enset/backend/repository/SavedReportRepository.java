package ma.enset.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ma.enset.backend.entity.SavedReport;
import ma.enset.backend.entity.User;
import java.util.List;
import java.util.Optional;

public interface SavedReportRepository extends JpaRepository<SavedReport, Long> {
    List<SavedReport> findByUser(User user);

    List<SavedReport> findByUserAndTicker(User user, String ticker);

    Optional<SavedReport> findByIdAndUser(Long id, User user);

    @Query("SELECT sr FROM SavedReport sr WHERE sr.user.id = :userId ORDER BY sr.createdAt DESC")
    List<SavedReport> findAllByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
}