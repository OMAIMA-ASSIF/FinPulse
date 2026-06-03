package ma.enset.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "strategy_update_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrategyUpdateLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String strategyId;
    private String ticker;
    private String updateType;

    @Column(columnDefinition = "text")
    private String updateContent;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}