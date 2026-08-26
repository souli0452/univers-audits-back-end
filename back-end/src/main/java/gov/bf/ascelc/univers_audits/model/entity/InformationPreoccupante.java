package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.AutoReferralSource;
import gov.bf.ascelc.univers_audits.enums.StatutInformationPreoccupante;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "information_preoccupante")
public class InformationPreoccupante extends AuditEntity {

    @Column(name = "objet", nullable = false, length = 500)
    private String objet;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 40)
    private AutoReferralSource source;

    @Column(name = "source_reference", length = 500)
    private String sourceReference;

    @Column(name = "date_reception", nullable = false)
    private Instant dateReception;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    private StatutInformationPreoccupante statut;
}
