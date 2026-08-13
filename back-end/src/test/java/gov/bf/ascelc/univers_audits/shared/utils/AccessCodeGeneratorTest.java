package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.enums.AttachmentSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessCodeGeneratorTest {

    private final AccessCodeGenerator generator = new AccessCodeGenerator();

    @Test
    void generateAttachmentCode_formatteAvecLaLettreDeLaSource() {
        String code = generator.generateAttachmentCode(AttachmentSource.FIELD_INVESTIGATION, 7);

        assertThat(code).isEqualTo("ACC-T-00007");
    }

    @Test
    void generateAttachmentCode_leveUneExceptionSiLaSourceEstNulle() {
        // La map ATTACHMENT_SOURCE_LETTERS (Map.of) rejette déjà les clés nulles ;
        // le garde-fou explicite de generateAttachmentCode couvre le cas d'une
        // valeur d'enum non nulle mais absente de la map (actuellement aucune,
        // les 6 valeurs d'AttachmentSource y sont toutes mappées).
        assertThatThrownBy(() -> generator.generateAttachmentCode(null, 1))
                .isInstanceOf(NullPointerException.class);
    }
}
