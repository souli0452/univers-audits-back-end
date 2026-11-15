package gov.bf.ascelc.univers_audits.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProtectedIdentityAlertServiceTest {

    private static final String CONSULTATION = "CONSULTER_IDENTITE_LANCEUR_ALERTE";

    @Mock private EmailService email;

    private final MutableClock horloge = new MutableClock(Instant.parse("2026-11-15T10:00:00Z"));

    private ProtectedIdentityAlertService service(String destinataire) {
        return new ProtectedIdentityAlertService(email, destinataire, Duration.ofHours(1), horloge);
    }

    @Test
    void envoieUneAlerteALAdresseConfiguree() {
        service("dsi@asce-lc.bf").notifyAccess("kc-1", "Awa Test", "ADMIN_DDIC", CONSULTATION, "d-1",
                "Consultation de l'identité du lanceur d'alerte protégé — dossier DOS-1");

        ArgumentCaptor<String> corps = ArgumentCaptor.forClass(String.class);
        verify(email).sendInternalAlert(eq("dsi@asce-lc.bf"), anyString(), anyString(), corps.capture());
        assertThat(corps.getValue()).contains("Awa Test").contains("ADMIN_DDIC").contains("DOS-1");
    }

    @Test
    void neFaitRienSansAdresseConfiguree() {
        service("").notifyAccess("kc-1", "Awa Test", "ADMIN_DDIC", CONSULTATION, "d-1", "x");
        service(null).notifyAccess("kc-1", "Awa Test", "ADMIN_DDIC", CONSULTATION, "d-1", "x");

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

        service.notifyAccess("kc-1", "Awa Test", "CGE", CONSULTATION, "d-1", "x");
        horloge.avancer(Duration.ofMinutes(59));
        service.notifyAccess("kc-1", "Awa Test", "CGE", CONSULTATION, "d-1", "x");

        verify(email, times(1)).sendInternalAlert(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void renvoieLAlerteUneFoisLaFenetreEcoulee() {
        ProtectedIdentityAlertService service = service("dsi@asce-lc.bf");

        service.notifyAccess("kc-1", "Awa Test", "CGE", CONSULTATION, "d-1", "x");
        horloge.avancer(Duration.ofMinutes(61));
        service.notifyAccess("kc-1", "Awa Test", "CGE", CONSULTATION, "d-1", "x");

        verify(email, times(2)).sendInternalAlert(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void uneAutrePersonneOuUnAutreDossierDeclencheUneNouvelleAlerte() {
        ProtectedIdentityAlertService service = service("dsi@asce-lc.bf");

        service.notifyAccess("kc-1", "Awa Test", "CGE", CONSULTATION, "d-1", "x");
        service.notifyAccess("kc-2", "Issa Test", "CGE", CONSULTATION, "d-1", "x");
        service.notifyAccess("kc-1", "Awa Test", "CGE", CONSULTATION, "d-2", "x");

        verify(email, times(3)).sendInternalAlert(anyString(), anyString(), anyString(), anyString());
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
