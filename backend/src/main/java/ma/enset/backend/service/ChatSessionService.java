package ma.enset.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.enset.backend.entity.ChatMessage;
import ma.enset.backend.entity.ChatSession;
import ma.enset.backend.entity.User;
import ma.enset.backend.repository.ChatMessageRepository;
import ma.enset.backend.repository.ChatSessionRepository;

/**
 * Service pour gérer les sessions de chat avec l'orchestre multi-agent.
 * Gère la création de sessions, l'enregistrement des messages, et la récupération de l'historique.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    /**
     * Crée une nouvelle session de chat pour un utilisateur.
     */
    @Transactional
    public ChatSession createSession(User user, String ticker, String contextType, String conversationId) {
        log.info("Creating chat session: user={}, ticker={}, type={}, conversationId={}",
                user.getUsername(), ticker, contextType, conversationId);

        ChatSession session = new ChatSession();
        session.setUser(user);
        session.setCompanyTicker(ticker);
        session.setContextType(contextType);
        session.setStartedAt(LocalDateTime.now());
        session.setConversationId(conversationId != null ? conversationId : UUID.randomUUID().toString());

        return sessionRepository.save(session);
    }

    public ChatSession createSession(User user, String ticker, String contextType) {
        return createSession(user, ticker, contextType, null);
    }

    /**
     * Sauvegarde un message dans la session.
     */
    @Transactional
    public ChatMessage saveMessage(ChatSession session, String sender, String message,
                                   String intent, Double nciSnapshot) {
        log.debug("Saving message: session={}, sender={}", session.getId(), sender);

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSession(session);
        chatMessage.setSender(sender);
        chatMessage.setMessage(message);
        chatMessage.setIntent(intent);
        chatMessage.setNciSnapshot(nciSnapshot);
        chatMessage.setCreatedAt(LocalDateTime.now());

        return messageRepository.save(chatMessage);
    }

    /**
     * Récupère tous les messages d'une session.
     */
    public List<ChatMessage> getSessionHistory(Long sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * Sauvegarde une session.
     */
    public ChatSession save(ChatSession session) {
        return sessionRepository.save(session);
    }

    /**
     * Termine une session.
     */
    @Transactional
    public void endSession(Long sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setEndedAt(LocalDateTime.now());
            sessionRepository.save(session);
        });
    }

    /**
     * Détecte l'intention d'un message.
     */
    public String detectIntent(String message) {
        if (Pattern.compile("rapport|pdf|analyse|stratégie", Pattern.CASE_INSENSITIVE).matcher(message).find()) {
            return "INVESTMENT_STRATEGY";
        }
        if (Pattern.compile("prix|price|stock|cours", Pattern.CASE_INSENSITIVE).matcher(message).find()) {
            return "MARKET_QUERY";
        }
        if (Pattern.compile("risque|risk|danger", Pattern.CASE_INSENSITIVE).matcher(message).find()) {
            return "RISK_ANALYSIS";
        }
        if (Pattern.compile("sentiment|news|média", Pattern.CASE_INSENSITIVE).matcher(message).find()) {
            return "SENTIMENT_QUERY";
        }
        return "GENERAL_QUERY";
    }

    /**
     * Récupère les N derniers messages d'une session.
     */
    public List<ChatMessage> getRecentMessages(ChatSession session, int limit) {
        Pageable topN = PageRequest.of(0, limit, Sort.by("createdAt").ascending());
        return messageRepository.findBySessionOrderByCreatedAtAsc(session, topN);
    }

    /**
     * Trouve la session active d'un utilisateur pour un ticker donné.
     */
    public Optional<ChatSession> findActiveSession(User user, String ticker) {
        return sessionRepository
                .findTopByUserAndCompanyTickerAndEndedAtIsNullOrderByStartedAtDesc(user, ticker);
    }

    /**
     * Trouve la dernière session active d'un utilisateur.
     */
    public Optional<ChatSession> findActiveSessionByUser(User user) {
        return sessionRepository
                .findTopByUserAndEndedAtIsNullOrderByStartedAtDesc(user);
    }

    /**
     * Récupère ou crée une session pour une conversationId donnée.
     */
    public ChatSession getOrCreateSession(User user, String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            return sessionRepository.findByConversationId(conversationId)
                    .orElseGet(() -> createSession(user, "CASUAL", "AGENT", conversationId));
        } else {
            return createSession(user, "CASUAL", "AGENT", null);
        }
    }
}
