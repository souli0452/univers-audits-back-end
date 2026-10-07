--liquibase formatted sql
--changeset dev:020-dossier-numero-courrier

-- Référence d'enregistrement du courrier dans l'application de gestion du courrier de l'ASCE-LC
-- (étapes 1-2 du workflow PGPD_GU V3). Facultative : l'application démarre au BRPD.
ALTER TABLE dossier ADD COLUMN numero_courrier character varying(50);

--rollback ALTER TABLE dossier DROP COLUMN numero_courrier;
