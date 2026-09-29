package gov.bf.ascelc.univers_audits.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * En production le back est derrière nginx : sans cette option, getRemoteAddr() renvoie l'adresse
 * du proxy pour tous les visiteurs et le limiteur de débit ({@link RateLimitFilter}) leur applique
 * un quota commun. « native » laisse Tomcat lire X-Forwarded-For uniquement lorsque la connexion
 * vient d'un proxy interne de confiance (un client externe ne peut donc pas falsifier son adresse).
 */
class ProdForwardedHeadersConfigTest {

    private Properties prod() throws IOException {
        return PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-prod.properties"));
    }

    @Test
    void leProfilProdLitLAdresseDuClientDansLesEnTetesDuProxy() throws IOException {
        assertThat(prod().getProperty("server.forward-headers-strategy")).isEqualTo("native");
    }

    @Test
    void lesAutresProfilsNeFontPasConfianceAuxEnTetesDuProxy() throws IOException {
        for (String profil : new String[]{"application.properties", "application-dev.properties"}) {
            Properties p = PropertiesLoaderUtils.loadProperties(new ClassPathResource(profil));
            assertThat(p.getProperty("server.forward-headers-strategy")).as(profil).isNull();
        }
    }
}
