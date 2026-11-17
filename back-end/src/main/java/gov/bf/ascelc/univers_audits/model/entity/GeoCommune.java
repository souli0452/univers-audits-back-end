package gov.bf.ascelc.univers_audits.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

/** Commune, rattachée à une province : de plein exercice, de moyen exercice ou à statut particulier. */
@Entity
@Immutable
@Table(name = "geo_commune")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoCommune {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "province_id", nullable = false)
    private GeoProvince province;

    @Column(nullable = false, unique = true, length = 120)
    private String code;

    @Column(nullable = false, length = 80)
    private String libelle;

    /** PLEIN, MOYEN ou STATUT_PARTICULIER (Ouagadougou et Bobo-Dioulasso). */
    @Column(nullable = false, length = 20)
    private String categorie;
}
