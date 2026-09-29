package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.response.ComplementRequestResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.ComplementSubmissionResponse;
import gov.bf.ascelc.univers_audits.service.ComplementPublicService;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import gov.bf.ascelc.univers_audits.shared.exceptions.GlobalExceptionHandler;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ComplementPublicControllerTest {

    private static final String URL = "/api/v1/dossiers/public/complement/ABCD1234";

    @Mock private ComplementPublicService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ComplementPublicController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void get_renvoie_le_motif_et_l_echeance() throws Exception {
        when(service.getComplementRequest("ABCD1234")).thenReturn(new ComplementRequestResponse(
                "EN_ATTENTE_COMPLEMENT", "Fournissez les justificatifs",
                Instant.parse("2026-09-20T09:00:00Z"), Instant.parse("2026-09-30T23:59:59Z"), false));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EN_ATTENTE_COMPLEMENT"))
                .andExpect(jsonPath("$.motif").value("Fournissez les justificatifs"))
                .andExpect(jsonPath("$.overdue").value(false));
    }

    @Test
    void get_code_inconnu_donne_404() throws Exception {
        when(service.getComplementRequest("ABCD1234"))
                .thenThrow(new ResourceNotFoundException("Dossier introuvable avec ce code d'accès"));

        mvc.perform(get(URL)).andExpect(status().isNotFound());
    }

    @Test
    void get_dossier_qui_n_attend_pas_de_complement_donne_409() throws Exception {
        when(service.getComplementRequest("ABCD1234"))
                .thenThrow(new ConflictException("Aucun complément n'est attendu pour ce dossier"));

        mvc.perform(get(URL)).andExpect(status().isConflict());
    }

    @Test
    @SuppressWarnings("unchecked")
    void post_transmet_le_message_les_fichiers_et_l_adresse_ip() throws Exception {
        when(service.submitComplement(eq("ABCD1234"), anyString(), any(), anyString()))
                .thenReturn(new ComplementSubmissionResponse("EN_ETUDE_OPPORTUNITE", false, 1));
        MockMultipartFile fichier = new MockMultipartFile(
                "files", "preuve.pdf", "application/pdf", "contenu".getBytes());

        mvc.perform(multipart(URL).file(fichier).param("message", "Voici mes pièces")
                        .with(request -> { request.setRemoteAddr("10.0.0.7"); return request; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EN_ETUDE_OPPORTUNITE"))
                .andExpect(jsonPath("$.late").value(false))
                .andExpect(jsonPath("$.filesUploaded").value(1));

        ArgumentCaptor<List<MultipartFile>> files = ArgumentCaptor.forClass(List.class);
        verify(service).submitComplement(eq("ABCD1234"), eq("Voici mes pièces"), files.capture(), eq("10.0.0.7"));
        assertThat(files.getValue()).hasSize(1);
        assertThat(files.getValue().get(0).getOriginalFilename()).isEqualTo("preuve.pdf");
    }

    @Test
    void post_sans_fichier_est_accepte_quand_le_service_l_accepte() throws Exception {
        when(service.submitComplement(eq("ABCD1234"), anyString(), any(), anyString()))
                .thenReturn(new ComplementSubmissionResponse("EN_ETUDE_OPPORTUNITE", true, 0));

        mvc.perform(multipart(URL).param("message", "Seulement un message")
                        .with(request -> { request.setRemoteAddr("10.0.0.7"); return request; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.late").value(true));
    }

    @Test
    void post_seconde_reponse_donne_409() throws Exception {
        when(service.submitComplement(eq("ABCD1234"), anyString(), any(), anyString()))
                .thenThrow(new ConflictException("Aucun complément n'est attendu pour ce dossier"));

        mvc.perform(multipart(URL).param("message", "Deuxième réponse")
                        .with(request -> { request.setRemoteAddr("10.0.0.7"); return request; }))
                .andExpect(status().isConflict());
    }
}
