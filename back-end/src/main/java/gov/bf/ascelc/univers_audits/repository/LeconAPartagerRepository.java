package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.LeconAPartager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface LeconAPartagerRepository extends JpaRepository<LeconAPartager, UUID> {

    Page<LeconAPartager> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Leçons dont le dossier n'est pas confidentiel : seules visibles aux agents sans accès aux dossiers confidentiels. */
    @Query("""
            SELECT l FROM LeconAPartager l
            WHERE l.ficheRetex.investigation.dossier.isConfidential = false
            ORDER BY l.createdAt DESC
            """)
    Page<LeconAPartager> findNonConfidentialesOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByFicheRetexId(UUID ficheRetexId);
}
