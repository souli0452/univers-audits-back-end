--liquibase formatted sql
--changeset dev:030-create-section-dossier-travail

CREATE TABLE section_dossier_travail (
    id            UUID         PRIMARY KEY,
    dossier_id    UUID         NOT NULL REFERENCES dossier(id),
    type          VARCHAR(35)  NOT NULL,
    libelle       VARCHAR(255),
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP,
    created_by_id VARCHAR(100),
    updated_by_id VARCHAR(100)
);

CREATE INDEX idx_section_dossier ON section_dossier_travail (dossier_id);
CREATE INDEX idx_section_dossier_type ON section_dossier_travail (dossier_id, type);
CREATE UNIQUE INDEX idx_section_dossier_detail_libelle
    ON section_dossier_travail (dossier_id, libelle)
    WHERE type = 'DETAIL';

ALTER TABLE dossier ADD COLUMN organisation_detail VARCHAR(25);

ALTER TABLE attachment ADD COLUMN section_id UUID REFERENCES section_dossier_travail(id);
CREATE INDEX idx_attachment_section ON attachment (section_id);

COMMENT ON TABLE section_dossier_travail IS 'Sections de l arborescence normalisee du dossier de travail (Lot 4 sous-chantier 6/6)';
COMMENT ON COLUMN section_dossier_travail.type IS '3 sections fixes creees automatiquement au demarrage de l investigation (une instance chacune), + type DETAIL cree a la demande (N instances)';
COMMENT ON COLUMN section_dossier_travail.libelle IS 'Libelle libre, renseigne uniquement pour le type DETAIL (ex: nom du site/entite/cycle/etape)';
COMMENT ON COLUMN dossier.organisation_detail IS 'Principe d organisation retenu pour les sections DETAIL de ce dossier (PAR_ETAPE/PAR_ENTITE/PAR_SITE/PAR_CYCLE_COMPTABLE) - defini une seule fois, immuable';
COMMENT ON COLUMN attachment.section_id IS 'Classement optionnel de la piece dans une section du dossier de travail - nullable au depot, modifiable apres coup via PATCH /section';
