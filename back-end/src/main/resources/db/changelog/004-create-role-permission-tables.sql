CREATE TABLE IF NOT EXISTS role_definition (
    id              UUID            DEFAULT gen_random_uuid() PRIMARY KEY,
    role_key        VARCHAR(60)     NOT NULL UNIQUE,
    label           VARCHAR(150)    NOT NULL,
    description     VARCHAR(500),
    icon            VARCHAR(60)     NOT NULL DEFAULT 'pi pi-user',
    severity        VARCHAR(20)     NOT NULL DEFAULT 'info'
                        CHECK (severity IN ('success','info','warn','danger','secondary')),
    display_order   INTEGER         NOT NULL DEFAULT 99,
    visible         BOOLEAN         NOT NULL DEFAULT TRUE,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    is_protected    BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);


CREATE TABLE IF NOT EXISTS permission (
    id              UUID            DEFAULT gen_random_uuid() PRIMARY KEY,
    permission_key  VARCHAR(80)     NOT NULL UNIQUE,
    label           VARCHAR(200)    NOT NULL,
    description     VARCHAR(500),
    category        VARCHAR(60),
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);


CREATE TABLE IF NOT EXISTS role_permission (
    role_id         UUID    NOT NULL REFERENCES role_definition(id) ON DELETE CASCADE,
    permission_id   UUID    NOT NULL REFERENCES permission(id)      ON DELETE CASCADE,
    granted_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (role_id, permission_id)
);


CREATE INDEX IF NOT EXISTS idx_role_definition_key     ON role_definition(role_key);
CREATE INDEX IF NOT EXISTS idx_permission_key          ON permission(permission_key);
CREATE INDEX IF NOT EXISTS idx_permission_category     ON permission(category);
CREATE INDEX IF NOT EXISTS idx_role_permission_role    ON role_permission(role_id);
CREATE INDEX IF NOT EXISTS idx_role_permission_perm    ON role_permission(permission_id);


CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_role_definition_updated_at
    BEFORE UPDATE ON role_definition
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();


INSERT INTO role_definition
    (role_key, label, description, icon, severity, display_order, is_protected)
VALUES
    ('ADMIN_DDIC',
     'Administrateur DDIC',
     'Administrateur principal du système. Gère les agents, rôles et configurations.',
     'pi pi-cog', 'danger', 1, TRUE),

    ('CGE',
     'Contrôleur Général d''État',
     'Supervise l''ensemble des dossiers et rapports de contrôle.',
     'pi pi-eye', 'warn', 2, TRUE),

    ('CGEA',
     'Assistant CGE',
     'Assiste le Contrôleur Général dans la supervision des dossiers.',
     'pi pi-eye', 'info', 3, FALSE),

    ('AGENT_BRPD',
     'Agent BRPD',
     'Réceptionne et traite les plaintes et dénonciations.',
     'pi pi-inbox', 'success', 4, FALSE),

    ('CONSEILLER_JURIDIQUE',
     'Conseiller Juridique',
     'Fournit l''avis juridique sur les dossiers sensibles.',
     'pi pi-book', 'secondary', 5, FALSE),

    ('CONTROLEUR_ETAT',
     'Contrôleur d''État',
     'Conduit les investigations et rédige les rapports de contrôle.',
     'pi pi-search', 'info', 6, FALSE),

    ('MEMBRE_CTADP',
     'Membre CTADP',
     'Membre du Comité Technique d''Analyse des Dossiers de Plainte.',
     'pi pi-users', 'secondary', 7, FALSE)

ON CONFLICT (role_key) DO NOTHING;


