--liquibase formatted sql
--changeset dev:039-retrait-suppleance-ancienne-038

-- Décision du 2026-11-19 : aucune suppléance entre rôles (séparation des pouvoirs). La première version de la 038
-- (« 038-pieces-jointes-telechargement-et-suppleance ») donnait au CGE 14 droits et au CGEA 10 droits de saisie
-- de l'agent BRPD. Elle n'a jamais été déployée en production, mais des bases de développement l'ont jouée : cette
-- migration les remet dans l'état voulu. Sur une base qui ne l'a jamais jouée, elle ne change rien.
--
-- Le CGEA avait déjà quatre de ces droits avant la 038 (migration 028 : mes dossiers, suppression d'une partie
-- visée, d'un témoin, d'une pièce jointe) : ils sont conservés, seuls les dix autres sont retirés.
DELETE FROM role_permission rp
USING role_definition r, permission p
WHERE rp.role_id = r.id AND rp.permission_id = p.id
  AND r.role_key = 'CGE'
  AND p.permission_key IN (
    'DOSSIER_ENREGISTRER', 'DOSSIER_MODIFIER', 'DOSSIER_MES_DOSSIERS_VOIR',
    'PARTIE_VISEE_GERER', 'PARTIE_VISEE_SUPPRIMER', 'TEMOIN_GERER', 'TEMOIN_SUPPRIMER',
    'PIECE_JOINTE_RECLASSER', 'PIECE_JOINTE_SUPPRIMER', 'SECTION_DOSSIER_TRAVAIL_GERER',
    'INFO_PREOCCUPANTE_AUTOSAISIR', 'INFO_PREOCCUPANTE_CREER', 'INFO_PREOCCUPANTE_TRAITER',
    'INFO_PREOCCUPANTE_VOIR');

DELETE FROM role_permission rp
USING role_definition r, permission p
WHERE rp.role_id = r.id AND rp.permission_id = p.id
  AND r.role_key = 'CGEA'
  AND p.permission_key IN (
    'DOSSIER_ENREGISTRER', 'DOSSIER_MODIFIER',
    'PARTIE_VISEE_GERER', 'TEMOIN_GERER',
    'PIECE_JOINTE_RECLASSER', 'SECTION_DOSSIER_TRAVAIL_GERER',
    'INFO_PREOCCUPANTE_AUTOSAISIR', 'INFO_PREOCCUPANTE_CREER', 'INFO_PREOCCUPANTE_TRAITER',
    'INFO_PREOCCUPANTE_VOIR');
