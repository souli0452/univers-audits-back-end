package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.Observation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ObservationRepository
        extends JpaRepository<Observation, UUID> {


    List<Observation> findByDossierIdOrderByCreatedAtAsc(
            UUID dossierId);


    List<Observation> findByDossierIdAndConfidentialFalse(
            UUID dossierId);
}