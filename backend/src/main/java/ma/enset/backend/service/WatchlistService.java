package ma.enset.backend.service;

import ma.enset.backend.dto.WatchlistDTO;
import ma.enset.backend.entity.Company;
import ma.enset.backend.entity.User;
import ma.enset.backend.entity.WatchlistEntry;
import ma.enset.backend.exception.ResourceNotFoundException;
import ma.enset.backend.repository.CompanyRepository;
import ma.enset.backend.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final CompanyService        companyService;
    private final UserService         userService;

    // ── READ ──────────────────────────────────────────────────────────────────

    public List<WatchlistDTO> getWatchlist(Long userId) {
        return watchlistRepository
                .findByUserIdOrderByPinnedAtDesc(userId)
                .stream()
                .map(WatchlistDTO::from)
                .toList();
    }

    public List<Long> getPinnedCompanyIds(Long userId) {
        return watchlistRepository.findCompanyIdsByUserId(userId);
    }

    public boolean isPinned(Long userId, Long companyId) {
        return watchlistRepository.existsByUserIdAndCompanyId(userId, companyId);
    }

    // ── PIN ───────────────────────────────────────────────────────────────────

    /**
     * Épingle une entreprise dans la watchlist.
     * Idempotent : si déjà épinglée, retourne l'entrée existante sans erreur.
     */
    @Transactional
    public WatchlistDTO pin(Long userId, Long companyId) {
        // Retourner l'existant si déjà épinglé
        return watchlistRepository
                .findByUserIdAndCompanyId(userId, companyId)
                .map(WatchlistDTO::from)
                .orElseGet(() -> {
                    User    user    = userService.findEntityById(userId);
                    Company company = companyService.findEntityByP1Id(companyId.longValue());

                    WatchlistEntry entry = WatchlistEntry.builder()
                            .user(user)
                            .company(company)
                            .build();

                    WatchlistEntry saved = watchlistRepository.save(entry);
                    log.info("Pinned company {} for user {}", companyId, userId);
                    return WatchlistDTO.from(saved);
                });
    }

    // ── UNPIN ─────────────────────────────────────────────────────────────────

    /**
     * Dépingle uniquement de la watchlist.
     * NE TOUCHE PAS les stratégies — indépendance totale.
     */
    @Transactional
    public void unpin(Long userId, Long companyId) {
        watchlistRepository.deleteByUserIdAndCompanyId(userId, companyId);
        log.info("Unpinned company {} for user {}", companyId, userId);
    }

    // ── STATUS ────────────────────────────────────────────────────────────────

    public Map<String, Object> getStatus(Long userId, Long companyId) {
        return Map.of(
                "pinned",    watchlistRepository.existsByUserIdAndCompanyId(userId, companyId),
                "companyId", companyId
        );
    }
}
