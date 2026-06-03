package gov.bf.ascelc.univers_audits.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class SmsService {

    @Value("${sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${africastalking.username:sandbox}")
    private String username;

    @Value("${africastalking.apiKey:sandbox_key}")
    private String apiKey;

    @Value("${africastalking.senderId:ASCELC}")
    private String senderId;

    @Value("${africastalking.apiUrl:https://api.sandbox.africastalking.com/version1/messaging}")
    private String apiUrl;

    @Value("${app.portal-url:integrite.bf}")
    private String portalUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Async
    public void sendAccessCode(String phoneNumber, String accessCode) {

        String message = String.format(
                "ASCE-LC INTEGRITE+\n" +
                        "Dossier enregistre.\n" +
                        "Code suivi: %s\n" +
                        "Suivi: integrite.bf/portail/suivi",
                accessCode
        );
        send(phoneNumber, message);
    }

    @Async
    public void sendStatusUpdate(String phoneNumber,
                                 String accessCode,
                                 String statusLabel) {

        String message = String.format(
                "ASCE-LC INTEGRITE+\n" +
                        "Votre dossier: %s\n" +
                        "Code: %s\n" +
                        "Suivi: integrite.bf/portail/suivi",
                statusLabel,
                accessCode
        );
        send(phoneNumber, message);
    }

    @Async
    public void sendComplementRequest(String phoneNumber, String accessCode) {
        String message = String.format(
                "ASCE-LC INTEGRITE+\n" +
                        "Action requise: des informations complementaires\n" +
                        "sont necessaires pour votre dossier.\n" +
                        "Code: %s - integrite.bf/portail/suivi",
                accessCode
        );
        send(phoneNumber, message);
    }

    @Async
    public void sendTransferExternal(String phoneNumber, String accessCode) {
        String message = String.format(
                "ASCE-LC INTEGRITE+\n" +
                        "Votre dossier a ete transmis a l'institution\n" +
                        "competente pour traitement.\n" +
                        "Code: %s",
                accessCode
        );
        send(phoneNumber, message);
    }


    @Async
    public void sendInternalAlert(String phoneNumber,
                                  String alertTitle,
                                  String dossierNumber) {

        String message = String.format(
                "ASCE-LC [ALERTE INTERNE]\n" +
                        "%s\n" +
                        "Dossier: %s\n" +
                        "Connectez-vous sur INTEGRITE+",
                alertTitle,
                dossierNumber != null ? dossierNumber : "N/A"
        );
        send(phoneNumber, message);
    }

    private void send(String phoneNumber, String message) {
        if (!smsEnabled) {
            log.info("[SMS] Désactivé — message simulé pour {} : {}",
                    phoneNumber, message.replace("\n", " | "));
            return;
        }

        try {
            String normalized = normalizePhone(phoneNumber);
            if (normalized == null || normalized.isBlank()) {
                log.warn("[SMS] Numéro invalide '{}' — SMS non envoyé", phoneNumber);
                return;
            }

            if (message.length() > 160) {
                log.warn("[SMS] Message trop long ({} chars) — sera découpé en {} segments",
                        message.length(),
                        (int) Math.ceil(message.length() / 153.0));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Accept", "application/json");
            headers.set("apiKey", apiKey);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("username", username);
            body.add("to",       normalized);
            body.add("message",  message);
            if (senderId != null && !senderId.isBlank()) {
                body.add("from", senderId);
            }

            HttpEntity<MultiValueMap<String, String>> request =
                    new HttpEntity<>(body, headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(apiUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[SMS] Envoyé à {} — réponse: {}",
                        normalized, response.getBody());
            } else {
                log.warn("[SMS] Échec pour {} — statut: {} — réponse: {}",
                        normalized, response.getStatusCode(), response.getBody());
            }

        } catch (Exception e) {
            log.error("[SMS] Erreur envoi à {} : {}", phoneNumber, e.getMessage());
        }
    }

    private String normalizePhone(String phone) {
        if (phone == null) return null;

        phone = phone.replaceAll("[\\s\\-\\.]", "");

        if (phone.startsWith("+"))                            return phone;
        if (phone.startsWith("00226"))                        return "+" + phone.substring(2);
        if (phone.startsWith("226") && phone.length() == 11) return "+" + phone;
        if (phone.matches("^[0-9]{8}$"))                      return "+226" + phone;

        log.warn("[SMS] Format de numéro non reconnu : {}", phone);
        return phone;
    }
}