package ma.enset.backend.repository;
import ma.enset.backend.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    List<ChatSession> findByUserIdOrderByLastMessageAtDesc(Long userId);
    @Query("SELECT s FROM ChatSession s LEFT JOIN FETCH s.messages WHERE s.id = :id AND s.user.id = :userId")
    Optional<ChatSession> findByIdAndUserIdWithMessages(@Param("id") Long id, @Param("userId") Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);
}
