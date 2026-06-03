package ma.enset.backend.dto;
import lombok.*;
import ma.enset.backend.entity.ChatSession;
import ma.enset.backend.entity.ChatMessage;
import java.util.List;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ChatSessionDTO {
    private Long id; private String title; private String contextType;
    private String startedAt; private String lastMessageAt;
    private int messageCount; private List<ChatMessageDTO> messages;

    public static ChatSessionDTO fromSummary(ChatSession s) {
        return ChatSessionDTO.builder().id(s.getId()).title(s.getTitle()).contextType(s.getContextType())
                .startedAt(s.getStartedAt()!=null?s.getStartedAt().toString():null)
                .lastMessageAt(s.getLastMessageAt()!=null?s.getLastMessageAt().toString():null)
                .messageCount(s.getMessages()!=null?s.getMessages().size():0).build();
    }
    public static ChatSessionDTO fromFull(ChatSession s) {
        ChatSessionDTO dto = fromSummary(s);
        if (s.getMessages()!=null) dto.setMessages(s.getMessages().stream().map(ChatMessageDTO::from).toList());
        return dto;
    }
}

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
class ChatMessageDTO {
    private Long id; private String sender; private String message;
    private String intent; private Float nciSnapshot; private String createdAt;
    public static ChatMessageDTO from(ChatMessage m) {
        return ChatMessageDTO.builder().id(m.getId()).sender(m.getSender()).message(m.getMessage())
                .intent(m.getIntent()).nciSnapshot(m.getNciSnapshot() != null ? m.getNciSnapshot().floatValue() : null)
                .createdAt(m.getCreatedAt()!=null?m.getCreatedAt().toString():null).build();
    }
}
