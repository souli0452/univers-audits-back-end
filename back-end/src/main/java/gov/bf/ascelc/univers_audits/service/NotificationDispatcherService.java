package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.entity.Declarant;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcherService {

    private final EmailService emailService;
    private final SmsService   smsService;

    public void dispatchAccessCode(Dossier dossier) {
        Declarant declarant = dossier.getDeclarant();
        if (declarant == null) {
            log.debug("dispatchAccessCode — pas de déclarant, envoi ignoré");
            return;
        }

        String accessCode    = dossier.getAccessCode();
        String declarantName = resolveDisplayName(declarant);

        if (declarant.getEmail() != null && !declarant.getEmail().isBlank()) {
            emailService.sendAccessCode(
                    declarant.getEmail(),
                    accessCode,
                    declarantName);
            log.info("[Dispatcher] AccessCode email → {}", declarant.getEmail());
        }

        if (declarant.getPhoneNumber() != null
                && !declarant.getPhoneNumber().isBlank()) {

            smsService.sendAccessCode(
                    declarant.getPhoneNumber(),
                    accessCode);
            log.info("[Dispatcher] AccessCode SMS → {}", declarant.getPhoneNumber());
        }

        if ((declarant.getEmail() == null || declarant.getEmail().isBlank())
                && (declarant.getPhoneNumber() == null
                || declarant.getPhoneNumber().isBlank())) {
            log.debug("[Dispatcher] dispatchAccessCode — aucun contact pour code {}",
                    accessCode);
        }
    }

    public void dispatchStatusUpdate(Dossier dossier,
                                     String statusLabel,
                                     String note) {
        Declarant declarant = dossier.getDeclarant();
        if (declarant == null) {
            log.debug("[Dispatcher] dispatchStatusUpdate — pas de déclarant, ignoré");
            return;
        }

        String accessCode    = dossier.getAccessCode();
        String declarantName = resolveDisplayName(declarant);

        String[] content         = EmailService.getStatusEmailContent(statusLabel);
        String   statusLabelText = content[0];
        String   statusDesc      = content[1];

        if (declarant.getEmail() != null && !declarant.getEmail().isBlank()) {
            emailService.sendStatusUpdate(
                    declarant.getEmail(),
                    accessCode,
                    declarantName,
                    statusLabelText,
                    statusDesc,
                    note);
            log.info("[Dispatcher] StatusUpdate email → {}", declarant.getEmail());
        }

        if (declarant.getPhoneNumber() != null
                && !declarant.getPhoneNumber().isBlank()) {
            smsService.sendStatusUpdate(
                    declarant.getPhoneNumber(),
                    accessCode,
                    statusLabelText);
            log.info("[Dispatcher] StatusUpdate SMS → {}", declarant.getPhoneNumber());
        }
    }

    public void dispatchComplementRequest(Dossier dossier, String motif) {
        Declarant declarant = dossier.getDeclarant();
        if (declarant == null) return;

        String accessCode    = dossier.getAccessCode();
        String declarantName = resolveDisplayName(declarant);

        if (declarant.getEmail() != null && !declarant.getEmail().isBlank()) {
            emailService.sendComplementRequest(
                    declarant.getEmail(), accessCode, declarantName, motif);
            log.info("[Dispatcher] ComplementRequest email → {}", declarant.getEmail());
        }

        if (declarant.getPhoneNumber() != null
                && !declarant.getPhoneNumber().isBlank()) {
            smsService.sendComplementRequest(
                    declarant.getPhoneNumber(), accessCode);
            log.info("[Dispatcher] ComplementRequest SMS → {}", declarant.getPhoneNumber());
        }
    }


    public void dispatchTransferExternal(Dossier dossier,
                                         String institutionLabel) {
        Declarant declarant = dossier.getDeclarant();
        if (declarant == null) return;

        String accessCode    = dossier.getAccessCode();
        String declarantName = resolveDisplayName(declarant);

        if (declarant.getEmail() != null && !declarant.getEmail().isBlank()) {
            emailService.sendTransferExternal(
                    declarant.getEmail(), accessCode,
                    declarantName, institutionLabel);
            log.info("[Dispatcher] TransferExternal email → {}", declarant.getEmail());
        }

        if (declarant.getPhoneNumber() != null
                && !declarant.getPhoneNumber().isBlank()) {
            smsService.sendTransferExternal(
                    declarant.getPhoneNumber(), accessCode);
            log.info("[Dispatcher] TransferExternal SMS → {}", declarant.getPhoneNumber());
        }
    }


    private String resolveDisplayName(Declarant declarant) {
        if (declarant.isAnonymous()
                || Boolean.TRUE.equals(declarant.getProtectionRequested())) {
            return null;
        }
        return declarant.getDisplayName();
    }
}