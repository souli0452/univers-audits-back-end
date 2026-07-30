--liquibase formatted sql
--changeset dev:012-fix-portal-config-hotline

-- La valeur seedée en 006-create-portal-config.sql ('80 00 11 11') est
-- incorrecte. La vraie valeur, confirmée sur le formulaire papier officiel,
-- est '80 00 11 02'.
UPDATE portal_config
    SET config_value = '80 00 11 02', updated_at = now()
    WHERE config_key = 'hotline_number'
      AND config_value = '80 00 11 11';

-- L'adresse et l'URL du site seedées en 006-create-portal-config.sql
-- divergeaient de la source unique gov.bf.ascelc.univers_audits.shared.utils.AsceLcInstitutionalInfo
-- (introduite le 2026-07-30), causant un footer public affichant des
-- coordonnées différentes du récépissé PDF et des emails.
UPDATE portal_config
    SET config_value = '01 BP 617 Ouagadougou 01 BF – Ouaga 2000 – Avenue Pascal ZAGRE',
        updated_at = now()
    WHERE config_key = 'address'
      AND config_value = 'Ouagadougou, Burkina Faso';

UPDATE portal_config
    SET config_value = 'www.asce-lc.bf', updated_at = now()
    WHERE config_key = 'website_url'
      AND config_value = 'https://www.asce-lc.bf';
