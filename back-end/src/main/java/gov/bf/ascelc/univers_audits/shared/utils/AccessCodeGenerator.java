package gov.bf.ascelc.univers_audits.shared.utils;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class AccessCodeGenerator {

    private static final String CHARACTERS =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int CODE_LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CHARACTERS.charAt(
                    random.nextInt(CHARACTERS.length())));
        }
        return code.toString();
    }

    public String generateDossierNumber(int sequence, int year) {
        return String.format("ASCE-%d-%06d", year, sequence);
    }
}