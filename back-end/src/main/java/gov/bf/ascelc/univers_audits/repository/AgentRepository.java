package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.Agent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentRepository
        extends JpaRepository<Agent, UUID> {

    Optional<Agent> findByKeycloakId(String keycloakId);

    Optional<Agent> findByEmail(String email);

    Optional<Agent> findBymatricule(String matricule);

    boolean existsByKeycloakId(String keycloakId);
    boolean existsByEmail(String email);
    boolean existsByMatricule(String matricule);

    @Query("""
            SELECT a FROM Agent a
            WHERE a.departement.id = :departementId
            AND a.actif = true
            ORDER BY a.lastName ASC
            """)
    List<Agent> findActiveByDepartement(
            @Param("departementId") UUID departementId);

    @Query("""
            SELECT DISTINCT a FROM Agent a
            JOIN a.roles ar
            JOIN ar.roleFonctionnel rf
            WHERE rf.code = :roleCode
            AND ar.actif = true
            AND a.actif = true
            ORDER BY a.lastName ASC
            """)
    List<Agent> findActiveByRoleCode(
            @Param("roleCode") String roleCode);


    Page<Agent> findByActifTrue(Pageable pageable);
}