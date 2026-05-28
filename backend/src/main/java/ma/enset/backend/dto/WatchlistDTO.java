package ma.enset.backend.dto;

import lombok.*;
import ma.enset.backend.entity.WatchlistEntry;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WatchlistDTO {
    private Long       id;
    private CompanyDTO company;
    private String     pinnedAt;

    public static WatchlistDTO from(WatchlistEntry e) {
        return WatchlistDTO.builder()
                .id(e.getId())
                .company(CompanyDTO.from(e.getCompany()))
                .pinnedAt(e.getPinnedAt() != null ? e.getPinnedAt().toString() : null)
                .build();
    }
}
