package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "information_preoccupante_dossier", indexes = {
        @Index(name = "idx_ip_dossier_information",
                columnList = "information_preoccupante_id"),
        @Index(name = "idx_ip_dossier_dossier",
                columnList = "dossier_id"),
        @Index(name = "idx_ip_dossier_unique",
                columnList = "information_preoccupante_id, dossier_id", unique = true)
})
public class InformationPreoccupanteDossier extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "information_preoccupante_id", nullable = false)
    private InformationPreoccupante informationPreoccupante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    @Column(name = "commentaire", length = 2000)
    private String commentaire;
}
