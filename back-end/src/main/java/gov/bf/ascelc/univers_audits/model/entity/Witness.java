package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;


@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "witness", indexes = {

        @Index(name = "idx_witness_case",
                columnList = "case_id")
})
public class Witness extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private Dossier dossier;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "profession", length = 100)
    private String profession;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "testimony_nature", columnDefinition = "TEXT")
    private String testimonyNature;

    @Column(name = "relation_with_parties", length = 200)
    private String relationWithParties;

    @Column(name = "interrogation_date")
    private LocalDate interrogationDate;


    @Column(name = "consent_to_contact", nullable = false)
    @Builder.Default
    private Boolean consentToContact = false;

    @Column(name = "anonymous", nullable = false)
    @Builder.Default
    private Boolean anonymous = false;


    public boolean hasConsented() {
        return Boolean.TRUE.equals(consentToContact);
    }

    public boolean isAnonymous() {
        return Boolean.TRUE.equals(anonymous);
    }

    public String getDisplayName() {
        if (Boolean.TRUE.equals(anonymous)) return "Témoin anonyme";
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        return "Not provided";
    }
}
