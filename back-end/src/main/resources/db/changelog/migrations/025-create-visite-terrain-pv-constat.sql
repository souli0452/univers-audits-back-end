--liquibase formatted sql
--changeset dev:025-create-visite-terrain-pv-constat

CREATE TABLE visite_terrain (
    id                   UUID PRIMARY KEY,
    investigation_id     UUID         NOT NULL REFERENCES investigation(id),
    conducted_by_id      UUID         NOT NULL REFERENCES agent(id),
    location             VARCHAR(300) NOT NULL,
    scheduled_at         TIMESTAMP    NOT NULL,
    conducted_at         TIMESTAMP,
    status               VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    summary              TEXT,
    cancellation_reason  TEXT,
    carence_reason       TEXT,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMP    NOT NULL,
    updated_at           TIMESTAMP,
    created_by_id        VARCHAR(100),
    updated_by_id        VARCHAR(100)
);

CREATE INDEX idx_visite_terrain_investigation ON visite_terrain (investigation_id);

CREATE TABLE pv_constat (
    id                UUID PRIMARY KEY,
    visite_terrain_id UUID         NOT NULL REFERENCES visite_terrain(id),
    content           TEXT         NOT NULL,
    drafted_by_id     UUID         NOT NULL REFERENCES agent(id),
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100)
);

CREATE UNIQUE INDEX idx_pv_constat_visite ON pv_constat (visite_terrain_id);
