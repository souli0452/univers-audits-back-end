--liquibase formatted sql
--changeset dev:034-create-requete-parquet

CREATE TABLE requete_parquet (
    id                UUID      PRIMARY KEY,
    investigation_id  UUID      NOT NULL UNIQUE REFERENCES investigation(id),
    contenu           TEXT,
    version           BIGINT    NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100)
);

COMMENT ON TABLE requete_parquet IS 'Requete au Parquet (Lot 5 sous-chantier 4/4) - redigee par le conseiller juridique, uniquement pour une issue JUDICIAL_REFERRAL';
