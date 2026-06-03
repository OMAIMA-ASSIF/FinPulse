package ma.enset.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import ma.enset.backend.enums.AlertType;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "alerts")
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_strategy_id",nullable = false)
    private UserStrategy userStrategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type",nullable = false)
    private AlertType alertType;

    @Column(columnDefinition = "TEXT",nullable = false)
    private String message;

    @Column(name = "is_read")
    private Boolean isRead = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;


    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}