package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.MesureConservatoire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MesureConservatoireRepository extends JpaRepository<MesureConservatoire, UUID> {

    List<MesureConservatoire> findByInvestigationIdOrderByTakenAtDesc(UUID investigationId);
}
