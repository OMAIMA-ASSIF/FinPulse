package ma.enset.backend.dto;

import lombok.*;
import ma.enset.backend.entity.UserStrategy;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class StrategyDTO {
    private Long       id;
    private CompanyDTO company;
    private String     userArgument;
    private Float      nciPersonalized;
    private Boolean    isActive;
    private String     createdAt;

    public static StrategyDTO from(UserStrategy s) {
        return StrategyDTO.builder()
                .id(s.getId())
                .company(CompanyDTO.from(s.getCompany()))
                .userArgument(s.getUserArgument())
                .nciPersonalized(s.getNciPersonalized())
                .isActive(s.getIsActive())
                .createdAt(s.getCreatedAt() != null ? s.getCreatedAt().toString() : null)
                .build();
    }
}
