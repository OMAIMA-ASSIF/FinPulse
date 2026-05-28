package ma.enset.backend.repository;

import ma.enset.backend.entity.WatchlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistRepository extends JpaRepository<WatchlistEntry, Long> {

    List<WatchlistEntry> findByUserIdOrderByPinnedAtDesc(Long userId);

    Optional<WatchlistEntry> findByUserIdAndCompanyId(Long userId, Long companyId);

    boolean existsByUserIdAndCompanyId(Long userId, Long companyId);

    void deleteByUserIdAndCompanyId(Long userId, Long companyId);

    @Query("SELECT w.company.id FROM WatchlistEntry w WHERE w.user.id = :userId")
    List<Long> findCompanyIdsByUserId(@Param("userId") Long userId);
}
