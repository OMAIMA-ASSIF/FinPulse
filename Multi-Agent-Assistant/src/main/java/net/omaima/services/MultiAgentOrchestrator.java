package net.omaima.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.omaima.agent.*;
import net.omaima.entities.ChatMessage;
import net.omaima.entities.ChatSession;
import net.omaima.entities.User;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class MultiAgentOrchestrator {

    private final IngestionPipelineService ingestionPipelineService;
    private final ChatSessionService chatSessionService;
    private final ChatClient chatClient;
    private final Agent1SupportExtractor agent1;
    private final Agent2RedFlagsExtractor agent2;
    private final Agent3FinalSynthesizer agent3;
    private final Agent4PdfAssembly agent4;
    private final Agent5MarketNews agent5;

    // =====================================================================
    // POINT D'ENTRÉE UNIQUE
    // =====================================================================

    @Transactional
    public OrchestratorResult handleMessage(User user, String ticker, String userMessage, String conversationId) {
        log.info("=== ORCHESTRATOR === user={} ticker={} conversationId={}", user.getUsername(), ticker, conversationId);

        // 1. Obtenir ou créer la session unique de la conversation
        ChatSession session;

        if (conversationId != null && !conversationId.isBlank()) {
            session = chatSessionService.getOrCreateSession(user, conversationId);
        } else {
            // Fallback pour tests sans conversationId
            Optional<ChatSession> active = chatSessionService.findActiveSessionByUser(user)
                    .filter(s -> !"CASUAL".equals(s.getCompanyTicker()));
            if (active.isPresent()) {
                session = active.get();
                log.info("Réutilisation de la session active (fallback test) id={}", session.getId());
            } else {
                session = chatSessionService.getOrCreateSession(user, null);
            }
        }

        // 2. Déterminer le ticker effectif
        if (ticker == null || ticker.isBlank()) {
            // a. Extraction depuis le message
            String extracted = extractTickerFromMessage(userMessage);

            // b. Si toujours rien, chercher dans l'historique de la session
            if (extracted == null) {
                extracted = findTickerInSessionHistory(session);
            }

            // c. Plusieurs entreprises détectées
            if ("MULTIPLE".equals(extracted)) {
                return OrchestratorResult.clarification(
                        "Vous avez mentionné plusieurs entreprises. Veuillez en choisir une seule.",
                        session.getConversationId()
                );
            }

            // d. Un ticker a été trouvé
            if (extracted != null) {
                ticker = extracted;
                log.info("Ticker résolu : {}", ticker);
            } else {
                // Aucune entreprise → mode conversationnel
                return OrchestratorResult.chatResponse(
                        handleCasualChat(user, userMessage, session),
                        session.getConversationId()
                );
            }
        }

        // 3. Mettre à jour le ticker courant de la session (optionnel, pratique pour l'affichage)
        session.setCompanyTicker(ticker);
        chatSessionService.save(session);

        // 4. Vérifier si l'entreprise existe dans la base
        if (!checkIfCompanyExists(ticker)) {
            try {
                ingestionPipelineService.triggerBackfillPipeline(ticker);
            } catch (Exception e) {
                log.warn("Backfill trigger failed for {}: {}", ticker, e.getMessage());
            }
            return OrchestratorResult.clarification(
                    "L'entreprise " + ticker + " n'est pas encore dans notre base. " +
                            "Son ingestion est lancée. Veuillez réessayer dans 15 minutes.",
                    session.getConversationId()
            );
        }

        // 5. Détection d'intention
        IntentResult intent;
        try {
            intent = detectIntentWithLLM(userMessage, ticker);
        } catch (Exception e) {
            log.error("LLM intent detection failed, falling back to CHATBOT mode", e);
            intent = new IntentResult(Mode.CHATBOT, null);
        }
        log.info("Intent: {}", intent.mode());

        // 6. Sauvegarder le message utilisateur dans l'historique de la session
        chatSessionService.saveMessage(session, "USER", userMessage, intent.mode().toString(), null);

        // 7. Router selon l'intention
        return switch (intent.mode()) {
            case OUT_OF_SCOPE -> OrchestratorResult.clarification(
                    "Je suis un assistant financier spécialisé. " +
                            "Posez-moi une question sur " + ticker + " ou une entreprise cotée.",
                    session.getConversationId()
            );
            case NEEDS_CLARIFICATION -> OrchestratorResult.clarification(
                    intent.clarificationQuestion(),
                    session.getConversationId()
            );
            case REPORT -> OrchestratorResult.reportGenerated(
                    generateStrategyReport(user, ticker, userMessage)
            );
            default -> OrchestratorResult.chatResponse(
                    handleChatbot(user, ticker, userMessage, session),
                    session.getConversationId()
            );
        };
    }
    private String findTickerInSessionHistory(ChatSession session) {
        List<ChatMessage> recent = chatSessionService.getRecentMessages(session, 10);
        // parcourir du plus récent au plus ancien
        for (int i = recent.size() - 1; i >= 0; i--) {
            String msg = recent.get(i).getMessage();
            String extracted = extractTickerFromMessage(msg);
            if (extracted != null && !"MULTIPLE".equals(extracted)) {
                return extracted;
            }
        }
        return null;
    }
    // =====================================================================
    // DÉTECTION D'INTENTION
    // =====================================================================

    private IntentResult detectIntentWithLLM(String message, String ticker) {
        try {
            String prompt = String.format("""
                Tu es le routeur intelligent d'un assistant financier FinPulse.
                
                TICKER: %s
                MESSAGE: "%s"
                
                Classifie en:
                - CHATBOT : question factuelle sur l'entreprise
                - REPORT  : demande d'analyse stratégique avec un argument d'investissement clair
                - OUT_OF_SCOPE : sans rapport avec la finance
                - NEEDS_CLARIFICATION : veut un rapport mais argument trop vague ou absent
                
                Si NEEDS_CLARIFICATION, formule une question pour guider l'utilisateur
                vers un argument d'investissement précis.
                
                Réponds UNIQUEMENT en JSON:
                {
                  "mode": "CHATBOT|REPORT|OUT_OF_SCOPE|NEEDS_CLARIFICATION",
                  "clarification_question": "... ou null"
                }
                """, ticker, message);

            String response = chatClient.prompt().user(prompt).call().content();
            int start = response.indexOf("{");
            int end   = response.lastIndexOf("}") + 1;

            if (start >= 0 && end > start) {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var node   = mapper.readTree(response.substring(start, end));
                String modeStr = node.get("mode").asText("CHATBOT");
                String clarif  = node.has("clarification_question")
                        && !node.get("clarification_question").isNull()
                        ? node.get("clarification_question").asText() : null;

                Mode mode = switch (modeStr) {
                    case "REPORT"              -> Mode.REPORT;
                    case "OUT_OF_SCOPE"        -> Mode.OUT_OF_SCOPE;
                    case "NEEDS_CLARIFICATION" -> Mode.NEEDS_CLARIFICATION;
                    default                    -> Mode.CHATBOT;
                };
                return new IntentResult(mode, clarif);
            }
        } catch (Exception e) {
            log.error("Erreur détection intent → fallback CHATBOT", e);
        }
        return new IntentResult(Mode.CHATBOT, null);
    }

    // =====================================================================
    // MODE 1 : CHATBOT
    // =====================================================================

    @Transactional
    public String handleChatbot(User user, String ticker, String userMessage, ChatSession session) {
        log.info("=== MODE CHATBOT ===");

        try {
            log.info("Fetching company name...");
            String companyName = ingestionPipelineService.getCompanyName(ticker);
            log.info("Company name: {}", companyName);

            log.info("Fetching NCI Global...");
            Double nciGlobal = ingestionPipelineService.getNciGlobal(ticker);
            log.info("NCI Global: {}", nciGlobal);

            log.info("Fetching embedding text...");
            String embeddingText = ingestionPipelineService.getLatestEmbeddingText(ticker, 0);
            log.info("Embedding text length: {}", embeddingText != null ? embeddingText.length() : 0);

            log.info("Fetching filed_at...");
            String filedAt = ingestionPipelineService.getLatestEmbeddingFiledAt(ticker);
            log.info("Filed at: {}", filedAt);

            log.info("Fetching price...");
            Double priceClose = ingestionPipelineService.getPriceClose(ticker);
            log.info("Price: {}", priceClose);

            // --- Load recent conversation history ---
            List<ChatMessage> history = chatSessionService.getRecentMessages(session, 6);
            StringBuilder historyText = new StringBuilder();
            if (!history.isEmpty()) {
                for (ChatMessage msg : history) {
                    historyText.append(msg.getSender()).append(": ").append(msg.getMessage()).append("\n");
                }
            }

            String secContext = String.format(
                    "Entreprise: %s (%s)\nNCI Global: %.2f\nRapport SEC: %s\nPrix: $%.2f\n\n%s",
                    companyName, ticker, nciGlobal, filedAt, priceClose, embeddingText);

            log.info("Calling Mistral AI...");
            String aiResponse = chatClient.prompt()
                    .user(u -> u.text("""
                            Tu es un assistant financier expert et factuel pour FinPulse.
                            
                            RÈGLES :
                            - Base ta réponse sur les données SEC fournies.
                            - Si une information n'est pas dans le contexte, indique simplement qu'elle n'est pas disponible.
                            - Ne jamais inventer de chiffres, dates ou faits.
                            - Le NCI Global est un score entre 0 et 1, ne le convertis jamais en dollars ou milliards, explique‑le comme un indicateur de cohérence narrative.
                            - Sois concis, professionnel et utile.
                            - Tu peux reformuler les données pour les rendre compréhensibles, mais sans extrapoler.
                            - Utilise l'historique de conversation pour maintenir le contexte et éviter les répétitions.
                            
                            HISTORIQUE DE LA CONVERSATION :
                            {history}
                            
                            CONTEXTE SEC :
                            {context}
                            
                            QUESTION : {question}
                            """)
                            .param("filedAt", filedAt)
                            .param("history", historyText.toString())
                            .param("context", secContext)
                            .param("question", userMessage))
                    .call().content();
            log.info("AI response received: {} chars", aiResponse != null ? aiResponse.length() : 0);

            chatSessionService.saveMessage(session, "USER", userMessage,
                    chatSessionService.detectIntent(userMessage), nciGlobal);
            chatSessionService.saveMessage(session, "AI", aiResponse, "RESPONSE", nciGlobal);
            return aiResponse;

        } catch (org.springframework.ai.retry.NonTransientAiException e) {
            log.warn("Mistral AI rate limited");
            return "Trop de requêtes en peu de temps. Merci d'attendre une minute avant de réessayer.";
        } catch (Exception e) {
            log.error("Erreur chatbot: {}", e.getMessage(), e);
            return "Une erreur s'est produite. Veuillez réessayer.";
        }
    }



    /**
     * Uses Mistral to extract a stock ticker from a natural language message.
     * Returns the uppercase ticker symbol, or null if none is found.
     */
    private String extractTickerFromMessage(String message) {
        // 1. LLM extraction – now also detects MULTIPLE
        String llmResult = null;
        try {
            String prompt = """
            You are a stock ticker extractor.
            From the following user message, return ONLY:
            - a single stock ticker symbol (uppercase, 1-5 letters) if exactly one company is mentioned,
            - the word "MULTIPLE" if more than one company is mentioned,
            - the word "NONE" if no company or ticker is mentioned.
            Never return any other text.
            
            Examples:
            "Tell me about Apple" → AAPL
            "Compare Apple and Microsoft" → MULTIPLE
            "Bonjour" → NONE
            "donne moi le nci globale de SEMPRA" → SRE
            
            Message: "%s"
            """.formatted(message);

            String response = chatClient.prompt().user(prompt).call().content();
            String cleaned = response.trim().toUpperCase().replaceAll("[^A-Z]", "");

            if (cleaned.equals("MULTIPLE")) {
                return "MULTIPLE";                 // <-- explicit multi‑company signal
            }
            if (!cleaned.equals("NONE") && cleaned.length() >= 2 && cleaned.length() <= 5) {
                llmResult = cleaned;
            }
        } catch (Exception e) {
            log.warn("LLM ticker extraction error", e);
        }

        // 2. P1 validation only for a single ticker candidate
        if (llmResult != null) {
            try {
                String companyName = ingestionPipelineService.getCompanyName(llmResult);
                if (companyName != null && !companyName.isBlank()) {
                    log.info("Ticker {} validated with P1 API (company: {})", llmResult, companyName);
                    return llmResult;
                }
            } catch (Exception e) {
                log.warn("P1 validation failed for ticker {}", llmResult);
            }
        }

        return null;   // no valid single ticker found
    }
    /**
     * Handles casual conversation without any ticker.
     * Uses Mistral to generate a friendly, finance‑aware response.
     */
    public String handleCasualChat(User user, String userMessage, ChatSession session) {
        log.info("=== MODE CASUAL CHAT ===");
        try {
            String aiResponse = chatClient.prompt()
                    .user(u -> u.text("""
                Tu es FinPulse assistant, un assistant financier sympathique et professionnel.
                
                RÈGLES:
                - Si l'utilisateur te salue, réponds de manière amicale et propose ton aide pour analyser des entreprises cotées.
                - Si la question n'est pas liée à la finance, rappelle poliment que tu es spécialisé en analyse financière et donne des exemples de ce que tu peux faire (analyser une entreprise, générer un rapport, donner le sentiment du marché).
                - Reste concis et utile.
                
                Message de l'utilisateur : {message}
                """)
                            .param("message", userMessage))
                    .call().content();

            chatSessionService.saveMessage(session, "USER", userMessage, "CASUAL", null);
            chatSessionService.saveMessage(session, "AI", aiResponse, "RESPONSE", null);
            return aiResponse;

        } catch (Exception e) {
            log.error("Erreur casual chat", e);
            return "Désolé, une erreur est survenue. Pouvez-vous reformuler ?";
        }
    }

    // =====================================================================
    // MODE 2 : GÉNÉRATION RAPPORT — SANS sauvegarde automatique
    // =====================================================================

    /**
     * Génère le rapport et retourne toutes les données calculées.
     * La sauvegarde en base N'EST PAS faite ici.
     * Elle sera faite UNIQUEMENT si l'utilisateur clique sur
     * "Enregistrer la stratégie" → POST /api/v2/strategy/save
     */
    public ReportResult generateStrategyReport(User user, String ticker, String userArgument) {
        log.info("=== MODE RAPPORT (génération sans sauvegarde) ===");
        try {
            String companyName   = ingestionPipelineService.getCompanyName(ticker);
            Double nciGlobal     = ingestionPipelineService.getNciGlobal(ticker);
            String embeddingText = ingestionPipelineService.getLatestEmbeddingText(ticker, 0);
            Double priceClose    = ingestionPipelineService.getPriceClose(ticker);
            Double sentiment     = ingestionPipelineService.getSentimentScore(ticker);

            List<String> news = agent5.getRecentNews(ticker);
            if (news.isEmpty()) news = List.of("Aucune actualité disponible pour " + ticker);

            log.info("Phase 1: Agent1...");
            List<String> supportPoints = agent1.extractSupportEvidence(
                    userArgument, embeddingText, companyName);

            log.info("Phase 2: Agent2...");
            Agent2RedFlagsExtractor.RiskAnalysisResult risk =
                    agent2.analyzeRedFlags(userArgument, embeddingText, nciGlobal);

            log.info("Phase 3: Agent3...");
            String finalConclusion = agent3.synthesizeFinalConclusion(
                    userArgument, supportPoints, risk.redFlags(),
                    risk.fConsistency(), sentiment, priceClose, news);

            log.info("Agent4: PDF...");
            byte[] pdfBytes = agent4.generateStrategyReport(
                    ticker, companyName, userArgument,
                    supportPoints, risk.redFlags(),
                    risk.fConsistency(), nciGlobal, risk.nciPersonalized(),
                    sentiment, finalConclusion);

            log.info("Rapport généré ({} bytes) — sauvegarde en attente du choix utilisateur", pdfBytes.length);

            return new ReportResult(
                    pdfBytes, ticker, companyName, userArgument,
                    nciGlobal, risk.nciPersonalized(), risk.fConsistency(),
                    sentiment, supportPoints.toString(), risk.redFlags().toString(), finalConclusion
            );

        } catch (Exception e) {
            log.error("Erreur génération rapport", e);
            throw new RuntimeException("Échec génération rapport", e);
        }
    }


    public boolean checkIfCompanyExists(String ticker) {
        try {
            String companyName = ingestionPipelineService.getCompanyName(ticker);
            log.info("Company check for {}: '{}'", ticker, companyName);

            if (companyName != null && !companyName.isBlank()) {
                return true;
            }

            log.warn("Company {} not found (null or blank name)", ticker);
            return false;

        } catch (Exception e) {
            log.error("Error checking company {}: {}", ticker, e.getMessage());
            return false;
        }
    }
    public enum Mode { CHATBOT, REPORT, OUT_OF_SCOPE, NEEDS_CLARIFICATION }

    public record IntentResult(Mode mode, String clarificationQuestion) {}

    /**
     * Toutes les données calculées lors de la génération du rapport.
     * Retournées au frontend pour affichage ET pour la sauvegarde optionnelle.
     */
    public record ReportResult(
            byte[]  pdfBytes,
            String  ticker,
            String  companyName,
            String  userArgument,
            Double  nciGlobal,
            Double  nciPersonalized,
            Double  fConsistency,
            Double  sentiment,
            String  supportEvidence,
            String  redFlags,
            String  finalConclusion
    ) {}

    public record OrchestratorResult(
            Mode         mode,
            String       textResponse,
            ReportResult reportResult,
            String       conversationId
    ) {
        public static OrchestratorResult chatResponse(String text, String conversationId) {
            return new OrchestratorResult(Mode.CHATBOT, text, null, conversationId);
        }
        public static OrchestratorResult clarification(String question, String conversationId) {
            return new OrchestratorResult(Mode.NEEDS_CLARIFICATION, question, null, conversationId);
        }
        public static OrchestratorResult reportGenerated(ReportResult r) {
            return new OrchestratorResult(Mode.REPORT, null, r, null);
        }
    }
}