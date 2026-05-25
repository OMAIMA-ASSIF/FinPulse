package net.omaima.repositories;

import net.omaima.entities.ChatMessage;
import net.omaima.entities.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import org.springframework.data.domain.Pageable;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
    List<ChatMessage> findBySessionIdAndSender(Long sessionId, String sender);
    List<ChatMessage> findBySessionIdOrderByCreatedAtDesc(Long sessionId);
    List<ChatMessage> findBySessionOrderByCreatedAtAsc(ChatSession session, Pageable pageable);
}