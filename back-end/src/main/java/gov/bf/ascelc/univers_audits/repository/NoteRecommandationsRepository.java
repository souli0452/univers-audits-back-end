package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.NoteRecommandations;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NoteRecommandationsRepository extends JpaRepository<NoteRecommandations, UUID> {
    Optional<NoteRecommandations> findByRapportEnqueteId(UUID rapportEnqueteId);
}
