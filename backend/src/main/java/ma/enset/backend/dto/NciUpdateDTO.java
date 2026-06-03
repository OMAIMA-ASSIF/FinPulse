package ma.enset.backend.dto;


import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder

public class NciUpdateDTO {

        private Long companyId;
        private String ticker;
        private String name;
        private Float nciValue;
        private Float previousNci;
        private Float sentimentAvg;
        private String trend;       // UP / DOWN / STABLE
        private LocalDateTime timestamp;
    }

