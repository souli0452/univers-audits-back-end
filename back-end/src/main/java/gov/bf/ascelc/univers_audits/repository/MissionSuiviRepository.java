package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.MissionSuivi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MissionSuiviRepository extends JpaRepository<MissionSuivi, UUID> {
    List<MissionSuivi> findByInvestigationIdOrderByMissionDateDesc(UUID investigationId);
}
