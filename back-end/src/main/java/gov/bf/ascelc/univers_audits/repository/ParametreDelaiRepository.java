package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.ParametreDelai;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParametreDelaiRepository
        extends JpaRepository<ParametreDelai, UUID> {

    Optional<ParametreDelai> findByCode(String code);

    List<ParametreDelai> findByActifTrueOrderByCodeAsc();
}
