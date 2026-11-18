package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.model.entity.Declarant;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** L'adresse de connexion d'un citoyen ne doit jamais permettre de remonter à un lanceur d'alerte. */
class ProtectionAdresseIpTest {

    private static final String IP = "203.0.113.9";

    @Test
    void ladresseDunDepotAnonymeNestPasConservee() {
        assertThat(ProtectionAdresseIp.aConserver(true, false, IP)).isNull();
    }

    @Test
    void ladresseDunDepotSousProtectionNestPasConservee() {
        assertThat(ProtectionAdresseIp.aConserver(false, true, IP)).isNull();
    }

    @Test
    void ladresseNestPasConserveeSiLesDeuxSappliquent() {
        assertThat(ProtectionAdresseIp.aConserver(true, true, IP)).isNull();
    }

    @Test
    void ladresseDunDepotIdentifieSansProtectionEstConserveePourLaTracabilite() {
        assertThat(ProtectionAdresseIp.aConserver(false, false, IP)).isEqualTo(IP);
    }

    @Test
    void uneAdresseAbsenteResteAbsente() {
        assertThat(ProtectionAdresseIp.aConserver(false, false, null)).isNull();
    }

    @Test
    void unDossierAnonymeNeConservePasLAdresse() {
        Dossier d = Dossier.builder().anonymous(true).build();

        assertThat(ProtectionAdresseIp.aConserver(d, IP)).isNull();
    }

    @Test
    void unDossierDontLeDeclarantADemandeLaProtectionNeConservePasLAdresse() {
        Dossier d = Dossier.builder().anonymous(false)
                .declarant(Declarant.builder().protectionRequested(true).build()).build();

        assertThat(ProtectionAdresseIp.aConserver(d, IP)).isNull();
    }

    @Test
    void unDossierOrdinaireConserveLAdresse() {
        Dossier d = Dossier.builder().anonymous(false)
                .declarant(Declarant.builder().protectionRequested(false).build()).build();

        assertThat(ProtectionAdresseIp.aConserver(d, IP)).isEqualTo(IP);
    }
}
