package ma.enset.backend.repository;

import ma.enset.backend.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.awt.print.Pageable;
import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    // Fetch all alerts for a user via strategy join
    @Query("SELECT a FROM Alert a JOIN a.userStrategy s WHERE s.user.id = :userId ORDER BY a.createdAt DESC")
    Page<Alert> findByUserId(@Param("userId") Long userId, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT a FROM Alert a JOIN a.userStrategy s WHERE s.user.id = :userId AND a.isRead = false ORDER BY a.createdAt DESC")
    List<Alert> findUnreadByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(a) FROM Alert a JOIN a.userStrategy s WHERE s.user.id = :userId AND a.isRead = false")
    long countUnreadByUserId(@Param("userId") Long userId);

    List<Alert> findByUserStrategyId(Long strategyId);

    // Mark all alerts as read for a user
    @Modifying
    @Query("UPDATE Alert a SET a.isRead = true WHERE a.userStrategy.id IN (SELECT s.id FROM UserStrategy s WHERE s.user.id = :userId)")
    int markAllAsReadForUser(@Param("userId") Long userId);

    // Mark single alert as read
    @Modifying
    @Query("UPDATE Alert a SET a.isRead = true WHERE a.id = :alertId")
    int markAsRead(@Param("alertId") Long alertId);

    @Query("SELECT a FROM Alert a JOIN a.userStrategy s WHERE s.company.id = :companyId ORDER BY a.createdAt DESC")
    List<Alert> findByCompanyId(@Param("companyId") Integer companyId, Pageable pageable);
}
