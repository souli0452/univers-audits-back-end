package gov.bf.ascelc.univers_audits.shared.exceptions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un corps envoyé avec un type de contenu que la route n'accepte pas est une erreur du client : 415, sans alerte
 * « erreur interne » dans les journaux du serveur (c'était un 500 avec une trace complète à chaque fois).
 */
class GlobalExceptionHandlerTypeDeContenuTest {

    @RestController
    static class RouteQuiAccepteDuJson {
        @PostMapping(value = "/essai", consumes = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, String> recevoir(@RequestBody Map<String, String> corps) {
            return corps;
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RouteQuiAccepteDuJson())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void unTypeDeContenuNonAccepteDonne415EtNonUneErreurInterne() throws Exception {
        mvc.perform(post("/essai").contentType(MediaType.APPLICATION_OCTET_STREAM).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void lAnnonceNeRevelePasLesDetailsInternesEtResteEnFrancais() throws Exception {
        mvc.perform(post("/essai").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Type de contenu non pris en charge par cette ressource"));
    }

    @Test
    void unCorpsAuBonTypeResteAccepte() throws Exception {
        mvc.perform(post("/essai").contentType(MediaType.APPLICATION_JSON).content("{\"a\":\"b\"}"))
                .andExpect(status().isOk());
    }
}
