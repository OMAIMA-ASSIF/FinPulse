package ma.enset.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ma.enset.backend.entity.FavoriteCompany;
import ma.enset.backend.entity.User;
import java.util.List;
import java.util.Optional;

public interface FavoriteCompanyRepository extends JpaRepository<FavoriteCompany, Long> {
    List<FavoriteCompany> findByUser(User user);

    Optional<FavoriteCompany> findByUserAndTicker(User user, String ticker);

    @Query("SELECT fc FROM FavoriteCompany fc WHERE fc.user.id = :userId ORDER BY fc.addedAt DESC")
    List<FavoriteCompany> findAllByUserIdOrderByAddedAtDesc(@Param("userId") Long userId);

    boolean existsByUserAndTicker(User user, String ticker);
}