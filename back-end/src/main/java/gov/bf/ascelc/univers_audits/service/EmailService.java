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

    @Value("${app.frontend.url:http://localhost:4200}")
    private String portalUrl;

    @Async
    public void sendInvestigationAssignment(
            String toEmail,
            String agentName,
            String dossierNumber,
            String dossierObject,
            String roleLabel,
            String linkDossier,
            String linkInvestigation) {

        if (toEmail == null || toEmail.isBlank()) return;
        try {
            MimeMessage mail   = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(mail, true, "UTF-8");

            h.setFrom(fromAddress, "ASCE-LC — Intégrité+");
            h.setTo(toEmail);
            h.setSubject("ASCE-LC — Vous avez été affecté(e) à une investigation : "
                    + dossierNumber);
            h.setText(buildInvestigationAssignmentHtml(
                    agentName, dossierNumber, dossierObject,
                    roleLabel, linkDossier, linkInvestigation), true);

            mailSender.send(mail);
            log.info("[Email] Affectation investigation → {} ({})", toEmail, dossierNumber);

        } catch (Exception e) {
            log.error("[Email] Échec affectation → {} : {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendAccessCode(String toEmail,
                               String accessCode,
                               String declarantName) {
        if (toEmail == null || toEmail.isBlank()) return;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress, "ASCE-LC — Intégrité+");
            helper.setTo(toEmail);
            helper.setSubject("ASCE-LC — Votre dossier a été enregistré");
            helper.setText(buildAccessCodeHtml(accessCode, declarantName), true);

            mailSender.send(message);
            log.info("[Email] Code d'accès envoyé → {} (code={})", toEmail, accessCode);

        } catch (Exception e) {
            log.error("[Email] Échec code accès → {} : {}", toEmail, e.getMessage(), e);
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

            helper.setFrom(fromAddress, "ASCE-LC — Intégrité+");
            helper.setTo(toEmail);
            helper.setSubject("ASCE-LC — Mise à jour de votre dossier : " + statusLabel);
            helper.setText(buildStatusHtml(
                    accessCode, declarantName, statusLabel, statusDescription, note), true);

            mailSender.send(mail);
            log.info("[Email] Statut '{}' → {}", statusLabel, toEmail);

        } catch (Exception e) {
            log.error("[Email] Échec statut → {} : {}", toEmail, e.getMessage());
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

            helper.setFrom(fromAddress, "ASCE-LC — Intégrité+");
            helper.setTo(toEmail);
            helper.setSubject("ASCE-LC — Information complémentaire requise");
            helper.setText(buildComplementHtml(
                    accessCode, declarantName, motif), true);

            mailSender.send(mail);
            log.info("[Email] Demande complément → {}", toEmail);

        } catch (Exception e) {
            log.error("[Email] Échec complément → {} : {}", toEmail, e.getMessage());
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

            helper.setFrom(fromAddress, "ASCE-LC — Intégrité+");
            helper.setTo(toEmail);
            helper.setSubject("ASCE-LC — Votre dossier a été transmis");
            helper.setText(buildTransferHtml(
                    accessCode, declarantName, institutionLabel), true);

            mailSender.send(mail);
            log.info("[Email] Transfert externe → {}", toEmail);

        } catch (Exception e) {
            log.error("[Email] Échec transfert → {} : {}", toEmail, e.getMessage());
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

            helper.setFrom(fromAddress, "ASCE-LC — Système");
            helper.setTo(toEmail);
            helper.setSubject("[ALERTE INTERNE] " + alertTitle);
            helper.setText(buildInternalAlertHtml(
                    agentName, alertTitle, alertBody), true);

            mailSender.send(mail);
            log.info("[Email] Alerte interne → {} — {}", toEmail, alertTitle);

        } catch (Exception e) {
            log.error("[Email] Échec alerte → {} : {}", toEmail, e.getMessage());
        }
    }

    private static final String STYLES = """
        <style>
          *{box-sizing:border-box;margin:0;padding:0}
          body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Arial,
               sans-serif;background:#F1F5F4;color:#1a1a1a}
          .outer{max-width:600px;margin:28px auto;padding:0 12px}
          .card{background:#ffffff;border-radius:16px;overflow:hidden;
                box-shadow:0 2px 16px rgba(0,0,0,.08)}

          /* Header */
          .hdr{background:#00662B;padding:32px 36px;text-align:center}
          .hdr-brand{font-size:11px;font-weight:700;letter-spacing:3px;
                     text-transform:uppercase;color:#A7F3D0;margin-bottom:8px}
          .hdr-title{font-size:22px;font-weight:900;color:#FFFFFF;
                     letter-spacing:.5px;margin:0}
          .hdr-sub{font-size:12px;color:#86EFAC;margin-top:6px;
                   font-style:italic}

          /* Corps */
          .body{padding:36px}
          .body p{font-size:14px;color:#374151;line-height:1.75;
                  margin-bottom:16px}
          .salut{font-size:16px;font-weight:600;color:#111827;
                 margin-bottom:20px}

          /* Blocs info */
          .block{border-radius:12px;padding:20px 24px;margin:20px 0}
          .block-green{background:#F0FDF4;border-left:4px solid #16A34A}
          .block-amber{background:#FFFBEB;border-left:4px solid #D97706}
          .block-red  {background:#FEF2F2;border-left:4px solid #DC2626}
          .block-blue {background:#EFF6FF;border-left:4px solid #2563EB}
          .block-gray {background:#F8FAFC;border-left:4px solid #6B7280}
          .block-label{font-size:10px;font-weight:700;letter-spacing:2px;
                       text-transform:uppercase;color:#6B7280;margin-bottom:6px}
          .block-title{font-size:18px;font-weight:800;color:#111827;
                       margin-bottom:6px}
          .block-text{font-size:13px;color:#4B5563;line-height:1.6}

          /* Badge rôle */
          .role-badge{display:inline-block;background:#DCFCE7;color:#15803D;
                      font-weight:700;font-size:13px;padding:6px 16px;
                      border-radius:20px;border:1.5px solid #86EFAC;
                      margin-bottom:24px}

          /* Code accès */
          .code-wrap{background:#0D1117;border-radius:14px;padding:28px;
                     text-align:center;margin:24px 0}
          .code-label{color:#6B7280;font-size:10px;letter-spacing:3px;
                      text-transform:uppercase;margin-bottom:12px}
          .code-val{color:#FFD700;font-family:'Courier New',monospace;
                    font-size:36px;font-weight:900;letter-spacing:8px}
          .code-hint{color:#6B7280;font-size:11px;margin-top:12px}

          /* Boutons */
          .btn-wrap{text-align:center;margin:24px 0}
          .btn{display:inline-block;padding:14px 32px;border-radius:10px;
               font-weight:700;font-size:14px;text-decoration:none;
               margin:6px 8px}
          .btn-primary{background:#00662B;color:#FFFFFF}
                          
           .btn-secondary{background:#ffffff;color:#00662B;
                              border:2px solid #00662B}
          .btn-danger{background:#DC2626;color:#FFFFFF}

          /* Confidentialité */
          .confid{background:#F0FDF4;border:1px solid #BBF7D0;border-radius:10px;
                  padding:12px 16px;margin:20px 0;text-align:center;
                  font-size:12px;color:#166534}

          /* Séparateur */
          .divider{height:1px;background:#F3F4F6;margin:8px 0}

          /* Footer */
          .footer{background:#F9FAFB;padding:20px 36px;border-top:1px solid #E5E7EB;
                  text-align:center}
          .footer p{font-size:11px;color:#9CA3AF;line-height:1.8}
          .footer a{color:#6B7280;text-decoration:none}
          .footer strong{color:#6B7280}
        </style>
        """;

    // ─── Header commun (sans drapeau) ─────────────────────────────────
    private String header(String subtitle) {
        return """
            <div class="hdr">
              <div class="hdr-brand">Burkina Faso</div>
              <div class="hdr-title">ASCE-LC — INTÉGRITÉ+</div>
              <div class="hdr-sub">%s</div>
            </div>
            """.formatted(subtitle != null ? subtitle : "Autorité Supérieure de Contrôle d'État et de Lutte contre la Corruption");
    }

    // ─── Header rouge pour alertes ─────────────────────────────────────
    private String headerAlert() {
        return """
            <div class="hdr" style="background:#991B1B">
              <div class="hdr-brand">Usage interne uniquement</div>
              <div class="hdr-title">ASCE-LC — Alerte Système</div>
              <div class="hdr-sub">Ce message est destiné exclusivement aux agents ASCE-LC</div>
            </div>
            """;
    }

    // ─── Footer commun ─────────────────────────────────────────────────
    private static final String FOOTER = """
        <div class="footer">
          <p>
            Ce message est généré automatiquement — merci de ne pas y répondre.<br>
            <strong>ASCE-LC</strong> · 03 BP 7204 Ouagadougou 03, Burkina Faso<br>
            Numéro Vert : <strong>80 00 11 11</strong> ·
            <a href="mailto:contact@asce-lc.bf">contact@asce-lc.bf</a><br><br>
            Vos informations sont traitées conformément à la Loi N°010-2004/AN
          </p>
        </div>
        """;

    // ─── Notice de confidentialité ─────────────────────────────────────
    private static final String CONFID = """
        <div class="confid">
          Vos informations sont strictement confidentielles et protégées
          conformément à la Loi N°010-2004/AN du Burkina Faso.
        </div>
        """;

    // ─── Salutation personnalisée ──────────────────────────────────────
    private String salut(String name) {
        if (name != null && !name.isBlank()
                && !name.equalsIgnoreCase("Anonyme")) {
            return "<p class=\"salut\">Bonjour <strong>" + name + "</strong>,</p>";
        }
        return "<p class=\"salut\">Bonjour,</p>";
    }

    // ─── Bloc code d'accès ────────────────────────────────────────────
    private String codeBlock(String code) {
        return """
            <div class="code-wrap">
              <div class="code-label">Votre code de suivi confidentiel</div>
              <div class="code-val">%s</div>
              <div class="code-hint">Conservez ce code — c'est votre seul identifiant de suivi</div>
            </div>
            """.formatted(code);
    }

    // ─── Bouton lien portail ──────────────────────────────────────────
    private String ctaPortail(String accessCode) {
        String url = portalUrl + "/#/portail/suivi?code=" + accessCode;
        return """
            <div class="btn-wrap">
              <a href="%s" class="btn btn-primary">Suivre mon dossier en ligne</a>
            </div>
            """.formatted(url);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TEMPLATE 1 — Affectation investigation (pour agents)
    // ═══════════════════════════════════════════════════════════════════
    private String buildInvestigationAssignmentHtml(
            String agentName, String dossierNumber, String dossierObject,
            String roleLabel, String linkDossier, String linkInvestigation) {
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head><meta charset="UTF-8"/>
            <meta name="viewport" content="width=device-width,initial-scale=1"/>
            %s
            </head>
            <body>
            <div class="outer"><div class="card">
              %s
              <div class="body">
                %s
                <p>
                  Vous avez été <strong>officiellement affecté(e)</strong> à une
                  investigation dans le système ASCE-LC — Intégrité+.
                  Veuillez en prendre connaissance et prendre en charge votre mission.
                </p>

                <div class="block block-green">
                  <div class="block-label">Dossier concerné</div>
                  <div class="block-title">%s</div>
                  <div class="block-text">%s</div>
                </div>

                <div style="margin:20px 0">
                  <span class="role-badge">Votre rôle : %s</span>
                </div>

                <p>
                  Cliquez sur l'un des boutons ci-dessous pour accéder
                  directement à votre espace de travail.
                </p>

                <div class="btn-wrap">
                  <a href="%s"
                         style="display:inline-block;padding:14px 32px;border-radius:10px;
                                font-weight:700;font-size:14px;text-decoration:none;
                                background:#00662B;color:#FFFFFF;margin:6px 8px">
                         Accéder au dossier
                      </a>
                      <a href="%s"
                         style="display:inline-block;padding:14px 32px;border-radius:10px;
                                font-weight:700;font-size:14px;text-decoration:none;
                                background:#ffffff;color:#00662B;border:2px solid #00662B;
                                margin:6px 8px">
                         Voir l'investigation
                      </a>
                </div>

                <div class="block block-amber">
                  <div class="block-label">Rappel confidentialité</div>
                  <div class="block-text">
                    Ce dossier est soumis au secret professionnel et à la
                    confidentialité institutionnelle. Toute divulgation non
                    autorisée est contraire au règlement de l'ASCE-LC et à
                    la législation burkinabè en vigueur.
                  </div>
                </div>
              </div>
              %s
            </div></div>
            </body></html>
            """.formatted(
                STYLES,
                header("Notification d'affectation — Usage interne"),
                salut(agentName),
                dossierNumber,
                dossierObject != null ? dossierObject : "",
                roleLabel,
                linkDossier,
                linkInvestigation,
                FOOTER);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TEMPLATE 2 — Code d'accès (pour déclarants)
    // ═══════════════════════════════════════════════════════════════════
    private String buildAccessCodeHtml(String accessCode, String declarantName) {
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head><meta charset="UTF-8"/>
            <meta name="viewport" content="width=device-width,initial-scale=1"/>
            %s
            </head>
            <body>
            <div class="outer"><div class="card">
              %s
              <div class="body">
                %s
                <p>
                  Votre dossier a bien été enregistré auprès de l'ASCE-LC.
                  Vous trouverez ci-dessous votre <strong>code de suivi confidentiel</strong>
                  qui vous permettra de suivre l'avancement de votre dossier
                  à tout moment depuis notre portail.
                </p>

                %s

                <div class="block block-amber">
                  <div class="block-label">Important</div>
                  <div class="block-text">
                    Ce code est strictement personnel. Ne le communiquez à personne.
                    Il est valable jusqu'à la clôture définitive de votre dossier.
                  </div>
                </div>

                %s

                %s
              </div>
              %s
            </div></div>
            </body></html>
            """.formatted(
                STYLES,
                header("Confirmation d'enregistrement de votre dossier"),
                salut(declarantName),
                codeBlock(accessCode),
                ctaPortail(accessCode),
                CONFID,
                FOOTER);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TEMPLATE 3 — Mise à jour de statut (pour déclarants)
    // ═══════════════════════════════════════════════════════════════════
    private String buildStatusHtml(String accessCode,
                                   String declarantName,
                                   String statusLabel,
                                   String statusDescription,
                                   String note) {
        String noteBlock = (note != null && !note.isBlank())
                ? """
                  <div class="block block-blue">
                    <div class="block-label">Note de l'ASCE-LC</div>
                    <div class="block-text">%s</div>
                  </div>
                  """.formatted(note)
                : "";

        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head><meta charset="UTF-8"/>
            <meta name="viewport" content="width=device-width,initial-scale=1"/>
            %s
            </head>
            <body>
            <div class="outer"><div class="card">
              %s
              <div class="body">
                %s
                <p>
                  L'ASCE-LC vous informe d'une mise à jour concernant
                  <strong>votre dossier</strong>.
                </p>

                <div class="block block-green">
                  <div class="block-label">Nouveau statut</div>
                  <div class="block-title">%s</div>
                  <div class="block-text">%s</div>
                </div>

                %s

                %s

                %s

                %s
              </div>
              %s
            </div></div>
            </body></html>
            """.formatted(
                STYLES,
                header("Mise à jour de votre dossier"),
                salut(declarantName),
                statusLabel,
                statusDescription != null ? statusDescription : "",
                noteBlock,
                codeBlock(accessCode),
                ctaPortail(accessCode),
                CONFID,
                FOOTER);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TEMPLATE 4 — Demande de complément (pour déclarants)
    // ═══════════════════════════════════════════════════════════════════
    private String buildComplementHtml(String accessCode,
                                       String declarantName,
                                       String motif) {
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head><meta charset="UTF-8"/>
            <meta name="viewport" content="width=device-width,initial-scale=1"/>
            %s
            </head>
            <body>
            <div class="outer"><div class="card">
              %s
              <div class="body">
                %s
                <p>
                  Dans le cadre du traitement de <strong>votre dossier</strong>,
                  l'ASCE-LC a besoin d'informations complémentaires avant
                  de poursuivre l'instruction.
                </p>

                <div class="block block-amber">
                  <div class="block-label">Informations demandées</div>
                  <div class="block-text">%s</div>
                </div>

                <p>
                  Vous pouvez transmettre ces éléments directement sur notre portail
                  ou en vous présentant au Bureau de Réception des Plaintes et
                  Dénonciations (BRPD).
                </p>

                <div class="block block-red">
                  <div class="block-label">Délai</div>
                  <div class="block-text">
                    Nous vous remercions de répondre dans un délai de
                    <strong>14 jours</strong>. Sans réponse de votre part,
                    le traitement de votre dossier pourrait être suspendu.
                  </div>
                </div>

                %s

                %s

                %s
              </div>
              %s
            </div></div>
            </body></html>
            """.formatted(
                STYLES,
                header("Complément d'information requis"),
                salut(declarantName),
                motif != null ? motif : "",
                codeBlock(accessCode),
                ctaPortail(accessCode),
                CONFID,
                FOOTER);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TEMPLATE 5 — Transfert externe (pour déclarants)
    // ═══════════════════════════════════════════════════════════════════
    private String buildTransferHtml(String accessCode,
                                     String declarantName,
                                     String institutionLabel) {
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head><meta charset="UTF-8"/>
            <meta name="viewport" content="width=device-width,initial-scale=1"/>
            %s
            </head>
            <body>
            <div class="outer"><div class="card">
              %s
              <div class="body">
                %s
                <p>
                  Après analyse approfondie, <strong>votre dossier</strong>
                  a été transmis à une institution compétente pour traitement.
                </p>

                <div class="block block-blue">
                  <div class="block-label">Dossier transmis à</div>
                  <div class="block-title">%s</div>
                  <div class="block-text">
                    Cette institution est compétente pour traiter votre situation.
                    Vous pouvez la contacter directement pour assurer le suivi.
                  </div>
                </div>

                <div class="block block-gray">
                  <div class="block-label">Information</div>
                  <div class="block-text">
                    L'ASCE-LC continuera d'assurer le suivi de votre dossier
                    en collaboration avec l'institution saisie.
                  </div>
                </div>

                %s

                %s
              </div>
              %s
            </div></div>
            </body></html>
            """.formatted(
                STYLES,
                header("Votre dossier a été transmis"),
                salut(declarantName),
                institutionLabel != null ? institutionLabel : "Institution compétente",
                codeBlock(accessCode),
                CONFID,
                FOOTER);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TEMPLATE 6 — Alerte interne (pour agents)
    // ═══════════════════════════════════════════════════════════════════
    private String buildInternalAlertHtml(String agentName,
                                          String alertTitle,
                                          String alertBody) {
        String dashboardUrl = portalUrl + "/#/app/dossiers";
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head><meta charset="UTF-8"/>
            <meta name="viewport" content="width=device-width,initial-scale=1"/>
            %s
            </head>
            <body>
            <div class="outer"><div class="card">
              %s
              <div class="body">
                %s
                <p>Une action urgente est requise de votre part.</p>

                <div class="block block-red">
                  <div class="block-label">Alerte</div>
                  <div class="block-title">%s</div>
                  <div class="block-text">%s</div>
                </div>

                <div class="btn-wrap">
                <a href="%s"
                 style="display:inline-block;padding:14px 32px;border-radius:10px;
                   font-weight:700;font-size:14px;text-decoration:none;
                  background:#DC2626;color:#FFFFFF;margin:6px 8px">
                  Accéder au tableau de bord
                  </a> </div>

                <p style="font-size:12px;color:#9CA3AF;text-align:center;margin-top:16px">
                  Ce message est à usage exclusivement interne.
                  Ne pas transférer à des personnes non habilitées.
                </p>
              </div>
              %s
            </div></div>
            </body></html>
            """.formatted(
                STYLES,
                headerAlert(),
                salut(agentName),
                alertTitle,
                alertBody,
                dashboardUrl,
                FOOTER);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Contenu des emails selon le statut du dossier
    // ═══════════════════════════════════════════════════════════════════
    public static String[] getStatusEmailContent(String status) {
        return switch (status) {
            case "RECU" -> new String[]{
                    "Dossier reçu et enregistré",
                    "Votre dossier a été officiellement reçu et enregistré par le Bureau "
                            + "de Réception des Plaintes et Dénonciations (BRPD). Un récépissé vous "
                            + "sera remis dans les meilleurs délais. L'étude de votre dossier va démarrer."
            };
            case "EN_ETUDE_OPPORTUNITE" -> new String[]{
                    "Dossier en cours d'analyse",
                    "Votre dossier est actuellement en cours d'étude par le Conseiller "
                            + "Juridique de l'ASCE-LC. Cette étape permet d'évaluer la recevabilité "
                            + "de votre demande et sa conformité aux critères d'intervention de l'autorité."
            };
            case "EN_ATTENTE_COMPLEMENT" -> new String[]{
                    "Complément d'information requis",
                    "L'ASCE-LC a besoin d'informations complémentaires pour poursuivre "
                            + "le traitement de votre dossier. Vous recevrez un message distinct "
                            + "précisant les éléments demandés."
            };
            case "EN_REVUE_CTADP" -> new String[]{
                    "Dossier soumis au Comité de Traitement",
                    "Votre dossier a été soumis au Comité de Traitement et d'Analyse des "
                            + "Dénonciations et Plaintes (CTADP) pour délibération. Ce comité se "
                            + "réunit de façon hebdomadaire."
            };
            case "RECEVABLE" -> new String[]{
                    "Dossier déclaré recevable",
                    "L'ASCE-LC a déclaré votre dossier recevable. Une équipe d'investigation "
                            + "sera mandatée pour mener les vérifications nécessaires. Vous serez "
                            + "informé(e) des suites données."
            };
            case "IRRECEVABLE" -> new String[]{
                    "Dossier déclaré irrecevable",
                    "Après analyse approfondie, votre dossier a été déclaré irrecevable. "
                            + "Une réponse motivée vous sera communiquée dans un délai de "
                            + "3 jours ouvrables conformément au Manuel des Procédures de l'ASCE-LC."
            };
            case "EN_INVESTIGATION" -> new String[]{
                    "Investigation en cours",
                    "Une équipe d'investigation spécialisée a été mandatée pour traiter "
                            + "votre dossier. Cette étape peut durer jusqu'à 90 jours en fonction "
                            + "de la complexité des vérifications à effectuer."
            };
            case "RAPPORT_PRODUIT" -> new String[]{
                    "Rapport d'investigation finalisé",
                    "L'équipe d'investigation a finalisé son rapport. Celui-ci est "
                            + "actuellement en cours de validation par les autorités compétentes "
                            + "selon le circuit d'approbation prévu."
            };
            case "DECISION_RENDUE" -> new String[]{
                    "Décision officielle rendue",
                    "Une décision officielle a été rendue par le CGE concernant votre dossier. "
                            + "Les autorités compétentes ont été saisies pour les suites à donner. "
                            + "Vous serez informé(e) des mesures prises."
            };
            case "CLOS" -> new String[]{
                    "Dossier clôturé",
                    "Votre dossier a été officiellement clôturé. L'ASCE-LC vous remercie "
                            + "pour votre contribution à la lutte contre la corruption et pour "
                            + "la bonne gouvernance au Burkina Faso."
            };
            case "CLASSE" -> new String[]{
                    "Dossier classé sans suite",
                    "Votre dossier a été classé sans suite. Une réponse motivée vous "
                            + "sera communiquée par le canal approprié conformément aux "
                            + "procédures en vigueur à l'ASCE-LC."
            };
            case "TRANSFERE" -> new String[]{
                    "Dossier transmis à une institution compétente",
                    "Votre dossier a été transmis à l'institution compétente pour "
                            + "traitement. Vous recevrez un message complémentaire avec "
                            + "les coordonnées de cette institution."
            };
            default -> new String[]{
                    "Mise à jour de votre dossier",
                    "Une mise à jour a été effectuée sur votre dossier. "
                            + "Vous pouvez consulter l'état actuel sur notre portail."
            };
        };
    }
}