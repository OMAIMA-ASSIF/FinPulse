package ma.enset.backend.controller;

import jakarta.validation.Valid;
import ma.enset.backend.dto.RegisterRequestDTO;
import ma.enset.backend.dto.RegisterResponseDTO;
import ma.enset.backend.dto.UserProfileDTO;
import ma.enset.backend.entity.User;
import ma.enset.backend.enums.ProfileType;
import ma.enset.backend.exception.ApiException;
import ma.enset.backend.repository.UserRepository;
import ma.enset.backend.service.CurrentUserService;
import ma.enset.backend.service.KeycloakAdminService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final CurrentUserService   currentUserService;
    private final UserRepository       userRepository;
    private final KeycloakAdminService keycloakAdminService;

    @Value("${finpulse.upload.avatars-dir:uploads/avatars}")
    private String avatarsDir;

    @Value("${finpulse.upload.base-url:http://localhost:8081}")
    private String baseUrl;

    // ── POST /api/auth/register ───────────────────────────────────────────

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequestDTO.RegisterRequest request) {
        log.info("Registration attempt: username={}", request.username());
        try {
            String keycloakId = keycloakAdminService.createUser(
                    request.username(), request.email(),
                    request.password(), request.profileType()
            );

            User user = User.builder()
                    .keycloakId(keycloakId)
                    .username(request.username())
                    .email(request.email())
                    .profileType(ProfileType.valueOf(request.profileType()))
                    .firstLogin(true)
                    .build();

            user = userRepository.save(user);
            log.info("Registration success: userId={}", user.getId());

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    new RegisterResponseDTO.RegisterResponse(
                            user.getId(), user.getUsername(), user.getEmail(),
                            user.getProfileType().name(),
                            "Account created. You may now log in."
                    )
            );
        } catch (KeycloakAdminService.KeycloakUserException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getErrorCode(), "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during registration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "An unexpected error occurred"));
        }
    }

    // ── GET /api/auth/me ──────────────────────────────────────────────────

    @GetMapping("/me")
    public ResponseEntity<UserProfileDTO> getMe() {
        return ResponseEntity.ok(toDTO(currentUserService.getCurrentUser()));
    }

    // ── PATCH /api/auth/profile-type ─────────────────────────────────────

    @PatchMapping("/profile-type")
    @Transactional
    public ResponseEntity<?> updateProfileType(@RequestBody Map<String, String> body) {
        String t = body.get("profileType");
        if (t == null || (!t.equals("PRUDENT") && !t.equals("SPECULATEUR")))
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid profileType"));
        User user = currentUserService.getCurrentUser();
        user.setProfileType(ProfileType.valueOf(t));
        userRepository.save(user);
        try { keycloakAdminService.updateProfileType(user.getKeycloakId(), t); }
        catch (Exception e) { log.warn("Keycloak profileType update failed: {}", e.getMessage()); }
        return ResponseEntity.ok(Map.of("profileType", t));
    }

    // ── PATCH /api/auth/info ─────────────────────────────────────────────

    @PatchMapping("/info")
    @Transactional
    public ResponseEntity<?> updateInfo(@RequestBody Map<String, String> body) {

        User user = currentUserService.getCurrentUser();

        String newUsername = body.get("username");
        String newEmail    = body.get("email");

        // ── VALIDATION DB ─────────────────────
        if (newUsername != null && !newUsername.isBlank()) {
            if (userRepository.existsByUsernameIgnoreCase(newUsername)
                    && !newUsername.equalsIgnoreCase(user.getUsername())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "USERNAME_EXISTS"));
            }
        }

        if (newEmail != null && !newEmail.isBlank()) {
            if (userRepository.existsByEmailIgnoreCase(newEmail)
                    && !newEmail.equalsIgnoreCase(user.getEmail())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "EMAIL_EXISTS"));
            }
        }

        // ── 1. UPDATE KEYCLOAK FIRST ──────────
        keycloakAdminService.updateUserInfo(
                user.getKeycloakId(),
                newUsername,
                newEmail
        );

        // ── 2. UPDATE LOCAL DB ────────────────
        if (newUsername != null && !newUsername.isBlank()) {
            user.setUsername(newUsername.trim());
        }

        if (newEmail != null && !newEmail.isBlank()) {
            user.setEmail(newEmail.trim().toLowerCase());
        }

        User saved = userRepository.save(user);

        return ResponseEntity.ok(toDTO(saved));
    }

    // ── PATCH /api/auth/password ─────────────────────────────────────────
    /**
     * Changes the Keycloak password after verifying the current one.
     * NEVER stores passwords in PostgreSQL.
     */
    @PatchMapping("/password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest req) {
        if (req.getCurrentPassword() == null || req.getCurrentPassword().isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Current password is required"));
        if (req.getNewPassword() == null || req.getNewPassword().length() < 8)
            return ResponseEntity.badRequest().body(Map.of("error", "New password must be at least 8 characters"));
        if (!req.getNewPassword().equals(req.getConfirmPassword()))
            return ResponseEntity.badRequest().body(Map.of("error", "Passwords do not match"));
        if (req.getCurrentPassword().equals(req.getNewPassword()))
            return ResponseEntity.badRequest().body(Map.of("error", "New password must differ from current"));

        User user = currentUserService.getCurrentUser();
        try {
            keycloakAdminService.changePassword(
                    user.getKeycloakId(), user.getUsername(),
                    req.getCurrentPassword(), req.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
        } catch (ApiException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Password change failed for userId={}: {}", user.getId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Password change failed. Please try again."));
        }
    }

    // ── POST /api/auth/avatar ─────────────────────────────────────────────

    @PostMapping("/avatar")
    @Transactional
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "No file"));
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/"))
            return ResponseEntity.badRequest().body(Map.of("error", "File must be an image"));
        if (file.getSize() > 5 * 1024 * 1024)
            return ResponseEntity.badRequest().body(Map.of("error", "Max 5MB"));

        User user = currentUserService.getCurrentUser();
        try {
            Path uploadPath = Paths.get(avatarsDir);
            Files.createDirectories(uploadPath);
            if (user.getAvatarUrl() != null) {
                String old = user.getAvatarUrl().substring(user.getAvatarUrl().lastIndexOf('/') + 1);
                Files.deleteIfExists(uploadPath.resolve(old));
            }
            String ext      = StringUtils.getFilenameExtension(file.getOriginalFilename());
            String fileName = "avatar_" + user.getId() + "_" + UUID.randomUUID() + "." + ext;
            Files.copy(file.getInputStream(), uploadPath.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            String avatarUrl = baseUrl + "/avatars/" + fileName;
            user.setAvatarUrl(avatarUrl);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    // ── DELETE /api/auth/avatar ──────────────────────────────────────────

    @DeleteMapping("/avatar")
    @Transactional
    public ResponseEntity<Void> deleteAvatar() {
        User user = currentUserService.getCurrentUser();
        if (user.getAvatarUrl() != null) {
            try {
                String fn = user.getAvatarUrl().substring(user.getAvatarUrl().lastIndexOf('/') + 1);
                Files.deleteIfExists(Paths.get(avatarsDir).resolve(fn));
            } catch (IOException e) { log.warn("Avatar delete failed: {}", e.getMessage()); }
            user.setAvatarUrl(null);
            userRepository.save(user);
        }
        return ResponseEntity.noContent().build();
    }

    // ── GET /avatars/{filename} ──────────────────────────────────────────

    @GetMapping("/avatars/{filename:.+}")
    public ResponseEntity<Resource> serveAvatar(@PathVariable String filename) {
        try {
            Path path    = Paths.get(avatarsDir).resolve(filename).normalize();
            Resource res = new UrlResource(path.toUri());
            if (!res.exists() || !res.isReadable()) return ResponseEntity.notFound().build();
            String ct = Files.probeContentType(path);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(ct != null ? ct : "image/jpeg"))
                    .body(res);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ── DELETE /api/auth/account ─────────────────────────────────────────

    @DeleteMapping("/account")
    @Transactional
    public ResponseEntity<Void> deleteAccount() {
        User user = currentUserService.getCurrentUser();
        if (user.getAvatarUrl() != null) {
            try {
                String fn = user.getAvatarUrl().substring(user.getAvatarUrl().lastIndexOf('/') + 1);
                Files.deleteIfExists(Paths.get(avatarsDir).resolve(fn));
            } catch (IOException e) { log.warn("Avatar cleanup failed: {}", e.getMessage()); }
        }
        try { keycloakAdminService.deleteUser(user.getKeycloakId()); }
        catch (Exception e) { log.error("Keycloak delete failed: {}", e.getMessage()); }
        userRepository.deleteById(user.getId());
        return ResponseEntity.noContent().build();
    }

    // ── PATCH /api/auth/first-login-done ─────────────────────────────────

    @PatchMapping("/first-login-done")
    @Transactional
    public ResponseEntity<Map<String, Boolean>> markFirstLoginDone() {
        User user = currentUserService.getCurrentUser();
        if (user.isFirstLogin()) { user.setFirstLogin(false); userRepository.save(user); }
        return ResponseEntity.ok(Map.of("firstLogin", false));
    }

    // ── Helper ────────────────────────────────────────────────────────────

    @Data
    public static class ChangePasswordRequest {
        private String currentPassword;
        private String newPassword;
        private String confirmPassword;
    }

    private UserProfileDTO toDTO(User user) {
        return UserProfileDTO.builder()
                .id(user.getId())
                .keycloakId(user.getKeycloakId())
                .username(user.getUsername())
                .email(user.getEmail())
                .profileType(user.getProfileType().name())
                .strategyCount(user.getStrategies() != null ? user.getStrategies().size() : 0)
                .createdAt(user.getCreatedAt())
                .avatarUrl(user.getAvatarUrl())
                .firstLogin(user.isFirstLogin())
                .build();
    }
}
