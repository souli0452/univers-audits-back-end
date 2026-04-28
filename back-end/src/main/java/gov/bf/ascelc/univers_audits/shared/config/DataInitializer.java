package gov.bf.ascelc.univers_audits.shared.config;

import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final AgentRepository agentRepository;


    @Value("${asce.admin.keycloak-sub:}")
    private String adminKeycloakId;

    @Bean
    public CommandLineRunner initData() {
        return args -> {

            if (adminKeycloakId == null || adminKeycloakId.isBlank()) {
                log.warn("Property 'asce.admin.keycloak-sub' non configurée "
                        + "— initialisation admin ignorée");
                return;
            }

            if (!agentRepository.existsByKeycloakId(adminKeycloakId)) {
                Agent admin = Agent.builder()
                        .keycloakId(adminKeycloakId)
                        .matricule("ASCE-ADMIN-003")
                        .firstName("Admin")
                        .lastName("ASCE")
                        .email("dsi@asce-lc.bf")
                        .actif(true)
                        .build();

                agentRepository.save(admin);
                log.info("Agent admin créé — matricule: ASCE-ADMIN-003, "
                        + "keycloakId: {}", adminKeycloakId);
            } else {
                log.info("Agent admin déjà présent en base");
            }
        };
    }
}