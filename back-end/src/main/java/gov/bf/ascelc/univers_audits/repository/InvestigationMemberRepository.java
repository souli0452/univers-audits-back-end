package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.TeamRole;
import gov.bf.ascelc.univers_audits.model.entity.InvestigationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvestigationMemberRepository
        extends JpaRepository<InvestigationMember, UUID> {

    List<InvestigationMember> findByInvestigationIdAndActiveTrue(
            UUID investigationId);

    Optional<InvestigationMember> findByInvestigationIdAndTeamRoleAndActiveTrue(
            UUID investigationId, TeamRole teamRole);

    List<InvestigationMember> findByAgentIdAndActiveTrue(
            UUID agentId);

    boolean existsByInvestigationIdAndAgentIdAndActiveTrue(
            UUID investigationId, UUID agentId);

    long countByInvestigationIdAndActiveTrue(UUID investigationId);

    Optional<InvestigationMember> findFirstByInvestigationIdAndAgentIdOrderByCreatedAtDesc(
            UUID investigationId, UUID agentId);

    @Query("""
            SELECT im FROM InvestigationMember im
            JOIN im.investigation i
            WHERE im.agent.id = :agentId
            AND im.active = true
            AND i.status = 'IN_PROGRESS'
            """)
    List<InvestigationMember> findActiveInvestigationsByAgent(
            @Param("agentId") UUID agentId);
}