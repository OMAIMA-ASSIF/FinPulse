package ma.enset.backend.service;

import ma.enset.backend.dto.UserRequestDTO;
import ma.enset.backend.dto.UserDTO;
import ma.enset.backend.entity.User;
import ma.enset.backend.enums.ProfileType;
import ma.enset.backend.exception.DuplicateResourceException;
import ma.enset.backend.exception.ResourceNotFoundException;
import ma.enset.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ── READ ──────────────────────────────────────────────────────────────────

    public UserDTO getById(Long id) {
        return userRepository.findById(id)
                .map(UserDTO::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    public UserDTO getByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .map(UserDTO::from)
                .orElseThrow(() -> new ResourceNotFoundException("User with username: " + username));
    }

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserDTO::from)
                .toList();
    }

    public List<UserDTO> getUsersByProfile(ProfileType profileType) {
        return userRepository.findByProfileType(profileType).stream()
                .map(UserDTO::from)
                .toList();
    }


    // ── WRITE ─────────────────────────────────────────────────────────────────

    @Transactional
    public UserDTO createUser(UserRequestDTO request) {
        if (userRepository.existsByUsernameIgnoreCase(request.getUsername())) {
            throw new DuplicateResourceException("User", "username", request.getUsername());
        }
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail().toLowerCase())
                .profileType(request.getProfileType() != null
                        ? request.getProfileType()
                        : ProfileType.PRUDENT)
                .build();

        user = userRepository.save(user);
        log.info("User created: {} [{}]", user.getUsername(), user.getProfileType());
        return UserDTO.from(user);
    }


    @Transactional
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        userRepository.deleteById(userId);
        log.info("User deleted: {}", userId);
    }

    // Internal: resolve entity (used by other services)
    public User findEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
