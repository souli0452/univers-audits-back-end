--liquibase formatted sql
--changeset dev:004-seed-roles-permissions

-- Restaure role_definition/permission/role_permission, perdus lors de la
-- consolidation du schema en 001-baseline-schema.sql (2026-09-17) — seule
-- la structure des tables a ete capturee, pas ces donnees de reference
-- (ecran d'administration des roles/permissions).

-- role_definition et permission n'ont pas de DEFAULT au niveau base (entites
-- JPA independantes RoleDefinition/Permission, @Builder.Default ne s'applique
-- qu'a la construction Java, jamais transcrit en DEFAULT SQL par
-- ddl-auto=create) : id/active/visible/created_at/updated_at/version doivent
-- etre fournis explicitement ici, contrairement a la migration d'origine
-- (004-create-role-permission-tables.sql, supprimee) qui s'appuyait sur des
-- DEFAULT declares a la main dans son propre CREATE TABLE.

INSERT INTO role_definition
    (id, role_key, label, description, icon, severity, display_order,
     visible, active, is_protected, created_at, updated_at, version)
VALUES
    (gen_random_uuid(), 'ADMIN_DDIC',
     'Administrateur DDIC',
     'Administrateur principal du système. Gère les agents, rôles et configurations.',
     'pi pi-cog', 'danger', 1, TRUE, TRUE, TRUE, now(), now(), 0),

    (gen_random_uuid(), 'CGE',
     'Contrôleur Général d''État',
     'Supervise l''ensemble des dossiers et rapports de contrôle.',
     'pi pi-eye', 'warn', 2, TRUE, TRUE, TRUE, now(), now(), 0),

    (gen_random_uuid(), 'CGEA',
     'Assistant CGE',
     'Assiste le Contrôleur Général dans la supervision des dossiers.',
     'pi pi-eye', 'info', 3, TRUE, TRUE, FALSE, now(), now(), 0),

    (gen_random_uuid(), 'AGENT_BRPD',
     'Agent BRPD',
     'Réceptionne et traite les plaintes et dénonciations.',
     'pi pi-inbox', 'success', 4, TRUE, TRUE, FALSE, now(), now(), 0),

    (gen_random_uuid(), 'CONSEILLER_JURIDIQUE',
     'Conseiller Juridique',
     'Fournit l''avis juridique sur les dossiers sensibles.',
     'pi pi-book', 'secondary', 5, TRUE, TRUE, FALSE, now(), now(), 0),

    (gen_random_uuid(), 'CONTROLEUR_ETAT',
     'Contrôleur d''État',
     'Conduit les investigations et rédige les rapports de contrôle.',
     'pi pi-search', 'info', 6, TRUE, TRUE, FALSE, now(), now(), 0),

    (gen_random_uuid(), 'MEMBRE_CTADP',
     'Membre CTADP',
     'Membre du Comité Technique d''Analyse des Dossiers de Plainte.',
     'pi pi-users', 'secondary', 7, TRUE, TRUE, FALSE, now(), now(), 0)

ON CONFLICT (role_key) DO NOTHING;


INSERT INTO permission (id, permission_key, label, category, active, created_at) VALUES

    (gen_random_uuid(), 'VOIR_DOSSIER',          'Consulter les dossiers',             'DOSSIERS', TRUE, now()),
    (gen_random_uuid(), 'CREER_DOSSIER',         'Créer un dossier',                   'DOSSIERS', TRUE, now()),
    (gen_random_uuid(), 'MODIFIER_DOSSIER',      'Modifier un dossier',                'DOSSIERS', TRUE, now()),
    (gen_random_uuid(), 'CHANGER_STATUT_DOSSIER','Changer le statut d''un dossier',    'DOSSIERS', TRUE, now()),
    (gen_random_uuid(), 'SUPPRIMER_DOSSIER',     'Supprimer un dossier',               'DOSSIERS', TRUE, now()),
    (gen_random_uuid(), 'EXPORTER_DOSSIER',      'Exporter un dossier en PDF',         'DOSSIERS', TRUE, now()),

    (gen_random_uuid(), 'VOIR_INVESTIGATION',    'Consulter les investigations',       'INVESTIGATIONS', TRUE, now()),
    (gen_random_uuid(), 'CREER_INVESTIGATION',   'Créer une investigation',            'INVESTIGATIONS', TRUE, now()),
    (gen_random_uuid(), 'MODIFIER_INVESTIGATION','Modifier une investigation',         'INVESTIGATIONS', TRUE, now()),
    (gen_random_uuid(), 'VALIDER_INVESTIGATION', 'Valider une investigation',          'INVESTIGATIONS', TRUE, now()),

    (gen_random_uuid(), 'VOIR_RAPPORT',          'Consulter les rapports',             'RAPPORTS', TRUE, now()),
    (gen_random_uuid(), 'CREER_RAPPORT',         'Rédiger un rapport',                 'RAPPORTS', TRUE, now()),
    (gen_random_uuid(), 'VALIDER_RAPPORT',       'Valider un rapport',                 'RAPPORTS', TRUE, now()),

    (gen_random_uuid(), 'VOIR_AGENT',            'Consulter les agents',               'ADMIN', TRUE, now()),
    (gen_random_uuid(), 'CREER_AGENT',           'Créer un agent',                     'ADMIN', TRUE, now()),
    (gen_random_uuid(), 'MODIFIER_AGENT',        'Modifier un agent',                  'ADMIN', TRUE, now()),
    (gen_random_uuid(), 'DESACTIVER_AGENT',      'Désactiver un agent',                'ADMIN', TRUE, now()),
    (gen_random_uuid(), 'GERER_ROLES',           'Gérer les rôles et permissions',     'ADMIN', TRUE, now()),

    (gen_random_uuid(), 'VOIR_STATISTIQUES',     'Accéder aux statistiques',           'STATS', TRUE, now()),
    (gen_random_uuid(), 'EXPORTER_STATISTIQUES', 'Exporter les statistiques',          'STATS', TRUE, now())
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
