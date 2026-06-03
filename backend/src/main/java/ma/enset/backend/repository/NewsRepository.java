package ma.enset.backend.repository;

import ma.enset.backend.entity.News;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NewsRepository extends JpaRepository<News, Long> {
    Page<News> findByCompanyIdOrderByPublishedAtDesc(Long companyId, Pageable pageable);

    List<News> findTop10ByCompanyIdOrderByPublishedAtDesc(Long companyId);

    @Query("SELECT AVG(n.sentimentScore) FROM News n WHERE n.company.id = :companyId")
    Double calculateAverageSentiment(@Param("companyId") Long companyId);

    @Query("SELECT AVG(n.sentimentScore) FROM News n WHERE n.company.id = :companyId AND n.publishedAt >= :since")
    Double calculateRecentAverageSentiment(@Param("companyId") Integer companyId,
                                           @Param("since") LocalDateTime since);

    long countByCompanyId(Integer companyId);

    boolean existsByUrl(String url);

    @Query("SELECT n FROM News n WHERE n.company.id = :companyId AND n.sentimentScore < :threshold ORDER BY n.publishedAt DESC")
    List<News> findNegativeNews(@Param("companyId") Integer companyId,
                                     @Param("threshold") double threshold);
}


