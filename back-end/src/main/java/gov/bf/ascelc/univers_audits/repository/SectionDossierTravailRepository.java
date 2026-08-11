package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.TypeSectionDossierTravail;
import gov.bf.ascelc.univers_audits.model.entity.SectionDossierTravail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SectionDossierTravailRepository
        extends JpaRepository<SectionDossierTravail, UUID> {

    List<SectionDossierTravail> findByDossierId(UUID dossierId);

    boolean existsByDossierIdAndType(UUID dossierId, TypeSectionDossierTravail type);

    boolean existsByDossierIdAndTypeAndLibelle(
            UUID dossierId, TypeSectionDossierTravail type, String libelle);
}
