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

/** Province, rattachée à une région. Référentiel en lecture seule. */
@Entity
@Immutable
@Table(name = "geo_province")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoProvince {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private GeoRegion region;

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 80)
    private String libelle;
}
