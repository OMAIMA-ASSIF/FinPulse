package ma.enset.backend.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import ma.enset.backend.enums.ProfileType;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public class UserRequestDTO {
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50)
        private String username;

        @NotBlank @Email
        private String email;

        @NotBlank
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String password;

        private ProfileType profileType = ProfileType.PRUDENT;
    }
