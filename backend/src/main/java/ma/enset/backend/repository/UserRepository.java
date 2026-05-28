package ma.enset.backend.repository;

import ma.enset.backend.entity.User;
import ma.enset.backend.enums.ProfileType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {
    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByKeycloakId(String keycloakId);


    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByKeycloakId(String keycloakId);


    List<User> findByProfileType(ProfileType profileType);

}
