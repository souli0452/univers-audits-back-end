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
@Table(name = "jour_ferie", indexes = {
        @Index(name = "idx_jour_ferie_date",
                columnList = "date", unique = true)
})
public class JourFerie extends AuditEntity {

    @Column(name = "date", nullable = false, unique = true)
    private LocalDate date;

    @Column(name = "libelle", nullable = false, length = 300)
    private String libelle;

    @Column(name = "actif", nullable = false)
    @Builder.Default
    private Boolean actif = true;
}
