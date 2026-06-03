package ma.enset.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import ma.enset.backend.entity.ChatSession;
import ma.enset.backend.entity.User;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    List<ChatSession> findByUserIdAndCompanyTickerOrderByStartedAtDesc(Long userId, String ticker);
    List<ChatSession> findByUserIdOrderByStartedAtDesc(Long userId);
    List<ChatSession> findByCompanyTickerOrderByStartedAtDesc(String ticker);

    Optional<ChatSession> findTopByUserAndCompanyTickerAndEndedAtIsNullOrderByStartedAtDesc(User user, String ticker);

    Optional<ChatSession> findTopByUserAndEndedAtIsNullOrderByStartedAtDesc(User user);

    Optional<ChatSession> findByConversationId(String conversationId);

    // Métodes requises par ChatService
    List<ChatSession> findByUserIdOrderByLastMessageAtDesc(Long userId);

    @Query("SELECT cs FROM ChatSession cs LEFT JOIN FETCH cs.messages WHERE cs.id = :id AND cs.user.id = :userId")
    Optional<ChatSession> findByIdAndUserIdWithMessages(@Param("id") Long id, @Param("userId") Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);
}
