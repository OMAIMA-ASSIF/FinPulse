package ma.enset.backend.service;

import ma.enset.backend.dto.NewsDTO;
import ma.enset.backend.entity.Company;
import ma.enset.backend.entity.News;
import ma.enset.backend.exception.ResourceNotFoundException;
import ma.enset.backend.repository.CompanyRepository;
import ma.enset.backend.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.print.Pageable;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NewsService {

    private final NewsRepository newsCacheRepository;
    private final CompanyRepository companyRepository;

    public Page<NewsDTO> getNewsByCompany(Long companyId, int page, int size) {
        if (!companyRepository.existsById(companyId)) {
            throw new ResourceNotFoundException("Company", companyId);
        }
        return newsCacheRepository
                .findByCompanyIdOrderByPublishedAtDesc(companyId, PageRequest.of(page, size))
                .map(NewsDTO::from);
    }

    public List<NewsDTO> getLatestNews(Long companyId) {
        return newsCacheRepository.findTop10ByCompanyIdOrderByPublishedAtDesc(companyId)
                .stream().map(NewsDTO::from).toList();
    }

    public Double getAverageSentiment(Long companyId) {
        Double avg = newsCacheRepository.calculateAverageSentiment(companyId);
        return avg != null ? Math.round(avg * 1000.0) / 1000.0 : 0.0;
    }

    @Transactional
    public NewsDTO saveNews(Long companyId, String title, String url,
                            String source, double sentimentScore) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        if (url != null && newsCacheRepository.existsByUrl(url)) {
            log.debug("News already cached: {}", url);
            return null;
        }

        News news = News.builder()
                .company(company)
                .title(title)
                .url(url)
                .source(source)
                .sentimentScore((float) Math.max(0.0, Math.min(1.0, sentimentScore)))
                .publishedAt(LocalDateTime.now())
                .build();

        news = newsCacheRepository.save(news);
        log.debug("News saved: [{}] {}", company.getTicker(), title);
        return NewsDTO.from(news);
    }

    @Transactional
    public void updateCompanySentimentAverage(Long companyId) {
        Double avg = newsCacheRepository.calculateAverageSentiment(companyId);
        if (avg != null) {
            companyRepository.findById(companyId).ifPresent(c -> {
                c.setSentimentAvg((float) (Math.round(avg * 1000.0) / 1000.0));
                companyRepository.save(c);
            });
        }
    }
}
