package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.PointChecklistDossierTravail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PointChecklistDossierTravailRepository
        extends JpaRepository<PointChecklistDossierTravail, UUID> {
    List<PointChecklistDossierTravail> findByActifTrueOrderByOrdreAsc();
    List<PointChecklistDossierTravail> findAllByOrderByOrdreAsc();
    Optional<PointChecklistDossierTravail> findByCode(String code);
    boolean existsByCode(String code);
}
