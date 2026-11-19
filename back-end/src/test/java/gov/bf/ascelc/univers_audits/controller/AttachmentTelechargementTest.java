package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.entity.Attachment;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.service.AttachmentStorageService;
import gov.bf.ascelc.univers_audits.service.AuditService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Télécharger une pièce jointe est un droit distinct de la consulter, et chaque téléchargement laisse une trace. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttachmentTelechargementTest {

    @Mock private AttachmentStorageService storage;
    @Mock private DossierAccessGuard accessGuard;
    @Mock private AuditService auditService;

    @TempDir Path dossierTemporaire;

    private AttachmentController controleur;
    private Attachment piece;
    private Dossier dossier;
    private Jwt jwt;
    private MockHttpServletRequest requete;

    @BeforeEach
    void setUp() throws Exception {
        controleur = new AttachmentController(storage, accessGuard, auditService);
        dossier = Dossier.builder().id(UUID.randomUUID()).number("DOS-2026-0042").isConfidential(false).build();
        Path fichier = Files.writeString(dossierTemporaire.resolve("preuve.png"), "contenu");
        piece = Attachment.builder().id(UUID.randomUUID()).dossier(dossier).originalName("preuve.png")
                .mimeType("image/png").filePath(fichier.toString()).build();
        when(storage.getOrThrow(piece.getId())).thenReturn(piece);
        when(accessGuard.getDossierOrThrow(dossier.getId())).thenReturn(dossier);
        jwt = Jwt.withTokenValue("t").header("alg", "none").subject("agent-1").claim("name", "Awa Traoré")
                .claim("roles", java.util.List.of("CGE")).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        requete = new MockHttpServletRequest();
        requete.setRemoteAddr("10.1.2.3");
        requete.addHeader("User-Agent", "JUnit");
    }

    @Test
    void leTelechargementEstToujoursForceEnPieceJointeMemePourUneImage() {
        ResponseEntity<Resource> reponse = controleur.telecharger(piece.getId().toString(), requete, jwt);

        assertThat(reponse.getStatusCode().value()).isEqualTo(200);
        assertThat(reponse.getHeaders().getFirst("Content-Disposition")).startsWith("attachment;");
        assertThat(reponse.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void chaqueTelechargementEstTraceAvecLAgentLeDossierEtLeFichier() {
        controleur.telecharger(piece.getId().toString(), requete, jwt);

        verify(auditService).logAction(eq("agent-1"), eq("Awa Traoré"), eq("CGE"),
                eq("TELECHARGER_PIECE_JOINTE"), eq("PIECE_JOINTE"), eq(piece.getId().toString()),
                org.mockito.ArgumentMatchers.argThat(d -> d.contains("preuve.png") && d.contains("DOS-2026-0042")),
                eq("10.1.2.3"), eq("JUnit"));
    }

    @Test
    void unAccesRefuseAuDossierNeTelechargeRienEtNeTraceRien() {
        doThrow(new BusinessException("Accès refusé")).when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> controleur.telecharger(piece.getId().toString(), requete, jwt))
                .isInstanceOf(BusinessException.class);

        verify(auditService, never()).logAction(any(), any(), any(), anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void unDossierConfidentielRefuseALAgentSansLeDroitEtNeTraceRien() {
        dossier.setIsConfidential(true);
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        assertThatThrownBy(() -> controleur.telecharger(piece.getId().toString(), requete, jwt))
                .isInstanceOf(BusinessException.class);

        verify(auditService, never()).logAction(any(), any(), any(), anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void unFichierAbsentDuDisqueDonne404SansTrace() {
        piece.setFilePath(dossierTemporaire.resolve("disparu.png").toString());

        ResponseEntity<Resource> reponse = controleur.telecharger(piece.getId().toString(), requete, jwt);

        assertThat(reponse.getStatusCode().value()).isEqualTo(404);
        verify(auditService, never()).logAction(any(), any(), any(), anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void consulterSurLaPlateformeResteSansTelechargement() {
        // La consultation (lecteur, aperçu) garde sa route ; seule la image est affichée en ligne, le reste en pièce jointe.
        ResponseEntity<Resource> reponse = controleur.download(piece.getId().toString());

        assertThat(reponse.getHeaders().getFirst("Content-Disposition")).startsWith("inline;");
    }
}
