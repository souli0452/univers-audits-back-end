package gov.bf.ascelc.univers_audits.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** L'espace des agents est à l'adresse /rpd : tous les liens envoyés par le serveur (courriels, notifications) l'utilisent. */
class EspaceAgentTest {

    @Test
    void lAdresseDeLEspaceAgentEstRpd() {
        assertThat(EspaceAgent.url("https://denoncer.asce-lc.bf", "")).isEqualTo("https://denoncer.asce-lc.bf/#/rpd");
    }

    @Test
    void ajouteLeCheminDemande() {
        assertThat(EspaceAgent.url("https://denoncer.asce-lc.bf", "/dossiers/42"))
                .isEqualTo("https://denoncer.asce-lc.bf/#/rpd/dossiers/42");
    }

    @Test
    void ignoreLesSlashsEnTropAFinDAdresse() {
        assertThat(EspaceAgent.url("https://denoncer.asce-lc.bf//", "/investigations/7"))
                .isEqualTo("https://denoncer.asce-lc.bf/#/rpd/investigations/7");
    }

    @Test
    void uneAdresseDeSiteNonRenseignee_ne_fait_pas_planter_l_envoi() {
        assertThat(EspaceAgent.url(null, "/dossiers/1")).isEqualTo("/#/rpd/dossiers/1");
    }

    @Test
    void neContientPlusAucuneTraceDeLAncienneAdresse() {
        assertThat(EspaceAgent.url("http://localhost:4200", "/dossiers")).doesNotContain("/#/app");
    }
}
