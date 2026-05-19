package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.entity.Declarant;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.service.SmsService;
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
        String dossierNumber = dossier.getNumber(); // peut être null si SOUMIS

        if (declarant.getEmail() != null
                && !declarant.getEmail().isBlank()) {
            emailService.sendAccessCode(
                    declarant.getEmail(), accessCode, dossierNumber);
            log.info("AccessCode email planifié → {}", declarant.getEmail());
        }

        if (declarant.getPhoneNumber() != null
                && !declarant.getPhoneNumber().isBlank()) {
            smsService.sendAccessCode(
                    declarant.getPhoneNumber(), accessCode, dossierNumber);
            log.info("AccessCode SMS planifié → {}", declarant.getPhoneNumber());
        }

        if ((declarant.getEmail() == null || declarant.getEmail().isBlank())
                && (declarant.getPhoneNumber() == null
                || declarant.getPhoneNumber().isBlank())) {
            log.debug("dispatchAccessCode — aucun contact fourni pour dossier {}",
                    accessCode);
        }
    }


    public void dispatchStatusUpdate(Dossier dossier,
                                     String statusLabel,
                                     String message) {
        Declarant declarant = dossier.getDeclarant();
        if (declarant == null) {
            log.debug("dispatchStatusUpdate — pas de déclarant, envoi ignoré");
            return;
        }

        String dossierNumber = dossier.getNumber();

        if (declarant.getEmail() != null
                && !declarant.getEmail().isBlank()) {
            emailService.sendStatusUpdate(
                    declarant.getEmail(),
                    dossierNumber,
                    statusLabel,
                    message);
            log.info("StatusUpdate email planifié → {} — dossier {}",
                    declarant.getEmail(), dossierNumber);
        }

        if (declarant.getPhoneNumber() != null
                && !declarant.getPhoneNumber().isBlank()) {
            smsService.sendStatusUpdate(
                    declarant.getPhoneNumber(),
                    dossierNumber,
                    statusLabel);
            log.info("StatusUpdate SMS planifié → {} — dossier {}",
                    declarant.getPhoneNumber(), dossierNumber);
        }
    }
}