package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeclarantCreateRequest {

    private TypeDeclarant typeDeclarant;

    private String quality;

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @Size(max = 200)
    private String organizationName;

    @Email(message = "Format email invalide")
    @Size(max = 150)
    private String email;

    @Size(max = 20)
    private String phoneNumber;

    @Size(max = 300)
    private String address;

    @Size(max = 100)
    private String commune;

    @Size(max = 100)
    private String province;

    @Size(max = 100)
    private String profession;

    @Size(max = 200)
    private String socialMediaAccount;

    @Size(max = 50)
    private String idDocumentNumber;

    @Size(max = 30)
    private String idDocumentType;

    private Boolean anonymous;
    private Boolean dataProcessingConsent;
    private Boolean notificationsAccepted;

    /**
     * Le déclarant demande la protection lanceur d'alerte (Loi N°010-2004/AN).
     * Ce champ ne peut être true que si protectionAcknowledged est également true.
     */
    @Builder.Default
    private Boolean protectionRequested = false;

    /**
     * Le déclarant confirme avoir lu et compris :
     *  - les conditions d'éligibilité à la protection
     *  - les conséquences légales d'une demande abusive
     *  - les droits et obligations liés à la Loi N°010-2004/AN
     *
     * Ce champ DOIT être true si protectionRequested est true.
     * Validé côté backend dans DossierServiceImpl.submit().
     */
    @Builder.Default
    private Boolean protectionAcknowledged = false;

    // ── Validation croisée Jakarta ───────────────────────────────────────────
    // Si protectionRequested = true, alors protectionAcknowledged doit être true.
    // Le message est retourné en 400 Bad Request si la contrainte est violée.
    @AssertTrue(
            message = "Vous devez confirmer avoir pris connaissance des conditions "
                    + "de la protection lanceur d'alerte (Loi N°010-2004/AN) "
                    + "avant de pouvoir la demander."
    )
    public boolean isProtectionConsistent() {
        // Si protection non demandée → pas de contrainte
        if (!Boolean.TRUE.equals(protectionRequested)) return true;
        // Si protection demandée → acknowledgment obligatoire
        return Boolean.TRUE.equals(protectionAcknowledged);
    }
}