package ma.enset.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "watchlist",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_watchlist_user_company",
                columnNames = {"user_id", "company_id"}
        )
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WatchlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "pinned_at", nullable = false, updatable = false)
    private LocalDateTime pinnedAt;

    @PrePersist
    protected void onCreate() {
        this.pinnedAt = LocalDateTime.now();
    }
}
