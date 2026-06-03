package ma.enset.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "companies")
@Builder
public class Company {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(unique = true,nullable = false)
        private String ticker;

        @Column(nullable = false)
        private String name;

        private String sector;

        @Column(name = "nci_global")
        private Float nciGlobal;

        @Column(name = "sentiment_avg")
        private Float sentimentAvg;

        @Column(name = "last_update")
        private LocalDateTime lastUpdate;

        /** Identifiant entreprise dans la base P1 (source de vérité). */
        @Column(name = "ingestion_company_id", unique = true)
        private Long ingestionCompanyId;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentsEmbedding> embeddings = new ArrayList<>();

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("publishedAt DESC")
    private List<News> newsList = new ArrayList<>();

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("recordedAt DESC")
    private List<NciHistory> nciHistory = new ArrayList<>();

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.lastUpdate = LocalDateTime.now();
    }

}
