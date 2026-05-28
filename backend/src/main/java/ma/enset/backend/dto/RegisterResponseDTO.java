package ma.enset.backend.dto;


public class RegisterResponseDTO {

    public record RegisterResponse(
            Long id,
            String username,
            String email,
            String profileType,
            String message
    ) {}

}
