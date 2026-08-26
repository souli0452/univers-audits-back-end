package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.InformationPreoccupanteDossier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InformationPreoccupanteDossierRepository
        extends JpaRepository<InformationPreoccupanteDossier, UUID> {

    List<InformationPreoccupanteDossier> findByInformationPreoccupanteId(
            UUID informationPreoccupanteId);

    boolean existsByInformationPreoccupanteIdAndDossierId(
            UUID informationPreoccupanteId, UUID dossierId);
}
