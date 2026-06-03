package ma.enset.backend.dto;
import lombok.*;
import ma.enset.backend.entity.News;

import java.time.LocalDateTime;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public class NewsDTO {
        private Long id;
        private Long companyId;
        private String ticker;
        private String title;
        private String url;
        private String source;
        private Float sentimentScore;
        private String sentimentLabel;
        private LocalDateTime publishedAt;

        public static NewsDTO from(News n) {
            return NewsDTO.builder()
                    .id(n.getId())
                    .companyId(n.getCompany().getId())
                    .ticker(n.getCompany().getTicker())
                    .title(n.getTitle())
                    .url(n.getUrl())
                    .source(n.getSource())
                    .sentimentScore(n.getSentimentScore())
                    .sentimentLabel(sentimentLabel(n.getSentimentScore()))
                    .publishedAt(n.getPublishedAt())
                    .build();
        }

        private static String sentimentLabel(Float score) {
            if (score == null) return "NEUTRAL";
            if (score >= 0.6) return "POSITIVE";
            if (score <= 0.3) return "NEGATIVE";
            return "NEUTRAL";
        }
    }

