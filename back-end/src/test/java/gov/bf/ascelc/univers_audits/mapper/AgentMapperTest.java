package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.model.dto.response.AgentSummaryResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Departement;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMapperTest {

    private final AgentMapper mapper = new AgentMapperImpl();

    @Test
    void l_annuaire_indique_le_departement_de_l_agent_par_son_identifiant_et_son_libelle() {
        UUID departementId = UUID.randomUUID();
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Awa").lastName("Kaboré")
                .departement(Departement.builder().id(departementId).code("DEI").libelle("Enquêtes").build())
                .build();

        AgentSummaryResponse resume = mapper.toSummaryResponse(agent);

        assertThat(resume.getDepartementId()).isEqualTo(departementId);
        assertThat(resume.getDepartementLabel()).isEqualTo("Enquêtes");
    }

    @Test
    void un_agent_sans_departement_n_a_ni_identifiant_ni_libelle() {
        AgentSummaryResponse resume = mapper.toSummaryResponse(
                Agent.builder().id(UUID.randomUUID()).firstName("Issa").build());

        assertThat(resume.getDepartementId()).isNull();
        assertThat(resume.getDepartementLabel()).isNull();
    }
}
