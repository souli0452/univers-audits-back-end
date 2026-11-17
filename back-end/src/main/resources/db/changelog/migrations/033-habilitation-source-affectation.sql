--liquibase formatted sql
--changeset dev:033-habilitation-source-affectation

-- Nouvelle origine d'habilitation : l'affectation d'un dossier à un département (le chef, puis les agents choisis
-- par lui). Distincte de MANUAL pour pouvoir la retirer à la réaffectation sans toucher aux habilitations accordées
-- à la main ni à celles de l'équipe d'investigation.
ALTER TABLE dossier_habilitation DROP CONSTRAINT dossier_habilitation_source_check;
ALTER TABLE dossier_habilitation ADD CONSTRAINT dossier_habilitation_source_check
    CHECK (source IN ('AGENT_IN_CHARGE', 'INVESTIGATION_TEAM', 'MANUAL', 'AFFECTATION'));
