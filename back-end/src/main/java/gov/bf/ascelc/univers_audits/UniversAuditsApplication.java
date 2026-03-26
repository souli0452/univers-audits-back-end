package gov.bf.ascelc.univers_audits;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class UniversAuditsApplication {

	public static void main(String[] args) {

		SpringApplication.run(UniversAuditsApplication.class, args);
	}

}
