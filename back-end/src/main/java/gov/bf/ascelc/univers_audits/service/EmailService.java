package gov.bf.ascelc.univers_audits.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;


    @Value("${spring.mail.username}")
    private String fromAddress;

    @Async
    public void sendAccessCode(String toEmail,
                               String accessCode,
                               String dossierNumber) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress, "ASCE-LC Intégrité+");
            helper.setTo(toEmail);
            helper.setSubject("Votre code de suivi — " + accessCode);
            helper.setText(buildHtml(accessCode, dossierNumber), true);

            mailSender.send(message);
            log.info("Email envoyé à {} — code {}", toEmail, accessCode);

        } catch (Exception e) {
            log.error("Échec envoi email à {} : {}", toEmail, e.getMessage(), e);
        }
    }

    @Async
    public void sendStatusUpdate(String toEmail,
                                 String dossierNumber,
                                 String statusLabel,
                                 String message) {
        try {
            MimeMessage mail = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(mail, true, "UTF-8");

            helper.setFrom(fromAddress, "ASCE-LC Intégrité+");
            helper.setTo(toEmail);
            helper.setSubject("Mise à jour dossier " + dossierNumber
                    + " — " + statusLabel);
            helper.setText(
                    buildStatusHtml(dossierNumber, statusLabel, message),
                    true);

            mailSender.send(mail);
            log.info("Email statut envoyé à {} — dossier {}",
                    toEmail, dossierNumber);

        } catch (Exception e) {
            log.error("Échec email statut à {} : {}", toEmail, e.getMessage());
        }
    }

    // ── Templates HTML ────────────────────────────────────────

    private String buildHtml(String accessCode, String dossierNumber) {
        return """
            <div style="font-family:Arial,sans-serif;max-width:520px;
                        margin:0 auto;border-radius:12px;overflow:hidden;
                        border:1px solid #e5e7eb;">

                <div style="background:linear-gradient(135deg,#16a34a,#22c55e);
                            padding:28px 24px;text-align:center;">
                    <h1 style="color:#fff;margin:0;font-size:22px;
                               font-weight:900;letter-spacing:1px;">
                        ASCE-LC — INTÉGRITÉ+
                    </h1>
                    <p style="color:#dcfce7;margin:6px 0 0;font-size:13px;">
                        Autorité Supérieure de Contrôle d'État et de Lutte
                        contre la Corruption
                    </p>
                </div>

                <div style="background:#f9fafb;padding:28px 24px;">
                    <p style="color:#374151;font-size:15px;margin-top:0;">
                        Bonjour,
                    </p>
                    <p style="color:#374151;font-size:14px;line-height:1.7;">
                        Votre dossier a bien été enregistré auprès de l'ASCE-LC.
                        Conservez précieusement les informations ci-dessous
                        pour suivre l'avancement de votre dossier.
                    </p>

                    <div style="background:#fff;border:2px solid #86efac;
                                border-radius:14px;padding:24px;
                                text-align:center;margin:20px 0;">
                        <div style="font-size:11px;color:#16a34a;font-weight:700;
                                    letter-spacing:2px;text-transform:uppercase;
                                    margin-bottom:6px;">
                            Numéro de dossier
                        </div>
                        <div style="font-size:20px;font-weight:700;
                                    color:#111827;font-family:monospace;
                                    margin-bottom:16px;">
                            %s
                        </div>
                        <div style="width:60px;height:2px;background:#e5e7eb;
                                    margin:0 auto 16px;"></div>
                        <div style="font-size:11px;color:#16a34a;font-weight:700;
                                    letter-spacing:2px;text-transform:uppercase;
                                    margin-bottom:8px;">
                            Code de suivi B4
                        </div>
                        <div style="font-family:monospace;font-size:36px;
                                    font-weight:900;color:#166534;
                                    letter-spacing:8px;">
                            %s
                        </div>
                    </div>

                    <div style="text-align:center;margin:24px 0;">
                        <a href="http://localhost:4200/#/portail/suivi"
                           style="background:#16a34a;color:#fff;
                                  padding:14px 32px;border-radius:10px;
                                  text-decoration:none;font-weight:700;
                                  font-size:14px;display:inline-block;">
                            Suivre mon dossier en ligne
                        </a>
                    </div>

                    <div style="background:#eff6ff;border:1px solid #bfdbfe;
                                border-radius:10px;padding:14px;
                                font-size:13px;color:#1e40af;">
                        <strong>Important :</strong> Notez ce code ou
                        conservez cet email. Il est le seul moyen de
                        suivre votre dossier en ligne.
                    </div>
                </div>

                <div style="background:#fff;padding:16px 24px;
                            border-top:1px solid #e5e7eb;text-align:center;">
                    <p style="color:#9ca3af;font-size:12px;margin:0;">
                        Ce message est envoyé automatiquement — ne pas répondre<br>
                        ASCE-LC — 03 BP 7204 Ouagadougou 03 —
                        Numéro vert : 80 00 11 57
                    </p>
                </div>
            </div>
            """.formatted(
                dossierNumber != null ? dossierNumber : "En attente d'attribution",
                accessCode);
    }

    private String buildStatusHtml(String dossierNumber,
                                   String statusLabel,
                                   String message) {
        return """
            <div style="font-family:Arial,sans-serif;max-width:520px;
                        margin:0 auto;border-radius:12px;overflow:hidden;
                        border:1px solid #e5e7eb;">
                <div style="background:linear-gradient(135deg,#16a34a,#22c55e);
                            padding:24px;text-align:center;">
                    <h1 style="color:#fff;margin:0;font-size:20px;
                               font-weight:900;">
                        ASCE-LC — INTÉGRITÉ+
                    </h1>
                </div>
                <div style="background:#f9fafb;padding:24px;">
                    <p style="color:#374151;font-size:14px;">Bonjour,</p>
                    <p style="color:#374151;font-size:14px;line-height:1.7;">
                        Une mise à jour a été effectuée sur votre dossier
                        <strong>%s</strong>.
                    </p>
                    <div style="background:#fff;border-left:4px solid #16a34a;
                                padding:16px;border-radius:0 10px 10px 0;
                                margin:16px 0;">
                        <div style="font-size:11px;color:#16a34a;font-weight:700;
                                    letter-spacing:1px;text-transform:uppercase;
                                    margin-bottom:6px;">
                            Nouveau statut
                        </div>
                        <div style="font-size:16px;font-weight:700;
                                    color:#111827;margin-bottom:8px;">
                            %s
                        </div>
                        <div style="font-size:13px;color:#6b7280;
                                    line-height:1.6;">
                            %s
                        </div>
                    </div>
                    <div style="text-align:center;margin-top:20px;">
                        <a href="https://integrite.bf/portail/suivi"
                           style="background:#16a34a;color:#fff;
                                  padding:12px 28px;border-radius:10px;
                                  text-decoration:none;font-weight:700;
                                  font-size:14px;display:inline-block;">
                            Voir mon dossier
                        </a>
                    </div>
                </div>
                <div style="background:#fff;padding:14px 24px;
                            border-top:1px solid #e5e7eb;text-align:center;">
                    <p style="color:#9ca3af;font-size:11px;margin:0;">
                        ASCE-LC — 03 BP 7204 Ouagadougou 03 —
                        Numéro vert : 80 00 11 57
                    </p>
                </div>
            </div>
            """.formatted(dossierNumber, statusLabel, message);
    }
}