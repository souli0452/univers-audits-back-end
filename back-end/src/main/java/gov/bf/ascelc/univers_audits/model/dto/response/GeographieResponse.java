package gov.bf.ascelc.univers_audits.model.dto.response;

import java.util.List;
import java.util.UUID;

/** Référentiel Pays → Région → Province → Commune, en un seul document. Données publiques, sans donnée personnelle. */
public record GeographieResponse(String code, String libelle, List<Region> regions) {

    public record Region(UUID id, String libelle, String zone, List<Province> provinces) {
    }

    public record Province(UUID id, String libelle, List<Commune> communes) {
    }

    public record Commune(UUID id, String libelle, String categorie) {
    }
}
