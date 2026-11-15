package gov.bf.ascelc.univers_audits.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CurrentUserAuditorTest {

    @Mock private AuditService audit;
    @Mock private ProtectedIdentityAlertService alerts;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private CurrentUserAuditor auditor() {
        return new CurrentUserAuditor(audit, alerts);
    }

    private static void connecterAvecRoles(String... roles) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("kc-42").claim("name", "Awa Test")
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void enregistreLActeurDuJetonSesRolesMetierEtLActionDemandee() {
        connecterAvecRoles("ADMIN_DDIC", "default-roles-asce-lc", "offline_access", "uma_authorization");

        auditor().record("CONSULTER_IDENTITE_LANCEUR_ALERTE", "DOSSIER", "d-1", "Consultation du dossier N-1");

        verify(audit).logAction(eq("kc-42"), eq("Awa Test"), eq("ADMIN_DDIC"),
                eq("CONSULTER_IDENTITE_LANCEUR_ALERTE"), eq("DOSSIER"), eq("d-1"),
                eq("Consultation du dossier N-1"), isNull(), isNull());
    }

    @Test
    void listeTousLesRolesMetierDeLActeurSansLesRolesTechniques() {
        connecterAvecRoles("CGE", "DCP", "default-roles-asce-lc");

        auditor().record("A", "DOSSIER", "d-1", "x");

        verify(audit).logAction(eq("kc-42"), any(), eq("CGE,DCP"), eq("A"), eq("DOSSIER"), eq("d-1"),
                eq("x"), isNull(), isNull());
    }

    @Test
    void sansUtilisateurConnecteLActeurEstSYSTEM() {
        auditor().record("A", "DOSSIER", "d-1", "x");

        verify(audit).logAction(eq("SYSTEM"), isNull(), isNull(), eq("A"), eq("DOSSIER"), eq("d-1"),
                eq("x"), isNull(), isNull());
    }

    @Test
    void alerteLaSecuriteQuandLActionConcerneUneIdentiteProtegee() {
        connecterAvecRoles("ADMIN_DDIC");

        auditor().record("CONSULTER_IDENTITE_LANCEUR_ALERTE", "DOSSIER", "d-1", "Consultation dossier DOS-1");

        verify(alerts).notifyAccess("kc-42", "Awa Test", "ADMIN_DDIC", "CONSULTER_IDENTITE_LANCEUR_ALERTE", "d-1",
                "Consultation dossier DOS-1");
    }

    @Test
    void ecritToujoursAuJournalMemeSiLAlerteEchoue() {
        connecterAvecRoles("CGE");
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP en panne")).when(alerts)
                .notifyAccess(any(), any(), any(), any(), any(), any());

        auditor().record("EXPORTER_IDENTITE_LANCEUR_ALERTE", "DOSSIER", "d-1", "Export");

        verify(audit).logAction(eq("kc-42"), any(), eq("CGE"), eq("EXPORTER_IDENTITE_LANCEUR_ALERTE"), eq("DOSSIER"),
                eq("d-1"), eq("Export"), isNull(), isNull());
    }
}
