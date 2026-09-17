--liquibase formatted sql
--changeset dev:009-create-jour-ferie

CREATE TABLE jour_ferie (
    id             UUID          PRIMARY KEY,
    date           DATE          NOT NULL,
    libelle        VARCHAR(300)  NOT NULL,
    actif          BOOLEAN       NOT NULL DEFAULT TRUE,
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMP     NOT NULL,
    updated_at     TIMESTAMP,
    created_by_id  VARCHAR(100),
    updated_by_id  VARCHAR(100)
);

CREATE UNIQUE INDEX idx_jour_ferie_date ON jour_ferie(date);

COMMENT ON TABLE jour_ferie IS 'Referentiel des jours feries burkinabe, dates fixes saisies annee par annee (pas de recurrence) - consomme par DeadlineCalculator pour le calcul des delais en jours ouvrables (chantier transversal "jours ouvrables reels", sous-chantier 1/4)';
