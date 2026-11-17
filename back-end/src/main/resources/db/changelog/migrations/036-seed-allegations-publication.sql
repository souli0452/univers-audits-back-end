--liquibase formatted sql
--changeset dev:036-seed-allegations-publication

-- Les 15 allégations du tableau « Situation des plaintes et dénonciations » publié sur le site. Seules deux
-- étaient en base (migration 006) : sans les autres, tous les dossiers apparaîtraient « non qualifiés ».
-- Les libellés sont ceux du document ; l'administrateur peut les modifier dans Paramètres métier.
-- Aucune ne suppose l'intervention de la DDIP. Insertion idempotente sur le code.

INSERT INTO type_infraction (id, code, libelle, implique_ddip, actif, ordre, version, created_at)
SELECT gen_random_uuid(), v.code, v.libelle, FALSE, TRUE, v.ordre, 0, now()
FROM (VALUES
    ('CONFLIT_INTERET',                   'Conflit d’intérêt',                                   3),
    ('BLANCHIMENT_CAPITAUX',              'Blanchiment de capitaux',                             4),
    ('FAVORITISME',                       'Favoritisme',                                         5),
    ('ABUS_DE_FONCTION',                  'Abus de fonction',                                    6),
    ('DEMANDE_INTERVENTION_DETTES',       'Demande d’intervention pour règlement de dettes',     7),
    ('FRAUDE',                            'Fraude',                                              8),
    ('CONCUSSION',                        'Concussion',                                          9),
    ('DELIT_APPARENCE',                   'Délit d’apparence',                                  10),
    ('CONFLIT_FONCIER',                   'Conflit foncier',                                    11),
    ('CORRUPTION',                        'Corruption',                                         12),
    ('DETOURNEMENT_MALVERSATIONS',        'Détournement et malversations financières',          13),
    ('STELLIONAT',                        'Stellionat',                                         14),
    ('DYSFONCTIONNEMENT',                 'Dysfonctionnement',                                  15),
    ('FAUX_ET_USAGE_DE_FAUX',             'Faux et usage de faux',                              16)
) AS v(code, libelle, ordre)
WHERE NOT EXISTS (SELECT 1 FROM type_infraction t WHERE t.code = v.code OR t.libelle = v.libelle);
