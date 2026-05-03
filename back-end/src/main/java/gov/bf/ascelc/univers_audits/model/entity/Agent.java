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
@Table(name = "agent", indexes = {
        @Index(name = "idx_agent_keycloak_id",
                columnList = "keycloak_id", unique = true),
        @Index(name = "idx_agent_email",
                columnList = "email", unique = true),
        @Index(name = "idx_agent_matricule",
                columnList = "matricule", unique = true)
})
public class Agent extends AuditEntity {

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false,
            unique = true, length = 150)
    private String email;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "matricule", nullable = false,
            unique = true, length = 20)
    private String matricule;

    @Column(name = "grade", length = 100)
    private String grade;

    @Column(name = "keycloak_id", unique = true, length = 36)
    private String keycloakId;

    @Column(name = "date_prise_fonction")
    private LocalDate datePriseFonction;

    @Column(name = "actif", nullable = false)
    @Builder.Default
    private Boolean actif = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departement_id")
    private Departement departement;

    public String getNomComplet() {
        return firstName + " " + lastName;
    }
}