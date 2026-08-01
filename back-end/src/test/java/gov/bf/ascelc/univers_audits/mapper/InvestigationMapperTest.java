package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.enums.InvestigationStatus;
import gov.bf.ascelc.univers_audits.enums.TeamRole;
import gov.bf.ascelc.univers_audits.model.dto.response.InvestigationResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.InvestigationMember;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InvestigationMapperTest {

    // InvestigationMapperImpl declare AgentMapper (uses = AgentMapper.class) en
    // injection de champ Spring (@Autowired) sans constructeur dedie — on la
    // fournit nous-memes par reflexion pour instancier le mapper hors contexte
    // Spring, comme pour les autres mappers de ce depot.
    private final InvestigationMapper mapper = createMapper();

    private static InvestigationMapper createMapper() {
        InvestigationMapperImpl impl = new InvestigationMapperImpl();
        ReflectionTestUtils.setField(impl, "agentMapper", new AgentMapperImpl());
        return impl;
    }

    @Test
    void toResponse_computesOverdueAndActiveMembersOnly() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        Investigation investigation = Investigation.builder()
                .id(UUID.randomUUID())
                .dossier(dossier)
                .status(InvestigationStatus.IN_PROGRESS)
                .plannedEndDate(Instant.now().minus(2, ChronoUnit.DAYS))
                .build();

        InvestigationMember activeMember = InvestigationMember.builder()
                .investigation(investigation)
                .agent(agent)
                .teamRole(TeamRole.MEMBER)
                .active(true)
                .build();
        InvestigationMember inactiveMember = InvestigationMember.builder()
                .investigation(investigation)
                .agent(agent)
                .teamRole(TeamRole.MEMBER)
                .active(false)
                .build();
        List<InvestigationMember> members = new ArrayList<>();
        members.add(activeMember);
        members.add(inactiveMember);
        investigation.setMembers(members);

        InvestigationResponse response = mapper.toResponse(investigation);

        assertThat(response.getOverdue()).isTrue();
        assertThat(response.getMemberCount()).isEqualTo(1);
        assertThat(response.getMembers()).hasSize(1);
    }
}
