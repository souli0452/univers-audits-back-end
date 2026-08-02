package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import gov.bf.ascelc.univers_audits.model.dto.response.DeclarantResponse;
import gov.bf.ascelc.univers_audits.model.entity.Declarant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeclarantMapperTest {

    private final DeclarantMapper mapper = new DeclarantMapperImpl();

    @Test
    void toResponse_masksCellulaireAndLocaliteForAnonymousDeclarant() {
        Declarant declarant = Declarant.builder()
                .typeDeclarant(TypeDeclarant.ANONYMOUS)
                .cellulaire("70000000")
                .localite("Tampouy")
                .build();

        DeclarantResponse response = mapper.toResponse(declarant);

        assertThat(response.getCellulaire()).isNull();
        assertThat(response.getLocalite()).isNull();
    }

    @Test
    void toResponse_keepsCellulaireAndLocaliteForNamedDeclarant() {
        Declarant declarant = Declarant.builder()
                .typeDeclarant(TypeDeclarant.CITIZEN)
                .firstName("Awa")
                .lastName("Ouedraogo")
                .cellulaire("70000000")
                .localite("Tampouy")
                .build();

        DeclarantResponse response = mapper.toResponse(declarant);

        assertThat(response.getCellulaire()).isEqualTo("70000000");
        assertThat(response.getLocalite()).isEqualTo("Tampouy");
    }
}
