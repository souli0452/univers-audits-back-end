--liquibase formatted sql
--changeset dev:027-audition-business-rules

ALTER TABLE audition ADD COLUMN no_show_note TEXT;

CREATE TABLE audition_investigator (
    audition_id UUID NOT NULL REFERENCES audition(id),
    agent_id    UUID NOT NULL REFERENCES agent(id),
    PRIMARY KEY (audition_id, agent_id)
);

INSERT INTO audition_investigator (audition_id, agent_id)
SELECT id, conducted_by_id FROM audition WHERE conducted_by_id IS NOT NULL;

ALTER TABLE audition DROP COLUMN conducted_by_id;
