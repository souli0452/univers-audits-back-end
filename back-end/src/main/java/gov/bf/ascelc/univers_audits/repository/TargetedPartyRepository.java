package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.AllegedRole;
import gov.bf.ascelc.univers_audits.enums.PartyType;
import gov.bf.ascelc.univers_audits.model.entity.TargetedParty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TargetedPartyRepository
        extends JpaRepository<TargetedParty, UUID> {

    List<TargetedParty> findByDossierId(UUID dossierId);

    List<TargetedParty> findByDossierIdAndPartyType(
            UUID dossierId, PartyType partyType);

    List<TargetedParty> findByDossierIdAndAllegedRole(
            UUID dossierId, AllegedRole allegedRole);

    long countByDossierId(UUID dossierId);

    @Query("""
            SELECT tp FROM TargetedParty tp
            WHERE tp.dossier.id = :dossierId
            AND LOWER(tp.name) = LOWER(:name)
            AND LOWER(tp.institution) = LOWER(:institution)
            """)
    List<TargetedParty> findByDossierIdAndNameAndInstitution(
            @Param("dossierId") UUID dossierId,
            @Param("name") String name,
            @Param("institution") String institution);

    @Query("""
            SELECT tp.partyType, COUNT(tp)
            FROM TargetedParty tp
            JOIN tp.dossier d
            WHERE d.receptionDate BETWEEN :start AND :end
            GROUP BY tp.partyType
            """)
    List<Object[]> countByPartyTypeBetween(
            @Param("start") java.time.Instant start,
            @Param("end") java.time.Instant end);

    @Query("""
            SELECT tp.allegedRole, COUNT(tp)
            FROM TargetedParty tp
            JOIN tp.dossier d
            WHERE d.receptionDate BETWEEN :start AND :end
            GROUP BY tp.allegedRole
            """)
    List<Object[]> countByAllegedRoleBetween(
            @Param("start") java.time.Instant start,
            @Param("end") java.time.Instant end);
}