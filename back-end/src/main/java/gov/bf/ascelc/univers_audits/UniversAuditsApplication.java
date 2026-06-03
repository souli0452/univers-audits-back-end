package gov.bf.ascelc.univers_audits;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "securityAuditorAware")
@EnableScheduling
@EnableAsync
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class UniversAuditsApplication {

	public static void main(String[] args) {
		SpringApplication.run(UniversAuditsApplication.class, args);
	}
}