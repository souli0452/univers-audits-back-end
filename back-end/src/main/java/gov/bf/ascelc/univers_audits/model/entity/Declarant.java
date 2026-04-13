package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "declarant", indexes = {
        // Index pour accélérer les recherches par email et téléphone
        @Index(name = "idx_declarant_email",
                columnList = "email"),
        @Index(name = "idx_declarant_phone",
                columnList = "phone_number"),
        // Index pour filtrer par type dans les tableaux de bord
        @Index(name = "idx_declarant_type",
                columnList = "type_declarant")
})
public class Declarant extends AuditEntity {

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "organization_name", length = 200)
    private String organizationName;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_declarant", nullable = false, length = 30)
    @Builder.Default
    private TypeDeclarant typeDeclarant = TypeDeclarant.CITIZEN;

    @Column(name = "quality", length = 20)
    private String quality;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "commune", length = 100)
    private String commune;

    @Column(name = "province", length = 100)
    private String province;

    @Column(name = "profession", length = 100)
    private String profession;

    @Column(name = "social_media_account", length = 200)
    private String socialMediaAccount;

    @Column(name = "id_document_number", length = 50)
    private String idDocumentNumber;

    @Column(name = "id_document_type", length = 30)
    private String idDocumentType;


    @Column(name = "anonymous", nullable = false)
    @Builder.Default
    private Boolean anonymous = false;

    @Column(name = "protection_requested", nullable = false)
    @Builder.Default
    private Boolean protectionRequested = false;

    @Column(name = "data_processing_consent", nullable = false)
    @Builder.Default
    private Boolean dataProcessingConsent = false;

    @Column(name = "notifications_accepted", nullable = false)
    @Builder.Default
    private Boolean notificationsAccepted = true;

    @OneToMany(mappedBy = "declarant", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Dossier> cases = new ArrayList<>();


    public String getDisplayName() {
        if (Boolean.TRUE.equals(anonymous)) {
            return "Anonymous";
        }
        if (TypeDeclarant.ASCE_SELF_REFERRAL.equals(typeDeclarant)) {
            return "ASCE-LC (Self-referral)";
        }
        if (organizationName != null && !organizationName.isBlank()) {
            return organizationName;
        }
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        return "Not provided";
    }


    public boolean isAnonymous() {
        return Boolean.TRUE.equals(anonymous)
                || TypeDeclarant.ANONYMOUS.equals(typeDeclarant);
    }


    public boolean isSelfReferral() {
        return TypeDeclarant.ASCE_SELF_REFERRAL.equals(typeDeclarant);
    }
    @PrePersist
    protected void onPrePersist() {
        if (anonymous == null)
            anonymous = false;
        if (protectionRequested == null)
            protectionRequested = false;
        if (dataProcessingConsent == null)
            dataProcessingConsent = false;
        if (notificationsAccepted == null)
            notificationsAccepted = true;
        if (typeDeclarant == null)
            typeDeclarant = TypeDeclarant.CITIZEN;
    }
}
