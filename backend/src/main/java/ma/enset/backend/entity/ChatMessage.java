package ma.enset.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

    @Entity @Table(name = "chat_messages", indexes = @Index(name = "idx_chat_messages_session", columnList = "session_id"))
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public class ChatMessage {

        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "session_id", nullable = false)
        private ChatSession session;

        @Column(name = "sender", length = 10, nullable = false)
        private String sender;

        @Column(name = "message", columnDefinition = "TEXT", nullable = false)
        private String message;

        @Column(name = "intent", length = 50)
        private String intent;

        @Column(name = "nci_snapshot")
        private Float nciSnapshot;

        @Column(name = "created_at", nullable = false, updatable = false)
        private LocalDateTime createdAt;

        @PrePersist protected void onCreate() {
            createdAt = LocalDateTime.now();
        }
    }

