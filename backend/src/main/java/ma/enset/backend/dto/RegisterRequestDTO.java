package ma.enset.backend.dto;
import jakarta.validation.constraints.*;


public class RegisterRequestDTO {

    public record RegisterRequest(
            @NotBlank(message = "Username obligatoire")
            @Size(min = 3, max = 50, message = "Username: 3-50 caractères")
            String username,

            @NotBlank(message = "Email obligatoire")
            @Email(message = "Email invalide")
            String email,

            @NotBlank(message = "Mot de passe obligatoire")
            @Size(min = 8, message = "Mot de passe: minimum 8 caractères")
            String password,

            @NotBlank(message = "profileType obligatoire")
            @Pattern(regexp = "PRUDENT|SPECULATEUR",
                    message = "profileType doit être PRUDENT ou SPECULATEUR")
            String profileType
    ) {}

}
