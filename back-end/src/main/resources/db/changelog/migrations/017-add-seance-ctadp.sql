--liquibase formatted sql
--changeset dev:017-add-seance-ctadp

CREATE TABLE seance_ctadp (
    id                UUID PRIMARY KEY,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100),
    date_seance       TIMESTAMP NOT NULL,
    statut            VARCHAR(20) NOT NULL DEFAULT 'PLANIFIEE',
    participants      VARCHAR(2000),
    proces_verbal     VARCHAR(5000)
);

CREATE TABLE seance_ctadp_dossier (
    id                UUID PRIMARY KEY,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100),
    seance_ctadp_id   UUID NOT NULL REFERENCES seance_ctadp(id),
    dossier_id        UUID NOT NULL REFERENCES dossier(id),
    recommandation    VARCHAR(40),
    commentaire       VARCHAR(2000),
    CONSTRAINT uk_seance_ctadp_dossier UNIQUE (seance_ctadp_id, dossier_id)
);

CREATE INDEX idx_seance_ctadp_dossier_seance ON seance_ctadp_dossier(seance_ctadp_id);
CREATE INDEX idx_seance_ctadp_dossier_dossier ON seance_ctadp_dossier(dossier_id);

COMMENT ON TABLE seance_ctadp IS 'Seance hebdomadaire du comite CTADP (Lot 2, plan de travail S11/S3) - regroupe plusieurs dossiers examines ensemble';
COMMENT ON TABLE seance_ctadp_dossier IS 'Un dossier a l ordre du jour d une seance, avec sa recommandation (DecisionCTADP) - un dossier peut revenir a une seance ulterieure, jamais deux fois a la meme';
