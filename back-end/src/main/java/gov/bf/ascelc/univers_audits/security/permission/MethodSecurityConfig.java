package gov.bf.ascelc.univers_audits.security.permission;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.method.PrePostTemplateDefaults;

/** Active les modèles d'annotation : {@code {value}} dans {@link HasPermission} est remplacé par la permission. */
@Configuration(proxyBeanMethods = false)
public class MethodSecurityConfig {

    /** Statique : le bean doit exister avant les autres beans de sécurité des méthodes. */
    @Bean
    static PrePostTemplateDefaults prePostTemplateDefaults() {
        return new PrePostTemplateDefaults();
    }
}
