package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.AgentRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface AgentRoleRepository
        extends JpaRepository<AgentRole, UUID> {

    List<AgentRole> findByAgentIdAndActifTrue(UUID agentId);

    List<AgentRole> findByRoleFonctionnelIdAndActifTrue(
            UUID roleFonctionnelId);

    boolean existsByAgentIdAndRoleFonctionnelIdAndActifTrue(
            UUID agentId, UUID roleFonctionnelId);

    @Query("""
            SELECT COUNT(ar) > 0 FROM AgentRole ar
            JOIN ar.roleFonctionnel rf
            WHERE ar.agent.id = :agentId
            AND rf.code = :roleCode
            AND ar.actif = true
            """)
    boolean agentHasRole(
            @Param("agentId") UUID agentId,
            @Param("roleCode") String roleCode);

    @Query("""
            SELECT ar FROM AgentRole ar
            JOIN ar.roleFonctionnel rf
            WHERE ar.agent.id = :agentId
            AND rf.code = :roleCode
            AND ar.actif = true
            """)
    Optional<AgentRole> findActiveRoleByAgentAndCode(
            @Param("agentId") UUID agentId,
            @Param("roleCode") String roleCode);
}
