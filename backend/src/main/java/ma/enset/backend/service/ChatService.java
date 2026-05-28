package ma.enset.backend.service;

import ma.enset.backend.dto.ChatSessionDTO;
import ma.enset.backend.entity.ChatMessage;
import ma.enset.backend.entity.ChatSession;
import ma.enset.backend.entity.Company;
import ma.enset.backend.entity.User;
import ma.enset.backend.exception.ApiException;
import ma.enset.backend.exception.ResourceNotFoundException;
import ma.enset.backend.repository.ChatMessageRepository;
import ma.enset.backend.repository.ChatSessionRepository;
import ma.enset.backend.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final CompanyRepository     companyRepository;
    private final UserService           userService;
    private final ChatClient            chatClient;

    // ── SESSIONS ──────────────────────────────────────────────────────────────

    public List<ChatSessionDTO> getSessions(Long userId) {
        return sessionRepository
                .findByUserIdOrderByLastMessageAtDesc(userId)
                .stream()
                .map(ChatSessionDTO::fromSummary)
                .toList();
    }

    public ChatSessionDTO getSession(Long sessionId, Long userId) {
        return sessionRepository
                .findByIdAndUserIdWithMessages(sessionId, userId)
                .map(ChatSessionDTO::fromFull)
                .orElseThrow(() -> new ResourceNotFoundException("ChatSession", sessionId));
    }

    // ── SEND MESSAGE ──────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> sendMessage(Long userId, Long sessionId,
                                           String userMessage, String intent,
                                           Long companyId) {
        User user = userService.findEntityById(userId);

        // Get or create session
        ChatSession session;
        if (sessionId != null) {
            session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new ResourceNotFoundException("ChatSession", sessionId));
            if (!session.getUser().getId().equals(userId))
                throw new ApiException("Access denied", HttpStatus.FORBIDDEN);
        } else {
            Company company = null;
            if (companyId != null)
                company = companyRepository.findById(companyId.longValue()).orElse(null);

            session = ChatSession.builder()
                    .user(user)
                    .title(truncate(userMessage, 60))
                    .contextType(intent != null ? intent : "AGENT")
                    .company(company)
                    .build();
            session = sessionRepository.save(session);
        }

        // Save user message
        messageRepository.save(ChatMessage.builder()
                .session(session).sender("USER")
                .message(userMessage).intent(intent)
                .build());

        // Build prompt
        String systemPrompt = """
            You are FinPulse AI — an expert financial intelligence assistant specializing
            in corporate narrative consistency analysis (NCI).

            [STRATEGY] requests → generate structured investment strategy reports:
              thesis validation, bull case, SEC contradictions, risks, recommendation (BUY/HOLD/AVOID)

            [AGENT] requests → concise conversational financial analysis:
              NCI scores, narrative consistency, SEC risk factors, market sentiment

            Always be factual, professional, and highlight narrative inconsistencies.
            """;

        String prompt = "[" + (intent != null ? intent : "AGENT") + "] " + userMessage;

        // Call Spring AI
        String aiResponse;
        try {
            aiResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("[ChatService] Spring AI error: {}", e.getMessage());
            aiResponse = "⚠️ AI service temporarily unavailable. Please try again.";
        }

        // Save AI response
        messageRepository.save(ChatMessage.builder()
                .session(session).sender("AI")
                .message(aiResponse).intent(intent)
                .build());

        // Update session metadata
        session.setLastMessageAt(LocalDateTime.now());
        if (session.getMessages() != null && session.getMessages().size() <= 2) {
            session.setTitle(truncate(userMessage, 60));
        }
        sessionRepository.save(session);

        return Map.of(
                "sessionId", session.getId(),
                "response",  aiResponse,
                "intent",    intent != null ? intent : "AGENT"
        );
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteSession(Long sessionId, Long userId) {
        if (!sessionRepository.existsByIdAndUserId(sessionId, userId))
            throw new ApiException("Session not found or access denied", HttpStatus.NOT_FOUND);
        sessionRepository.deleteById(sessionId);
        log.info("[ChatService] Session {} deleted by user {}", sessionId, userId);
    }

    @Transactional
    public void deleteAllSessions(Long userId) {
        List<ChatSession> sessions = sessionRepository.findByUserIdOrderByLastMessageAtDesc(userId);
        sessionRepository.deleteAll(sessions);
        log.info("[ChatService] {} sessions deleted for user {}", sessions.size(), userId);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String truncate(String text, int max) {
        if (text == null || text.isBlank()) return "New conversation";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
