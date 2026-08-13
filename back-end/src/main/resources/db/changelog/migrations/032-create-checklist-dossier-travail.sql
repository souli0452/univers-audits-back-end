--liquibase formatted sql
--changeset dev:032-create-checklist-dossier-travail

CREATE TABLE point_checklist_dossier_travail (
    id            UUID         PRIMARY KEY,
    code          VARCHAR(50)  NOT NULL,
    libelle       VARCHAR(500) NOT NULL,
    categorie     VARCHAR(100),
    ordre         INTEGER      NOT NULL,
    actif         BOOLEAN      NOT NULL DEFAULT TRUE,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP,
    created_by_id VARCHAR(100),
    updated_by_id VARCHAR(100),
    CONSTRAINT uq_point_checklist_code UNIQUE (code)
);

CREATE TABLE checklist_dossier_travail_coche (
    id              UUID      PRIMARY KEY,
    investigation_id UUID     NOT NULL REFERENCES investigation(id),
    point_id        UUID      NOT NULL REFERENCES point_checklist_dossier_travail(id),
    coche           BOOLEAN   NOT NULL DEFAULT FALSE,
    coche_par_id    UUID      REFERENCES agent(id),
    coche_at        TIMESTAMP,
    commentaire     VARCHAR(2000),
    version         BIGINT    NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP,
    created_by_id   VARCHAR(100),
    updated_by_id   VARCHAR(100),
    CONSTRAINT uq_checklist_coche_investigation_point UNIQUE (investigation_id, point_id)
);

CREATE INDEX idx_checklist_coche_investigation
    ON checklist_dossier_travail_coche (investigation_id);

-- Contenu PROVISOIRE : le manuel de procédures ASCE-LC listant les 22 points réels
-- n'est pas disponible dans ce dépôt (voir spec 2026-08-13). Ces libellés doivent être
-- corrigés via PUT /api/v1/points-checklist-dossier-travail/{code} avant tout usage réel.
INSERT INTO point_checklist_dossier_travail (id, code, libelle, categorie, ordre, actif, version, created_at)
SELECT gen_random_uuid(),
       'PT-' || LPAD(n::text, 2, '0'),
       'Point de contrôle ' || n || ' — contenu à confirmer avec le manuel de procédures ASCE-LC',
       'PROVISOIRE',
       n,
       TRUE,
       0,
       now()
FROM generate_series(1, 22) AS n;

COMMENT ON TABLE point_checklist_dossier_travail IS 'Referentiel configurable des points de la check-list du dossier de travail (Lot 5 sous-chantier 2/4) - contenu initial PROVISOIRE, a corriger via l administration avant usage reel';
COMMENT ON TABLE checklist_dossier_travail_coche IS 'Etat de coche par investigation pour chaque point actif du referentiel - bloque submitReport() tant qu un point actif reste non coche';
