--liquibase formatted sql
--changeset dev:019-team-role-rename-and-mandat

ALTER TABLE investigation_member ALTER COLUMN team_role TYPE VARCHAR(20);

ALTER TABLE investigation_member
DROP CONSTRAINT IF EXISTS investigation_member_team_role_check;

UPDATE investigation_member SET team_role = 'CHEF_MISSION' WHERE team_role = 'TEAM_LEADER';
UPDATE investigation_member SET team_role = 'INVESTIGATEUR' WHERE team_role = 'MEMBER';

ALTER TABLE investigation_member
ADD CONSTRAINT investigation_member_team_role_check
CHECK (team_role IN (
    'CHEF_MISSION',
    'INVESTIGATEUR',
    'CONSEIL_JURIDIQUE',
    'PERSONNE_RESSOURCE'
));

CREATE TABLE mandat (
    id                UUID PRIMARY KEY,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100),
    investigation_id  UUID NOT NULL UNIQUE REFERENCES investigation(id),
    date_delivrance   TIMESTAMP NOT NULL,
    agent_cge_id      UUID NOT NULL REFERENCES agent(id)
);

COMMENT ON TABLE mandat IS 'Mandat delivre par le CGE avant le demarrage effectif d une investigation (Lot 3, plan de travail S11) - un par investigation, prealable obligatoire a start()';
