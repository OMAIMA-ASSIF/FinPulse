package ma.enset.backend.repository;


import ma.enset.backend.entity.UserStrategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserStrategyRepository extends JpaRepository<UserStrategy, Long> {

    List<UserStrategy> findByUserIdAndIsActiveTrue(Long userId);

    List<UserStrategy> findByUserId(Long userId);

    List<UserStrategy> findByCompanyId(Long companyId);

    Optional<UserStrategy> findByUserIdAndCompanyId(Long userId, Integer companyId);

    boolean existsByUserIdAndCompanyId(Long userId, Long companyId);

    @Query("SELECT s FROM UserStrategy s WHERE s.user.id = :userId AND s.isActive = true ORDER BY s.nciPersonalized ASC")
    List<UserStrategy> findActiveByUserOrderByNciAsc(@Param("userId") Long userId);

    @Query("SELECT COUNT(s) FROM UserStrategy s WHERE s.company.id = :companyId AND s.isActive = true")
    long countActiveByCompany(@Param("companyId") Integer companyId);

    // All active strategies for a company (used to generate bulk alerts)
    @Query("SELECT s FROM UserStrategy s JOIN FETCH s.user WHERE s.company.id = :companyId AND s.isActive = true")
    List<UserStrategy> findActiveByCompanyWithUser(@Param("companyId") Long companyId);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.userStrategy.user.id = :userId AND a.isRead = false")
    long countUnreadAlertsByUserId(@Param("userId") Long userId);
}
