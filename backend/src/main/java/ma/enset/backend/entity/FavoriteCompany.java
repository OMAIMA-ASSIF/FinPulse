package ma.enset.backend.entity;

import jakarta.persistence.*;
        import lombok.*;
        import java.time.LocalDateTime;

@Entity
@Table(name = "favorite_companies", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "ticker"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FavoriteCompany {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String ticker;

    private String companyName;

    private LocalDateTime addedAt;

    @PrePersist
    protected void onCreate() {
        addedAt = LocalDateTime.now();
    }
}