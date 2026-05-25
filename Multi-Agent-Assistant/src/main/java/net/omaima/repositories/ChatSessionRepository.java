package net.omaima.repositories;

import net.omaima.entities.ChatSession;
import net.omaima.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    List<ChatSession> findByUserIdAndCompanyTickerOrderByStartedAtDesc(Long userId, String ticker);
    List<ChatSession> findByUserIdOrderByStartedAtDesc(Long userId);
    List<ChatSession> findByCompanyTickerOrderByStartedAtDesc(String ticker);

    Optional<ChatSession> findTopByUserAndCompanyTickerAndEndedAtIsNullOrderByStartedAtDesc(User user, String ticker);

    Optional<ChatSession> findTopByUserAndEndedAtIsNullOrderByStartedAtDesc(User user);

    Optional<ChatSession> findByConversationId(String conversationId);
}