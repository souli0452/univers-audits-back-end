--liquibase formatted sql
--changeset dev:029-add-attachment-chain-of-custody

ALTER TABLE attachment ADD COLUMN uploaded_by_id UUID REFERENCES agent(id);
ALTER TABLE attachment ADD COLUMN personne_remettante VARCHAR(255);
ALTER TABLE attachment ADD COLUMN mode_obtention VARCHAR(20) NOT NULL DEFAULT 'VOLONTAIRE';
ALTER TABLE attachment ADD COLUMN code VARCHAR(20);

CREATE UNIQUE INDEX idx_attachment_code ON attachment (code) WHERE code IS NOT NULL;

COMMENT ON COLUMN attachment.uploaded_by_id IS 'Auteur du depot (Agent) - null pour un depot citoyen anonyme via accessCode';
COMMENT ON COLUMN attachment.personne_remettante IS 'Personne ayant physiquement remis la piece, distincte de l auteur du depot';
COMMENT ON COLUMN attachment.mode_obtention IS 'Volontaire ou requisition - plan de travail S8.2';
COMMENT ON COLUMN attachment.code IS 'Code auto-genere ACC-<lettre-provenance>-NNNNN, null pour les pieces anterieures a cette migration';
