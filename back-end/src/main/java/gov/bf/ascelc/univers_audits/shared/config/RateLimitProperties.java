package gov.bf.ascelc.univers_audits.shared.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rate-limit")
@Data
public class RateLimitProperties {

    private boolean enabled = true;
    private Rule submit = new Rule(5, 10);
    private Rule track = new Rule(20, 1);
    private Rule attachmentUpload = new Rule(10, 10);
    private Rule statsPublic = new Rule(60, 1);
    private Rule complementRead = new Rule(20, 1);
    private Rule complementSubmit = new Rule(5, 10);

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Rule {
        private int capacity;
        private int refillMinutes;
    }
}
