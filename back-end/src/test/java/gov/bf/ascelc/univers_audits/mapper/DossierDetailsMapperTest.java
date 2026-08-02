package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.model.dto.request.EtudeOpportuniteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.WitnessResponse;
import gov.bf.ascelc.univers_audits.model.entity.EtudeOpportunite;
import gov.bf.ascelc.univers_audits.model.entity.Witness;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DossierDetailsMapperTest {

    private final DossierDetailsMapper mapper = new DossierDetailsMapperImpl();

    @Test
    void toResponse_masksIdentityForAnonymousWitness() {
        Witness witness = Witness.builder()
                .firstName("Jean")
                .lastName("Kabore")
                .phoneNumber("70000000")
                .email("jean@example.com")
                .address("Secteur 15")
                .profession("Enseignant")
                .anonymous(true)
                .build();

        WitnessResponse response = mapper.toResponse(witness);

        assertThat(response.getFirstName()).isNull();
        assertThat(response.getLastName()).isNull();
        assertThat(response.getPhoneNumber()).isNull();
        assertThat(response.getEmail()).isNull();
        assertThat(response.getAddress()).isNull();
        assertThat(response.getProfession()).isNull();
        assertThat(response.getDisplayName()).isEqualTo("Témoin anonyme");
    }

    @Test
    void toResponse_keepsIdentityForNamedWitness() {
        Witness witness = Witness.builder()
                .firstName("Jean")
                .lastName("Kabore")
                .phoneNumber("70000000")
                .anonymous(false)
                .build();

        WitnessResponse response = mapper.toResponse(witness);

        assertThat(response.getFirstName()).isEqualTo("Jean");
        assertThat(response.getLastName()).isEqualTo("Kabore");
        assertThat(response.getPhoneNumber()).isEqualTo("70000000");
        assertThat(response.getDisplayName()).isEqualTo("Jean Kabore");
    }

    @Test
    void updateEntity_ignoresNullFieldsAndPreservesExistingValues() {
        EtudeOpportunite existing = EtudeOpportunite.builder()
                .preoccupationReelle(true)
                .avisGeneral("Avis initial du conseiller")
                .build();

        EtudeOpportuniteRequest request = EtudeOpportuniteRequest.builder()
                .preuvesSuffisantes(false)
                .build();

        mapper.updateEntity(request, existing);

        assertThat(existing.getPreoccupationReelle()).isTrue();
        assertThat(existing.getAvisGeneral()).isEqualTo("Avis initial du conseiller");
        assertThat(existing.getPreuvesSuffisantes()).isFalse();
    }
}
