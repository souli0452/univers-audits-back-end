package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.StatutSeanceCtadp;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "seance_ctadp")
public class SeanceCTADP extends AuditEntity {

    @Column(name = "date_seance", nullable = false)
    private Instant dateSeance;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    @Builder.Default
    private StatutSeanceCtadp statut = StatutSeanceCtadp.PLANIFIEE;

    @Column(name = "participants", length = 2000)
    private String participants;

    @Column(name = "proces_verbal", length = 5000)
    private String procesVerbal;

    @OneToMany(mappedBy = "seanceCtadp",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    @Builder.Default
    private List<SeanceCtadpDossier> dossiers = new ArrayList<>();
}
