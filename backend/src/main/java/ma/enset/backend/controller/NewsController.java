package ma.enset.backend.controller;

import ma.enset.backend.dto.NewsDTO;
import ma.enset.backend.service.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    /** GET /api/news/{companyId}?page=0&size=10 */
    @GetMapping("/{companyId}")
    public ResponseEntity<Page<NewsDTO>> getNews(@PathVariable Long companyId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(newsService.getNewsByCompany(companyId, page, size));
    }

    /** GET /api/news/{companyId}/latest */
    @GetMapping("/{companyId}/latest")
    public ResponseEntity<List<NewsDTO>> getLatestNews(@PathVariable Long companyId) {
        return ResponseEntity.ok(newsService.getLatestNews(companyId));
    }

    /** GET /api/news/{companyId}/sentiment */
    @GetMapping("/{companyId}/sentiment")
    public ResponseEntity<Map<String, Double>> getSentiment(@PathVariable Long companyId) {
        return ResponseEntity.ok(Map.of("sentimentAvg", newsService.getAverageSentiment(companyId)));
    }
}
