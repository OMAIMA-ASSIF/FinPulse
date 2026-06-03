package ma.enset.backend.repository;

import ma.enset.backend.entity.DocumentsEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentEmbeddingRepository extends JpaRepository<DocumentsEmbedding, Long> {
    List<DocumentsEmbedding> findByCompanyIdOrderByChunkOrderAsc(Integer companyId);

    List<DocumentsEmbedding> findByCompanyIdAndFiscalYear(Integer companyId, String fiscalYear);

    long countByCompanyId(Integer companyId);

    void deleteByCompanyId(Integer companyId);

    @Query("SELECT d FROM DocumentsEmbedding d WHERE d.company.id = :companyId AND d.section = :section ORDER BY d.chunkOrder ASC")
    List<DocumentsEmbedding> findByCompanyIdAndSection(@Param("companyId") Integer companyId,
                                                      @Param("section") String section);
}
