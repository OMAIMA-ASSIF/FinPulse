package ma.enset.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ma.enset.backend.entity.StrategyUpdateLog;
import ma.enset.backend.entity.User;
import java.util.List;

public interface StrategyUpdateLogRepository extends JpaRepository<StrategyUpdateLog, Long> {
    List<StrategyUpdateLog> findByUser(User user);

    List<StrategyUpdateLog> findByUserAndTicker(User user, String ticker);
}