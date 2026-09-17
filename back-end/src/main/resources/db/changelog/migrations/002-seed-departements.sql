--liquibase formatted sql
--changeset dev:002-seed-departements

-- Departements reels de l'ASCE-LC, confirmes par l'utilisateur le 2026-09-17
-- (CAEDT = BRPD, meme structure ; CJ/OR et CJ/OO = notation "Conseiller
-- Juridique" + initiales agent, pas des codes de departement). DEI/DAC/DIP
-- (DDIP)/DSRAJ/DCP sont les departements activement utilises par
-- l'application ; DSI/DRH existent au referentiel mais restent inactifs
-- tant qu'aucun flux ne les utilise.

INSERT INTO departement (id, code, libelle, description, actif, ordre_affichage, version, created_at)
VALUES
    (gen_random_uuid(), 'DEI', 'Direction des Enquêtes et Investigations',
     'Valide le plan d''investigation, analyse les rapports, coordonne les enquêteurs.',
     TRUE, 1, 0, now()),
    (gen_random_uuid(), 'DAC', 'DAC',
     'Producteur de saisines internes (faits découverts en audit) et membre d''équipe de mission.',
     TRUE, 2, 0, now()),
    (gen_random_uuid(), 'DDIP', 'Direction des Déclarations d''Intérêts et de Patrimoine',
     'Producteur de saisines internes (soupçons issus des déclarations) et membre d''équipe.',
     TRUE, 3, 0, now()),
    (gen_random_uuid(), 'DSRAJ', 'DSRAJ',
     'Propose la saisine judiciaire, suit les recommandations et les actions en justice.',
     TRUE, 4, 0, now()),
    (gen_random_uuid(), 'DCP', 'Direction de la Communication et de la Presse',
     'Veille presse, alimente le module d''information préoccupante, communication.',
     TRUE, 5, 0, now()),
    (gen_random_uuid(), 'DSI', 'Direction des Systèmes d''Information',
     'Fonction support, non encore rattachée à un flux applicatif.',
     FALSE, 6, 0, now()),
    (gen_random_uuid(), 'DRH', 'Direction des Ressources Humaines',
     'Fonction support, non encore rattachée à un flux applicatif.',
     FALSE, 7, 0, now());
