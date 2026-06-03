CREATE TABLE IF NOT EXISTS portal_config (
    id           UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    config_key   VARCHAR(80) NOT NULL UNIQUE,
    config_value TEXT,
    label        VARCHAR(200) NOT NULL,
    description  VARCHAR(500),

    value_type   VARCHAR(20) NOT NULL DEFAULT 'TEXT'
        CHECK (
            value_type IN (
                'TEXT',
                'IMAGE_URL',
                'COLOR',
                'PHONE',
                'URL',
                'HTML'
            )
        ),

    group_name   VARCHAR(60) NOT NULL DEFAULT 'GENERAL',

    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by   VARCHAR(200)
);

CREATE INDEX IF NOT EXISTS idx_portal_config_key
    ON portal_config(config_key);

CREATE INDEX IF NOT EXISTS idx_portal_config_group
    ON portal_config(group_name);

CREATE OR REPLACE FUNCTION update_portal_config_updated_at()
RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_portal_config_updated_at
ON portal_config;

CREATE TRIGGER trg_portal_config_updated_at
BEFORE UPDATE
ON portal_config
FOR EACH ROW
EXECUTE FUNCTION update_portal_config_updated_at();

INSERT INTO portal_config
(
    config_key,
    config_value,
    label,
    description,
    value_type,
    group_name
)
VALUES


(
    'site_name',
    'INTÉGRITÉ+',
    'Nom du site',
    'Nom affiché dans la navbar et les onglets',
    'TEXT',
    'IDENTITE'
),

(
    'site_tagline',
    'La Patrie ou la Mort, nous vaincrons',
    'Slogan footer',
    'Slogan affiché en bas du footer',
    'TEXT',
    'IDENTITE'
),

(
    'logo_integrite',
    '/assets/logo-integrite.png',
    'Logo principal',
    'Logo INTÉGRITÉ+ (navbar, footer)',
    'IMAGE_URL',
    'IDENTITE'
),

(
    'logo_asce',
    '/assets/logo-asce.png',
    'Logo ASCE-LC',
    'Logo ASCE-LC (footer uniquement)',
    'IMAGE_URL',
    'IDENTITE'
),

-- CONTACT
(
    'hotline_number',
    '80 00 11 11',
    'Numéro vert',
    'Numéro affiché dans la barre supérieure et footer',
    'PHONE',
    'CONTACT'
),

(
    'hotline_label',
    'N° VERT',
    'Libellé numéro vert',
    'Texte avant le numéro',
    'TEXT',
    'CONTACT'
),

(
    'email_contact',
    'contact@asce-lc.bf',
    'Email de contact',
    'Email affiché dans le footer',
    'TEXT',
    'CONTACT'
),

(
    'website_url',
    'https://www.asce-lc.bf',
    'Site web officiel',
    'URL du site ASCE-LC',
    'URL',
    'CONTACT'
),

(
    'address',
    'Ouagadougou, Burkina Faso',
    'Adresse',
    'Adresse physique affichée dans le footer',
    'TEXT',
    'CONTACT'
)

ON CONFLICT (config_key)
DO NOTHING;