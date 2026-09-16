package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.LeconAPartager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LeconAPartagerRepository extends JpaRepository<LeconAPartager, UUID> {

    Page<LeconAPartager> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByFicheRetexId(UUID ficheRetexId);
}
