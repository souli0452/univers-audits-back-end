package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.InformationPreoccupante;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InformationPreoccupanteRepository
        extends JpaRepository<InformationPreoccupante, UUID> {

    Page<InformationPreoccupante> findAllByOrderByDateReceptionDesc(Pageable pageable);
}
