package ma.enset.backend.service;

import ma.enset.backend.entity.Company;
import ma.enset.backend.entity.DocumentsEmbedding;
import ma.enset.backend.exception.ResourceNotFoundException;
import ma.enset.backend.repository.CompanyRepository;
import ma.enset.backend.repository.DocumentEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class EmbeddingService {

    private final DocumentEmbeddingRepository embeddingRepository;
    private final CompanyRepository companyRepository;
    private static final int EMBEDDING_DIM = 1536;
    private final Random random = new Random();

    public List<DocumentsEmbedding> getEmbeddingsByCompany(Integer companyId) {
        return embeddingRepository.findByCompanyIdOrderByChunkOrderAsc(companyId);
    }

    public long countEmbeddings(Integer companyId) {
        return embeddingRepository.countByCompanyId(companyId);
    }

    /**
     * Store a real embedding (called by P1 module via REST or message queue).
     */
    @Transactional
    public DocumentsEmbedding storeEmbedding(Long companyId, String content,
                                            float[] embedding, String section,
                                            Integer fiscalYear, int chunkOrder) {
        if (embedding.length != EMBEDDING_DIM) {
            throw new IllegalArgumentException(
                    "Embedding dimension must be " + EMBEDDING_DIM + ", got " + embedding.length);
        }
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        DocumentsEmbedding doc = DocumentsEmbedding.builder()
                .company(company)
                .content(content)
                .embedding(embedding)
                .section(section)
                .fiscalYear(fiscalYear)
                .chunkOrder(chunkOrder)
                .build();

        return embeddingRepository.save(doc);
    }

    /**
     * Simulate an embedding (fake data — used until P1 is integrated).
     */
    @Transactional
    public DocumentsEmbedding simulateEmbedding(Long companyId, String content,
                                               String section, Integer fiscalYear,
                                               int chunkOrder) {
        float[] fakeEmbedding = generateRandomEmbedding();
        return storeEmbedding(companyId, content, fakeEmbedding, section, fiscalYear, chunkOrder);
    }

    @Transactional
    public void deleteEmbeddingsByCompany(Integer companyId) {
        embeddingRepository.deleteByCompanyId(companyId);
        log.info("Deleted embeddings for company {}", companyId);
    }

    // Generate a normalized random float vector of dimension 1536
    private float[] generateRandomEmbedding() {
        float[] vec = new float[EMBEDDING_DIM];
        double norm = 0.0;
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            vec[i] = (float) (random.nextGaussian());
            norm += vec[i] * vec[i];
        }
        norm = Math.sqrt(norm);
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            vec[i] /= (float) norm;
        }
        return vec;
    }
}
