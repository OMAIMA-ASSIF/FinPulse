package ma.enset.backend.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ma.enset.backend.entity.ChatMessage;
import ma.enset.backend.entity.ChatSession;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
    List<ChatMessage> findBySessionIdAndSender(Long sessionId, String sender);
    List<ChatMessage> findBySessionIdOrderByCreatedAtDesc(Long sessionId);
    List<ChatMessage> findBySessionOrderByCreatedAtAsc(ChatSession session, Pageable pageable);
}
