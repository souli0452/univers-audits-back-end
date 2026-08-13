package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.enums.AttachmentSource;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Map;

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

    private static final Map<AttachmentSource, Character> ATTACHMENT_SOURCE_LETTERS = Map.of(
            AttachmentSource.INITIAL_SUBMISSION, 'S',
            AttachmentSource.FIELD_INVESTIGATION, 'T',
            AttachmentSource.SOCIAL_MEDIA, 'R',
            AttachmentSource.PRESS_MEDIA, 'P',
            AttachmentSource.EXTERNAL_AUDIT, 'E',
            AttachmentSource.OTHER, 'X');

    public String generateAttachmentCode(AttachmentSource source, long sequence) {
        Character letter = ATTACHMENT_SOURCE_LETTERS.get(source);
        if (letter == null) {
            throw new IllegalStateException(
                    "Aucune lettre de code définie pour AttachmentSource." + source);
        }
        return String.format("ACC-%c-%05d", letter, sequence);
    }
}