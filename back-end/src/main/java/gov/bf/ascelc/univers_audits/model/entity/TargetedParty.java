package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.AllegedRole;
import gov.bf.ascelc.univers_audits.enums.PartyType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "targeted_party", indexes = {
        // Recherche de toutes les parties visées d'un dossier
        @Index(name = "idx_targeted_party_case",
                columnList = "case_id")
})
public class TargetedParty extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private Dossier dossier;


    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false, length = 25)
    private PartyType partyType;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "position", length = 150)
    private String position;


    @Column(name = "institution", length = 200)
    private String institution;

    @Column(name = "organization", length = 200)
    private String organization;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "relation_with_declarant", length = 200)
    private String relationWithDeclarant;

    // MAIN_PERPETRATOR, ACCOMPLICE, BENEFICIARY, INSTIGATOR
    @Enumerated(EnumType.STRING)
    @Column(name = "alleged_role", length = 20)
    private AllegedRole allegedRole;

    public String getDisplayName() {
        if (firstName != null && name != null) {
            return firstName + " " + name;
        }
        if (name != null) return name;
        if (organization != null) return organization;
        return "Partie inconnue";
    }
}