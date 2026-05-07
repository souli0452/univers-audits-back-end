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

    @Value("${sms.enabled:true}")
    private boolean smsEnabled;

    @Value("${africastalking.username:sandbox}")
    private String username;

    @Value("${africastalking.apiKey:sandbox_key}")
    private String apiKey;

    @Value("${africastalking.senderId:ASCELC}")
    private String senderId;

    @Value("${africastalking.apiUrl:https://api.sandbox.africastalking.com/version1/messaging}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Async
    public void sendAccessCode(String phoneNumber,
                               String accessCode,
                               String dossierNumber) {
        String message = String.format(
                "ASCE-LC INTEGRITE+%n" +
                        "Dossier enregistre.%n" +
                        "N: %s%n" +
                        "Code suivi: %s%n" +
                        "Suivi: integrite.bf/portail/suivi",
                dossierNumber != null ? dossierNumber : "En attente",
                accessCode
        );
        send(phoneNumber, message);
    }

    @Async
    public void sendStatusUpdate(String phoneNumber,
                                 String dossierNumber,
                                 String statusLabel) {
        String message = String.format(
                "ASCE-LC INTEGRITE+%n" +
                        "Dossier %s mis a jour.%n" +
                        "Statut: %s%n" +
                        "Suivi: integrite.bf/portail/suivi",
                dossierNumber,
                statusLabel
        );
        send(phoneNumber, message);
    }

    private void send(String phoneNumber, String message) {
        if (!smsEnabled) {
            log.info("SMS désactivé — destinataire ignoré: {}", phoneNumber);
            return;
        }

        try {
            String normalized = normalizePhone(phoneNumber);
            if (normalized == null || normalized.isBlank()) {
                log.warn("Numéro invalide — SMS non envoyé");
                return;
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
                log.info("SMS envoyé à {} — réponse: {}",
                        normalized, response.getBody());
            } else {
                log.warn("SMS échoué pour {} — statut: {} — réponse: {}",
                        normalized, response.getStatusCode(),
                        response.getBody());
            }

        } catch (Exception e) {
            log.error("Erreur envoi SMS à {} : {}", phoneNumber, e.getMessage());
        }
    }

    private String normalizePhone(String phone) {
        if (phone == null) return null;
        phone = phone.replaceAll("\\s+", "").replaceAll("-", "");

        if (phone.startsWith("+"))                           return phone;
        if (phone.startsWith("00226"))                       return "+" + phone.substring(2);
        if (phone.startsWith("226") && phone.length() == 11) return "+" + phone;
        if (phone.matches("^[0-9]{8}$"))                     return "+226" + phone;

        return phone;
    }
}