package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.Witness;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WitnessRepository
        extends JpaRepository<Witness, UUID> {

    List<Witness> findByDossierId(UUID dossierId);

    List<Witness> findByDossierIdAndConsentToContactTrue(
            UUID dossierId);

    List<Witness> findByDossierIdAndAnonymousFalse(
            UUID dossierId);

    long countByDossierId(UUID dossierId);

    @Query("""
            SELECT w FROM Witness w
            WHERE w.dossier.id = :dossierId
            AND w.phoneNumber = :phoneNumber
            """)
    List<Witness> findByDossierIdAndPhoneNumber(
            @Param("dossierId") UUID dossierId,
            @Param("phoneNumber") String phoneNumber);
}