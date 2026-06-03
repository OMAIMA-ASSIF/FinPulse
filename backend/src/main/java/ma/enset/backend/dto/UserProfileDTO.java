package ma.enset.backend.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UserProfileDTO {
        private Long          id;
        private String        keycloakId;
        private String        username;
        private String        email;
        private String        profileType;
        private int           strategyCount;
        private LocalDateTime createdAt;
        private String        avatarUrl;
        private boolean       firstLogin;
}
