--liquibase formatted sql
--changeset dev:012-fix-portal-config-hotline

-- La valeur seedée en 006-create-portal-config.sql ('80 00 11 11') est
-- incorrecte. La vraie valeur, confirmée sur le formulaire papier officiel,
-- est '80 00 11 02'.
UPDATE portal_config
    SET config_value = '80 00 11 02', updated_at = now()
    WHERE config_key = 'hotline_number';
