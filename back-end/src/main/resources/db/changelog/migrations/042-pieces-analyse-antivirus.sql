--liquibase formatted sql
--changeset dev:042-pieces-analyse-antivirus

-- Résultat de l'analyse antivirus de chaque pièce jointe. NULL : pièce antérieure à l'analyse (donc non analysée).
ALTER TABLE attachment ADD COLUMN analyse_antivirus VARCHAR(20)
    CONSTRAINT attachment_analyse_antivirus_check CHECK (analyse_antivirus IN ('SAIN', 'NON_ANALYSE'));
