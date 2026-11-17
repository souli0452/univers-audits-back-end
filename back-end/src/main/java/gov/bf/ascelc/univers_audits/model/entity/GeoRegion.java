package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.enums.RegionOrigine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

/** Région du Burkina Faso (17 depuis le découpage de juillet 2025). Référentiel en lecture seule. */
@Entity
@Immutable
@Table(name = "geo_region")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoRegion {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, unique = true, length = 80)
    private String libelle;

    /** Zone dans laquelle la région est comptée pour la publication : Kadiogo, Guiriko ou autres régions. */
    @Enumerated(EnumType.STRING)
    @Column(name = "zone_publication", nullable = false, length = 20)
    private RegionOrigine zonePublication;
}
