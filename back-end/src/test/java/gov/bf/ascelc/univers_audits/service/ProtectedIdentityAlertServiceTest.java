package gov.bf.ascelc.univers_audits.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProtectedIdentityAlertServiceTest {

    private static final String CONSULTATION = "CONSULTER_IDENTITE_LANCEUR_ALERTE";

    @Mock private EmailService email;

    private final MutableClock horloge = new MutableClock(Instant.parse("2026-11-15T10:00:00Z"));

    @BeforeEach
    void livraisonReussieParDefaut() {
        when(email.sendInternalAlertTracked(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(true));
    }

    private ProtectedIdentityAlertService service(String destinataire) {
        return new ProtectedIdentityAlertService(email, destinataire, Duration.ofHours(1), horloge);
    }

    private void envoyer(ProtectedIdentityAlertService service, String acteur, String entite, String description) {
        service.notifyAccess(acteur, "Awa Test", "CGE", CONSULTATION, entite, description);
    }

    private void verifierEnvois(int attendus) {
        verify(email, times(attendus)).sendInternalAlertTracked(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void envoieUneAlerteALAdresseConfiguree() {
        service("dsi@asce-lc.bf").notifyAccess("kc-1", "Awa Test", "ADMIN_DDIC", CONSULTATION, "d-1",
                "Consultation de l'identité du lanceur d'alerte protégé — dossier DOS-1");

        ArgumentCaptor<String> corps = ArgumentCaptor.forClass(String.class);
        verify(email).sendInternalAlertTracked(eq("dsi@asce-lc.bf"), anyString(), anyString(), corps.capture());
        assertThat(corps.getValue()).contains("Awa Test").contains("ADMIN_DDIC").contains("DOS-1");
    }

    @Test
    void neFaitRienSansAdresseConfiguree() {
        envoyer(service(""), "kc-1", "d-1", "x");
        envoyer(service(null), "kc-1", "d-1", "x");

        verifyNoInteractions(email);
    }

    @Test
    void neFaitRienPourUneActionHorsIdentiteProtegee() {
        service("dsi@asce-lc.bf").notifyAccess("kc-1", "Awa Test", "ADMIN_DDIC", "MODIFIER_PERMISSIONS_ROLE",
                "CGE", "x");

        verifyNoInteractions(email);
    }

    @Test
    void neRenvoiePasLaMemeAlertePendantLaFenetre() {
        ProtectedIdentityAlertService service = service("dsi@asce-lc.bf");

        envoyer(service, "kc-1", "d-1", "x");
        horloge.avancer(Duration.ofMinutes(59));
        envoyer(service, "kc-1", "d-1", "x");

        verifierEnvois(1);
    }

    @Test
    void renvoieLAlerteUneFoisLaFenetreEcoulee() {
        ProtectedIdentityAlertService service = service("dsi@asce-lc.bf");

        envoyer(service, "kc-1", "d-1", "x");
        horloge.avancer(Duration.ofMinutes(61));
        envoyer(service, "kc-1", "d-1", "x");

        verifierEnvois(2);
    }

    @Test
    void uneAutrePersonneOuUnAutreDossierDeclencheUneNouvelleAlerte() {
        ProtectedIdentityAlertService service = service("dsi@asce-lc.bf");

        envoyer(service, "kc-1", "d-1", "x");
        envoyer(service, "kc-2", "d-1", "x");
        envoyer(service, "kc-1", "d-2", "x");

        verifierEnvois(3);
    }

    @Test
    void deuxListesDifferentesSansIdentifiantNeSeMasquentPasMutuellement() {
        ProtectedIdentityAlertService service = service("dsi@asce-lc.bf");

        envoyer(service, "kc-1", null, "2 lanceur(s) — dossier(s) : DOS-1, DOS-2");
        envoyer(service, "kc-1", null, "1 lanceur(s) — dossier(s) : DOS-9");

        verifierEnvois(2);
    }

    @Test
    void laMemeListeRepeteeNeDeclencheQuUneAlerte() {
        ProtectedIdentityAlertService service = service("dsi@asce-lc.bf");

        envoyer(service, "kc-1", null, "2 lanceur(s) — dossier(s) : DOS-1, DOS-2");
        envoyer(service, "kc-1", null, "2 lanceur(s) — dossier(s) : DOS-1, DOS-2");

        verifierEnvois(1);
    }

    @Test
    void unEnvoiEchoueNeBloquePasLaProchaineAlerte() {
        when(email.sendInternalAlertTracked(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(false))
                .thenReturn(CompletableFuture.completedFuture(true));
        ProtectedIdentityAlertService service = service("dsi@asce-lc.bf");

        envoyer(service, "kc-1", "d-1", "x");
        envoyer(service, "kc-1", "d-1", "x");

        verifierEnvois(2);
    }

    @Test
    void unEnvoiEnErreurNeBloquePasLaProchaineAlerte() {
        when(email.sendInternalAlertTracked(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("SMTP en panne")))
                .thenReturn(CompletableFuture.completedFuture(true));
        ProtectedIdentityAlertService service = service("dsi@asce-lc.bf");

        envoyer(service, "kc-1", "d-1", "x");
        envoyer(service, "kc-1", "d-1", "x");

        verifierEnvois(2);
    }

    @Test
    void unEnvoiReussiGardeLeCreneauReserve() {
        ProtectedIdentityAlertService service = service("dsi@asce-lc.bf");

        envoyer(service, "kc-1", "d-1", "x");
        envoyer(service, "kc-1", "d-1", "x");
        envoyer(service, "kc-1", "d-1", "x");

        verifierEnvois(1);
    }

    /** Horloge de test dont on avance le temps à la main. */
    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant depart) {
            this.instant = depart;
        }

        void avancer(Duration duree) {
            instant = instant.plus(duree);
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
