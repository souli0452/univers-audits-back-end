package gov.bf.ascelc.univers_audits.shared.exceptions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Envoi de pièces mal formé = erreur du client, jamais une erreur interne. Constat du scan actif OWASP ZAP du 2026-11-24 :
 * une requête qui n'est pas au format multipart sur une route d'envoi de fichiers donnait un 500 et une trace complète
 * dans les journaux (des centaines de lignes pendant le scan) ; un fichier trop gros donnait aussi un 500.
 */
class GlobalExceptionHandlerEnvoiDeFichiersTest {

    @RestController
    static class RouteDEnvoi {
        @PostMapping("/envoi")
        public String recevoir(@RequestPart("files") List<MultipartFile> files) {
            return "ok";
        }

        @PostMapping("/trop-gros")
        public String tropGros() {
            throw new MaxUploadSizeExceededException(26_214_400L);
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RouteDEnvoi())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void uneRequeteQuiNEstPasAuFormatMultipartDonne400EtNonUneErreurInterne() throws Exception {
        mvc.perform(post("/envoi").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void unePartieAttendueManquanteDonne400() throws Exception {
        mvc.perform(multipart("/envoi").file(new MockMultipartFile("autre", "a.txt", "text/plain", "x".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void unFichierTropGrosDonne413EtUnMessageClair() throws Exception {
        mvc.perform(post("/trop-gros"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("Fichier trop volumineux"));
    }

    @Test
    void unEnvoiBienFormeResteAccepte() throws Exception {
        mvc.perform(multipart("/envoi").file(new MockMultipartFile("files", "a.txt", "text/plain", "x".getBytes())))
                .andExpect(status().isOk());
    }
}
