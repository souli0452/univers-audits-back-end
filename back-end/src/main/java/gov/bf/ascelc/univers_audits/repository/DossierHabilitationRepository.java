package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.HabilitationSource;
import gov.bf.ascelc.univers_audits.model.entity.DossierHabilitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DossierHabilitationRepository extends JpaRepository<DossierHabilitation, UUID> {

    boolean existsByDossierIdAndAgentIdAndRevokedAtIsNull(UUID dossierId, UUID agentId);

    boolean existsByDossierIdAndAgentIdAndSourceAndRevokedAtIsNull(
            UUID dossierId, UUID agentId, HabilitationSource source);

    List<DossierHabilitation> findByDossierIdAndAgentIdAndRevokedAtIsNull(
            UUID dossierId, UUID agentId);

    Optional<DossierHabilitation> findFirstByDossierIdAndAgentIdAndSourceAndRevokedAtIsNullOrderByCreatedAtDesc(
            UUID dossierId, UUID agentId, HabilitationSource source);

    List<DossierHabilitation> findByDossierIdAndRevokedAtIsNull(UUID dossierId);
}
