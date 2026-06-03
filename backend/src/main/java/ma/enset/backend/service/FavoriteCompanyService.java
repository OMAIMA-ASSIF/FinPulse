package ma.enset.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ma.enset.backend.entity.FavoriteCompany;
import ma.enset.backend.entity.User;
import ma.enset.backend.repository.FavoriteCompanyRepository;
import ma.enset.backend.dto.FavoriteCompanyDTO;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteCompanyService {

    private final FavoriteCompanyRepository favoriteRepository;
    private final CurrentUserService currentUserService;

    @CacheEvict(value = "user-favorites", key = "#userId")
    public FavoriteCompanyDTO addFavorite(Long userId, String ticker, String companyName) {
        User user = currentUserService.getUserById(userId);

        // Vérifier si déjà en favoris
        if (favoriteRepository.existsByUserAndTicker(user, ticker)) {
            log.warn("Company {} already in favorites for user {}", ticker, userId);
            throw new RuntimeException("Déjà en favoris");
        }

        FavoriteCompany favorite = FavoriteCompany.builder()
                .user(user)
                .ticker(ticker.toUpperCase())
                .companyName(companyName)
                .build();

        FavoriteCompany saved = favoriteRepository.save(favorite);
        log.info("Added favorite company {} for user {}", ticker, userId);

        return toDTO(saved);
    }

    @Cacheable(value = "user-favorites", key = "#userId")
    public List<FavoriteCompanyDTO> getFavorites(Long userId) {
        User user = currentUserService.getUserById(userId);
        List<FavoriteCompany> favorites = favoriteRepository.findByUser(user);
        return favorites.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @CacheEvict(value = "user-favorites", key = "#userId")
    public void removeFavorite(Long userId, Long favoriteId) {
        FavoriteCompany favorite = favoriteRepository.findById(favoriteId)
                .orElseThrow(() -> new RuntimeException("Favori non trouvé"));

        if (!favorite.getUser().getId().equals(userId)) {
            throw new RuntimeException("Accès non autorisé");
        }

        favoriteRepository.delete(favorite);
        log.info("Removed favorite company {} for user {}", favorite.getTicker(), userId);
    }

    @CacheEvict(value = "user-favorites", key = "#userId")
    public void removeFavorite(Long userId, String ticker) {
        User user = currentUserService.getUserById(userId);
        FavoriteCompany favorite = favoriteRepository.findByUserAndTicker(user, ticker.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Favori non trouvé pour " + ticker));
        favoriteRepository.delete(favorite);
        log.info("Removed favorite company {} for user {}", ticker, userId);
    }

    public boolean isFavorite(Long userId, String ticker) {
        User user = currentUserService.getUserById(userId);
        return favoriteRepository.existsByUserAndTicker(user, ticker.toUpperCase());
    }

    private FavoriteCompanyDTO toDTO(FavoriteCompany favorite) {
        return FavoriteCompanyDTO.builder()
                .id(favorite.getId())
                .ticker(favorite.getTicker())
                .companyName(favorite.getCompanyName())
                .addedAt(favorite.getAddedAt())
                .build();
    }
}