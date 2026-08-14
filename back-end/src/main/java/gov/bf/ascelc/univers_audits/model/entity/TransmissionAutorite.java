package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
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
@Table(name = "transmission_autorite")
public class TransmissionAutorite extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false, unique = true)
    private Investigation investigation;

    @Column(name = "autorite_destinataire", nullable = false, length = 300)
    private String autoriteDestinataire;

    @Column(name = "transmitted_at", nullable = false)
    private Instant transmittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transmitted_by_id", nullable = false)
    private Agent transmittedBy;

    @OneToMany(mappedBy = "transmissionAutorite",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    @Builder.Default
    private List<RelanceSuites> relances = new ArrayList<>();
}