INSERT INTO permission (permission_key, label, category) VALUES

    ('VOIR_DOSSIER',          'Consulter les dossiers',             'DOSSIERS'),
    ('CREER_DOSSIER',         'Créer un dossier',                   'DOSSIERS'),
    ('MODIFIER_DOSSIER',      'Modifier un dossier',                'DOSSIERS'),
    ('CHANGER_STATUT_DOSSIER','Changer le statut d''un dossier',    'DOSSIERS'),
    ('SUPPRIMER_DOSSIER',     'Supprimer un dossier',               'DOSSIERS'),
    ('EXPORTER_DOSSIER',      'Exporter un dossier en PDF',         'DOSSIERS'),

    ('VOIR_INVESTIGATION',    'Consulter les investigations',       'INVESTIGATIONS'),
    ('CREER_INVESTIGATION',   'Créer une investigation',            'INVESTIGATIONS'),
    ('MODIFIER_INVESTIGATION','Modifier une investigation',         'INVESTIGATIONS'),
    ('VALIDER_INVESTIGATION', 'Valider une investigation',          'INVESTIGATIONS'),

    ('VOIR_RAPPORT',          'Consulter les rapports',             'RAPPORTS'),
    ('CREER_RAPPORT',         'Rédiger un rapport',                 'RAPPORTS'),
    ('VALIDER_RAPPORT',       'Valider un rapport',                 'RAPPORTS'),

    ('VOIR_AGENT',            'Consulter les agents',               'ADMIN'),
    ('CREER_AGENT',           'Créer un agent',                     'ADMIN'),
    ('MODIFIER_AGENT',        'Modifier un agent',                  'ADMIN'),
    ('DESACTIVER_AGENT',      'Désactiver un agent',                'ADMIN'),
    ('GERER_ROLES',           'Gérer les rôles et permissions',     'ADMIN'),

    ('VOIR_STATISTIQUES',     'Accéder aux statistiques',           'STATS'),
    ('EXPORTER_STATISTIQUES', 'Exporter les statistiques',          'STATS')
ON CONFLICT (permission_key) DO NOTHING;


INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role_definition r, permission p
WHERE r.role_key = 'ADMIN_DDIC'
ON CONFLICT DO NOTHING;


INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role_definition r, permission p
WHERE r.role_key = 'CGE'
  AND p.permission_key != 'GERER_ROLES'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role_definition r, permission p
WHERE r.role_key = 'CGEA'
  AND p.permission_key IN (
      'VOIR_DOSSIER','VOIR_INVESTIGATION','VOIR_RAPPORT',
      'VOIR_STATISTIQUES','EXPORTER_DOSSIER','EXPORTER_STATISTIQUES')
ON CONFLICT DO NOTHING;


INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role_definition r, permission p
WHERE r.role_key = 'AGENT_BRPD'
  AND p.permission_key IN (
      'VOIR_DOSSIER','CREER_DOSSIER','MODIFIER_DOSSIER',
      'CHANGER_STATUT_DOSSIER','EXPORTER_DOSSIER',
      'VOIR_INVESTIGATION','VOIR_STATISTIQUES')
ON CONFLICT DO NOTHING;


INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role_definition r, permission p
WHERE r.role_key = 'CONSEILLER_JURIDIQUE'
  AND p.permission_key IN (
      'VOIR_DOSSIER','VOIR_INVESTIGATION','VOIR_RAPPORT',
      'CREER_RAPPORT','VOIR_STATISTIQUES')
ON CONFLICT DO NOTHING;


INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role_definition r, permission p
WHERE r.role_key = 'CONTROLEUR_ETAT'
  AND p.permission_key IN (
      'VOIR_DOSSIER','MODIFIER_DOSSIER','CHANGER_STATUT_DOSSIER','EXPORTER_DOSSIER',
      'VOIR_INVESTIGATION','CREER_INVESTIGATION','MODIFIER_INVESTIGATION','VALIDER_INVESTIGATION',
      'VOIR_RAPPORT','CREER_RAPPORT','VALIDER_RAPPORT',
      'VOIR_STATISTIQUES','EXPORTER_STATISTIQUES')
ON CONFLICT DO NOTHING;


INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role_definition r, permission p
WHERE r.role_key = 'MEMBRE_CTADP'
  AND p.permission_key IN (
      'VOIR_DOSSIER','VOIR_INVESTIGATION','VOIR_RAPPORT','VOIR_STATISTIQUES')
ON CONFLICT DO NOTHING;