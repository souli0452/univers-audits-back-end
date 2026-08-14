--liquibase formatted sql
--changeset dev:033-circuit-validation-renforce

ALTER TABLE investigation ADD COLUMN cgea_approved_at TIMESTAMP;
ALTER TABLE investigation ADD COLUMN cgea_approved_by_id UUID REFERENCES agent(id);

INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'REVUE_CJ_RAPPORT', 'Délai de revue du rapport par le conseiller juridique', 10, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'ANALYSE_DEI_RAPPORT', 'Délai d''analyse du rapport par le DEI', 15, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'APPROBATION_CGEA_RAPPORT', 'Délai d''approbation du rapport par le CGEA', 10, TRUE, TRUE, 0, now());

COMMENT ON COLUMN investigation.cgea_approved_at IS 'Circuit de validation du rapport (Lot 5 sous-chantier 3/4) - 3e etape, entre DEI et CGE';
