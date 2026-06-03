package ma.enset.backend.dto;

import lombok.*;
import ma.enset.backend.entity.User;
import ma.enset.backend.enums.ProfileType;

import java.time.LocalDateTime;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public class UserDTO {
        private Long id;
        private String username;
        private String email;
        private ProfileType profileType;
        private LocalDateTime createdAt;
        private long strategyCount;

        public static UserDTO from(User u) {
            return UserDTO.builder()
                    .id(u.getId())
                    .username(u.getUsername())
                    .email(u.getEmail())
                    .profileType(u.getProfileType())
                    .createdAt(u.getCreatedAt())
                    .strategyCount(u.getStrategies() != null ? u.getStrategies().size() : 0)
                    .build();
        }
    }
