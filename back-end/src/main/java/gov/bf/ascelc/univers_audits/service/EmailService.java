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

    @Value("${app.portal-url:http://localhost:4200}")
    private String portalUrl;


    @Async
    public void sendAccessCode(String toEmail,
                               String accessCode,
                               String declarantName) {
        if (toEmail == null || toEmail.isBlank()) return;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress, "ASCE-LC Intégrité+");
            helper.setTo(toEmail);
            helper.setSubject("Votre dossier a été enregistré — ASCE-LC INTÉGRITÉ+");
            helper.setText(buildAccessCodeHtml(accessCode, declarantName), true);

            mailSender.send(message);
            log.info("[Email] Confirmation envoyée à {} — code {}", toEmail, accessCode);

        } catch (Exception e) {
            log.error("[Email] Échec confirmation à {} : {}", toEmail, e.getMessage(), e);
        }
    }

    @Async
    public void sendStatusUpdate(String toEmail,
                                 String accessCode,
                                 String declarantName,
                                 String statusLabel,
                                 String statusDescription,
                                 String note) {
        if (toEmail == null || toEmail.isBlank()) return;
        try {
            MimeMessage mail = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(mail, true, "UTF-8");

            helper.setFrom(fromAddress, "ASCE-LC Intégrité+");
            helper.setTo(toEmail);
            helper.setSubject("Mise à jour de votre dossier — " + statusLabel);
            helper.setText(
                    buildStatusHtml(accessCode, declarantName,
                            statusLabel, statusDescription, note),
                    true);

            mailSender.send(mail);
            log.info("[Email] Statut '{}' envoyé à {}", statusLabel, toEmail);

        } catch (Exception e) {
            log.error("[Email] Échec statut à {} : {}", toEmail, e.getMessage());
        }
    }


    @Async
    public void sendComplementRequest(String toEmail,
                                      String accessCode,
                                      String declarantName,
                                      String motif) {
        if (toEmail == null || toEmail.isBlank()) return;
        try {
            MimeMessage mail = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(mail, true, "UTF-8");

            helper.setFrom(fromAddress, "ASCE-LC Intégrité+");
            helper.setTo(toEmail);
            helper.setSubject("Information complémentaire requise — votre dossier");
            helper.setText(
                    buildComplementHtml(accessCode, declarantName, motif),
                    true);

            mailSender.send(mail);
            log.info("[Email] Demande complément envoyée à {}", toEmail);

        } catch (Exception e) {
            log.error("[Email] Échec complément à {} : {}", toEmail, e.getMessage());
        }
    }


    @Async
    public void sendTransferExternal(String toEmail,
                                     String accessCode,
                                     String declarantName,
                                     String institutionLabel) {
        if (toEmail == null || toEmail.isBlank()) return;
        try {
            MimeMessage mail = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(mail, true, "UTF-8");

            helper.setFrom(fromAddress, "ASCE-LC Intégrité+");
            helper.setTo(toEmail);
            helper.setSubject("Votre dossier a été transmis à une institution compétente");
            helper.setText(
                    buildTransferHtml(accessCode, declarantName, institutionLabel),
                    true);

            mailSender.send(mail);
            log.info("[Email] Transfert externe envoyé à {}", toEmail);

        } catch (Exception e) {
            log.error("[Email] Échec transfert à {} : {}", toEmail, e.getMessage());
        }
    }


    @Async
    public void sendInternalAlert(String toEmail,
                                  String agentName,
                                  String alertTitle,
                                  String alertBody) {
        if (toEmail == null || toEmail.isBlank()) return;
        try {
            MimeMessage mail = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(mail, true, "UTF-8");

            helper.setFrom(fromAddress, "ASCE-LC Intégrité+ — Système");
            helper.setTo(toEmail);
            helper.setSubject("[ALERTE] " + alertTitle);
            helper.setText(
                    buildInternalAlertHtml(agentName, alertTitle, alertBody),
                    true);

            mailSender.send(mail);
            log.info("[Email] Alerte interne envoyée à {} — {}", toEmail, alertTitle);

        } catch (Exception e) {
            log.error("[Email] Échec alerte à {} : {}", toEmail, e.getMessage());
        }
    }

    private static final String COMMON_STYLES = """
            <style>
              * { box-sizing: border-box; margin: 0; padding: 0; }
              body { font-family: Arial, Helvetica, sans-serif; background: #f3f4f6; }
              .wrapper { max-width: 580px; margin: 32px auto; background: #fff;
                         border-radius: 14px; overflow: hidden;
                         box-shadow: 0 4px 20px rgba(0,0,0,.1); }
              .header { background: linear-gradient(135deg, #005c2a 0%, #009A44 100%);
                        padding: 32px 28px; text-align: center; }
              .header h1 { color: #fff; font-size: 20px; font-weight: 900;
                           letter-spacing: 1px; text-transform: uppercase; margin: 0; }
              .header p { color: #a7f3d0; font-size: 12px; margin: 6px 0 0; }
              .body { padding: 32px 28px; background: #fff; }
              .body p { color: #374151; font-size: 14px; line-height: 1.7; margin-bottom: 14px; }
              .status-block { border-left: 4px solid #009A44; padding: 16px 20px;
                              background: #f0fdf4; border-radius: 0 10px 10px 0;
                              margin: 20px 0; }
              .status-label { font-size: 10px; font-weight: 700; color: #009A44;
                              text-transform: uppercase; letter-spacing: 2px; margin-bottom: 6px; }
              .status-title { font-size: 18px; font-weight: 700; color: #111827; margin-bottom: 8px; }
              .status-desc { font-size: 13px; color: #6b7280; line-height: 1.6; }
              .code-block { background: #0f1117; border-radius: 12px;
                            padding: 24px; text-align: center; margin: 24px 0; }
              .code-label { color: #9ca3af; font-size: 10px; text-transform: uppercase;
                            letter-spacing: 2px; margin-bottom: 10px; }
              .code-value { color: #FFD700; font-family: monospace; font-size: 34px;
                            font-weight: 900; letter-spacing: 6px; }
              .code-hint { color: #6b7280; font-size: 12px; margin-top: 10px; }
              .note-block { background: #fffbeb; border: 1px solid #fcd34d;
                            border-radius: 10px; padding: 14px 18px; margin: 20px 0;
                            font-size: 13px; color: #78350f; line-height: 1.6; }
              .warn-block { background: #fff7ed; border: 1px solid #fdba74;
                            border-radius: 10px; padding: 14px 18px; margin: 20px 0;
                            font-size: 13px; color: #7c2d12; line-height: 1.6; }
              .danger-block { background: #fef2f2; border: 1px solid #fca5a5;
                              border-radius: 10px; padding: 14px 18px; margin: 20px 0;
                              font-size: 13px; color: #7f1d1d; line-height: 1.6; }
              .cta { text-align: center; margin: 28px 0; }
              .btn { display: inline-block; background: #009A44; color: #fff;
                     text-decoration: none; padding: 14px 36px; border-radius: 10px;
                     font-weight: 700; font-size: 15px; }
              .confidential { background: #f0fdf4; border: 1px solid #86efac;
                              border-radius: 10px; padding: 12px 16px; margin: 20px 0;
                              font-size: 12px; color: #166534; text-align: center; }
              .footer { background: #f9fafb; padding: 18px 28px; text-align: center;
                        border-top: 1px solid #e5e7eb; }
              .footer p { font-size: 11px; color: #9ca3af; line-height: 1.7; }
              .footer a { color: #9ca3af; }
              .flag { display: inline-flex; gap: 4px; margin-top: 8px; }
              .flag span { width: 24px; height: 12px; border-radius: 2px; display: inline-block; }
            </style>
            """;


    private String header() {
        return """
                <div class="header">
                  <h1>ASCE-LC — INTÉGRITÉ+</h1>
                  <p>Autorité Supérieure de Contrôle d'État et de Lutte contre la Corruption</p>
                  <div class="flag">
                    <span style="background:#EF2B2D;"></span>
                    <span style="background:#FFD700; height:12px; width:12px;
                                 border-radius:50%; display:inline-block;
                                 position:relative; top:-1px;"></span>
                    <span style="background:#009A44;"></span>
                  </div>
                </div>
                """;
    }

    private String footer() {
        return """
                <div class="footer">
                  <p>
                    Ce message est envoyé automatiquement — ne pas répondre directement.<br>
                    <strong>ASCE-LC</strong> — 03 BP 7204 Ouagadougou 03, Burkina Faso<br>
                    Numéro Vert : <strong>80 00 11 11</strong> —
                    <a href="mailto:contact@asce-lc.bf">contact@asce-lc.bf</a><br>
                    🔒 Vos informations sont protégées conformément à la Loi N°010-2004/AN
                  </p>
                </div>
                """;
    }


    private String salutation(String declarantName) {
        if (declarantName != null && !declarantName.isBlank()
                && !declarantName.equalsIgnoreCase("Anonyme")) {
            return "<p>Bonjour <strong>" + declarantName + "</strong>,</p>";
        }
        return "<p>Bonjour,</p>";
    }


    private String codeBlock(String accessCode) {
        return """
                <div class="code-block">
                  <div class="code-label">Votre code de suivi</div>
                  <div class="code-value">%s</div>
                  <div class="code-hint">Conservez ce code — il est votre seul identifiant</div>
                </div>
                """.formatted(accessCode);
    }


    private String ctaButton(String accessCode) {
        String url = portalUrl + "/#/portail/suivi?code=" + accessCode;
        return """
                <div class="cta">
                  <a href="%s" class="btn">Suivre mon dossier en ligne</a>
                </div>
                """.formatted(url);
    }

    private static final String CONFIDENTIALITY_NOTICE = """
            <div class="confidential">
              🔒 Toutes vos informations sont traitées de manière strictement confidentielle.
              Votre identité est protégée conformément à la Loi N°010-2004/AN.
            </div>
            """;


    private String buildAccessCodeHtml(String accessCode, String declarantName) {
        return """
                <!DOCTYPE html>
                <html lang="fr">
                <head><meta charset="UTF-8"/><meta name="viewport" content="width=device-width"/>
                %s
                </head>
                <body>
                <div class="wrapper">
                  %s
                  <div class="body">
                    %s
                    <p>
                      Votre dossier a bien été enregistré auprès de l'ASCE-LC.
                      Conservez précieusement le code ci-dessous — il est votre
                      seul identifiant pour suivre l'avancement de votre dossier.
                    </p>

                    %s

                    <div class="note-block">
                      <strong>Important :</strong>
                      Ce code est strictement personnel et confidentiel.
                      Ne le communiquez à personne.
                      Vous pouvez l'utiliser à tout moment sur notre portail
                      pour consulter l'état d'avancement de votre dossier.
                    </div>

                    %s

                    %s
                  </div>
                  %s
                </div>
                </body>
                </html>
                """.formatted(
                COMMON_STYLES,
                header(),
                salutation(declarantName),
                codeBlock(accessCode),
                ctaButton(accessCode),
                CONFIDENTIALITY_NOTICE,
                footer()
        );
    }


    private String buildStatusHtml(String accessCode,
                                   String declarantName,
                                   String statusLabel,
                                   String statusDescription,
                                   String note) {
        String noteBlock = (note != null && !note.isBlank())
                ? "<div class=\"note-block\"><strong>Note de l'ASCE-LC :</strong><br/>"
                + note + "</div>"
                : "";

        return """
                <!DOCTYPE html>
                <html lang="fr">
                <head><meta charset="UTF-8"/><meta name="viewport" content="width=device-width"/>
                %s
                </head>
                <body>
                <div class="wrapper">
                  %s
                  <div class="body">
                    %s
                    <p>
                      Une mise à jour a été effectuée sur <strong>votre dossier</strong>.
                    </p>

                    <div class="status-block">
                      <div class="status-label">Nouveau statut</div>
                      <div class="status-title">%s</div>
                      <div class="status-desc">%s</div>
                    </div>

                    %s

                    %s

                    %s

                    %s
                  </div>
                  %s
                </div>
                </body>
                </html>
                """.formatted(
                COMMON_STYLES,
                header(),
                salutation(declarantName),
                statusLabel,
                statusDescription != null ? statusDescription : "",
                noteBlock,
                codeBlock(accessCode),
                ctaButton(accessCode),
                CONFIDENTIALITY_NOTICE,
                footer()
        );
    }


    private String buildComplementHtml(String accessCode,
                                       String declarantName,
                                       String motif) {
        return """
                <!DOCTYPE html>
                <html lang="fr">
                <head><meta charset="UTF-8"/><meta name="viewport" content="width=device-width"/>
                %s
                </head>
                <body>
                <div class="wrapper">
                  %s
                  <div class="body">
                    %s
                    <p>
                      Dans le cadre du traitement de <strong>votre dossier</strong>,
                      l'ASCE-LC a besoin d'informations complémentaires.
                    </p>

                    <div class="warn-block">
                      <strong>📋 Informations demandées :</strong><br/><br/>
                      %s
                    </div>

                    <p>
                      Merci de transmettre ces éléments dans les meilleurs délais
                      en vous rendant sur le portail ou en contactant le Bureau
                      de Réception des Plaintes et Dénonciations (BRPD).
                    </p>

                    <div class="note-block">
                      <strong>⏱ Délai :</strong> Veuillez répondre dans un délai raisonnable.
                      Sans réponse de votre part, le traitement de votre dossier
                      pourrait être suspendu.
                    </div>

                    %s

                    %s

                    %s
                  </div>
                  %s
                </div>
                </body>
                </html>
                """.formatted(
                COMMON_STYLES,
                header(),
                salutation(declarantName),
                motif != null ? motif : "",
                codeBlock(accessCode),
                ctaButton(accessCode),
                CONFIDENTIALITY_NOTICE,
                footer()
        );
    }


    private String buildTransferHtml(String accessCode,
                                     String declarantName,
                                     String institutionLabel) {
        return """
                <!DOCTYPE html>
                <html lang="fr">
                <head><meta charset="UTF-8"/><meta name="viewport" content="width=device-width"/>
                %s
                </head>
                <body>
                <div class="wrapper">
                  %s
                  <div class="body">
                    %s
                    <p>
                      Après analyse approfondie, <strong>votre dossier</strong>
                      a été transmis à une institution compétente pour traitement.
                    </p>

                    <div class="status-block" style="border-color:#6366f1;background:#eef2ff;">
                      <div class="status-label" style="color:#6366f1;">Dossier transmis</div>
                      <div class="status-title">Institution compétente saisie</div>
                      <div class="status-desc">
                        Votre dossier a été orienté vers l'institution habilitée
                        à traiter ce type de situation.
                        Vous pouvez les contacter directement pour le suivi.
                      </div>
                    </div>

                    <div class="note-block">
                      <strong>ℹ️ Institution :</strong> %s
                    </div>

                    %s

                    %s
                  </div>
                  %s
                </div>
                </body>
                </html>
                """.formatted(
                COMMON_STYLES,
                header(),
                salutation(declarantName),
                institutionLabel != null ? institutionLabel : "Institution compétente",
                codeBlock(accessCode),
                CONFIDENTIALITY_NOTICE,
                footer()
        );
    }


    private String buildInternalAlertHtml(String agentName,
                                          String alertTitle,
                                          String alertBody) {
        return """
                <!DOCTYPE html>
                <html lang="fr">
                <head><meta charset="UTF-8"/><meta name="viewport" content="width=device-width"/>
                %s
                </head>
                <body>
                <div class="wrapper">
                  <div class="header" style="background:linear-gradient(135deg,#991B1B,#DC2626);">
                    <h1>ASCE-LC — ALERTE SYSTÈME</h1>
                    <p style="color:#fecaca;">Usage interne uniquement</p>
                  </div>
                  <div class="body">
                    <p>Bonjour %s,</p>
                    <p>Une action est requise de votre part.</p>

                    <div class="danger-block">
                      <strong>⚠️ %s</strong><br/><br/>
                      %s
                    </div>

                    <div class="cta">
                      <a href="%s/#/app/dossiers" class="btn"
                         style="background:#991B1B;">
                        Accéder au tableau de bord
                      </a>
                    </div>

                    <p style="font-size:12px;color:#9ca3af;text-align:center;">
                      Ce message est destiné exclusivement aux agents ASCE-LC.
                      Ne pas transférer.
                    </p>
                  </div>
                  %s
                </div>
                </body>
                </html>
                """.formatted(
                COMMON_STYLES,
                agentName != null ? agentName : "Agent",
                alertTitle,
                alertBody,
                portalUrl,
                footer()
        );
    }




    public static String[] getStatusEmailContent(String status) {
        return switch (status) {
            case "RECU" -> new String[]{
                    "Dossier reçu et enregistré",
                    "Votre dossier a été officiellement reçu et enregistré par le BRPD. " +
                            "Un récépissé vous sera remis. L'étude de votre dossier va démarrer."
            };
            case "EN_ETUDE_OPPORTUNITE" -> new String[]{
                    "Dossier en cours d'étude",
                    "Votre dossier est en cours d'étude par le Conseiller Juridique " +
                            "de l'ASCE-LC. Cette étape permet d'évaluer la recevabilité de votre demande."
            };
            case "EN_ATTENTE_COMPLEMENT" -> new String[]{
                    "Complément d'information requis",
                    "L'ASCE-LC a besoin d'informations complémentaires pour traiter " +
                            "votre dossier. Veuillez vous reporter au message ci-dessous."
            };
            case "EN_REVUE_CTADP" -> new String[]{
                    "Dossier soumis au Comité de traitement",
                    "Votre dossier a été soumis au Comité de Traitement et d'Analyse " +
                            "des Dénonciations et Plaintes (CTADP) pour délibération."
            };
            case "RECEVABLE" -> new String[]{
                    "Dossier recevable",
                    "L'ASCE-LC a déclaré votre dossier recevable. " +
                            "Une investigation va être diligentée."
            };
            case "IRRECEVABLE" -> new String[]{
                    "Dossier irrecevable",
                    "Après analyse, votre dossier a été déclaré irrecevable. " +
                            "Une réponse motivée vous sera communiquée dans les 3 jours ouvrables."
            };
            case "EN_INVESTIGATION" -> new String[]{
                    "Investigation en cours",
                    "Une équipe d'investigation a été mandatée pour traiter votre dossier. " +
                            "Cette étape peut durer jusqu'à 90 jours selon la complexité du dossier."
            };
            case "RAPPORT_PRODUIT" -> new String[]{
                    "Rapport d'investigation produit",
                    "L'équipe d'investigation a finalisé son rapport. " +
                            "Celui-ci est en cours de validation par les autorités compétentes."
            };
            case "DECISION_RENDUE" -> new String[]{
                    "Décision rendue",
                    "Une décision officielle a été rendue concernant votre dossier. " +
                            "Vous serez informé des suites données."
            };
            case "CLOS" -> new String[]{
                    "Dossier clôturé",
                    "Votre dossier a été clôturé. " +
                            "Merci pour votre contribution à la lutte contre la corruption."
            };
            case "CLASSE" -> new String[]{
                    "Dossier classé",
                    "Votre dossier a été classé sans suite. " +
                            "Une réponse motivée vous sera communiquée."
            };
            case "TRANSFERE" -> new String[]{
                    "Dossier transmis",
                    "Votre dossier a été transmis à l'institution compétente pour traitement."
            };
            default -> new String[]{
                    "Mise à jour de votre dossier",
                    "Une mise à jour a été effectuée sur votre dossier."
            };
        };
    }
}