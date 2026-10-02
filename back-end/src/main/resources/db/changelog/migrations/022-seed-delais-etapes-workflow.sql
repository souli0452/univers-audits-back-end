--liquibase formatted sql
--changeset dev:022-seed-delais-etapes-workflow

-- Délais des étapes du circuit de traitement (workflow PGPD_GU V3), en jours ouvrables :
-- 72 h = 3 jours ouvrables. Modifiables dans « Paramètres métier ».
INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'ETAPE_ANALYSE_CGEA',      'Analyse du dossier par le CGEA et transmission au Conseiller juridique (étape 5)', 3,  TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'ETAPE_CONVOCATION_CTADP', 'Convocation du comité après l''avis juridique (étape 7)',                         3,  TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'ETAPE_QUITUS_CGE',        'Quitus du CGE après la séance du comité (étape 9)',                                15, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'ETAPE_IMPUTATION_CGE',    'Imputation du dossier retenu par le CGE (étape 12)',                              3,  TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'ETAPE_AFFECTATION_CGEA',  'Affectation du dossier par le CGEA (étape 13)',                                   3,  TRUE, TRUE, 0, now())
ON CONFLICT (code) DO NOTHING;

--rollback DELETE FROM parametre_delai WHERE code IN ('ETAPE_ANALYSE_CGEA','ETAPE_CONVOCATION_CTADP','ETAPE_QUITUS_CGE','ETAPE_IMPUTATION_CGE','ETAPE_AFFECTATION_CGEA');
