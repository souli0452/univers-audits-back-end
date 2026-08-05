--liquibase formatted sql
--changeset dev:023-add-procedure-urgence-mesure-conservatoire

CREATE TABLE procedure_urgence (
    id                UUID PRIMARY KEY,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100),
    investigation_id  UUID NOT NULL REFERENCES investigation(id),
    justification     TEXT NOT NULL,
    requested_by_id   UUID NOT NULL REFERENCES agent(id),
    requested_at      TIMESTAMP NOT NULL,
    status            VARCHAR(20) NOT NULL,
    decided_by_id     UUID REFERENCES agent(id),
    decided_at        TIMESTAMP,
    motif_decision    TEXT
);

CREATE INDEX idx_procedure_urgence_investigation
    ON procedure_urgence (investigation_id);

CREATE TABLE mesure_conservatoire (
    id                UUID PRIMARY KEY,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100),
    investigation_id  UUID NOT NULL REFERENCES investigation(id),
    description       TEXT NOT NULL,
    taken_by_id       UUID NOT NULL REFERENCES agent(id),
    taken_at          TIMESTAMP NOT NULL
);

CREATE INDEX idx_mesure_conservatoire_investigation
    ON mesure_conservatoire (investigation_id);

COMMENT ON TABLE procedure_urgence IS 'Demande DEI (=CGEA) + decision CGE de declenchement d une procedure d urgence sur une investigation - evenement trace, aucun effet sur le statut de l investigation ou du dossier (Lot 3, plan de travail S5/S6/S11)';
COMMENT ON TABLE mesure_conservatoire IS 'Mesure conservatoire concrete prise dans le cadre d une procedure d urgence approuvee (Lot 3, plan de travail S5/S11)';
