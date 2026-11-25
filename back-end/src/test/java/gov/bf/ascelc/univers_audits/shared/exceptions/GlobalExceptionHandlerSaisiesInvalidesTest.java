package gov.bf.ascelc.univers_audits.shared.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Une saisie invalide est une erreur du client, jamais une erreur interne (scan actif OWASP ZAP du 2026-11-25). */
class GlobalExceptionHandlerSaisiesInvalidesTest {

    @RestController
    static class Routes {
        @GetMapping("/periode")
        public String periode(@RequestParam String start) {
            return Instant.parse(start).toString();
        }

        @GetMapping("/base/{etat}")
        public String base(@PathVariable String etat) {
            throw new DataIntegrityViolationException("could not execute statement",
                    new SQLException("ERROR: détail interne de la base", etat));
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Routes())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void uneDateMalFormeeDonne400() throws Exception {
        mvc.perform(get("/periode").param("start", "start"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void unTexteTropLongOuUnCaractereNulDonne400SansDetailDeLaBase() throws Exception {
        for (String etat : new String[]{"22001", "22021"}) {
            mvc.perform(get("/base/" + etat))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Requête invalide — vérifiez les données envoyées"));
        }
    }

    @Test
    void uneValeurDejaUtiliseeDonne409() throws Exception {
        mvc.perform(get("/base/23505"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void uneAutreViolationResteUneErreurInterne() throws Exception {
        // ex. colonne obligatoire non remplie : c'est un défaut de l'application, à voir dans les journaux
        mvc.perform(get("/base/23502")).andExpect(status().isInternalServerError());
    }
}
