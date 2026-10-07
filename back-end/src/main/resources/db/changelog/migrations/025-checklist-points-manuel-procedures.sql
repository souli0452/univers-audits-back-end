--liquibase formatted sql
--changeset dev:025-checklist-points-manuel-procedures

-- Remplace les 22 libellés provisoires (migration 007) par ceux de la « Liste de vérification du dossier
-- de travail » du Manuel des procédures de gestion des dénonciations et des plaintes (ASCE-LC, processus C,
-- section C.3). Seuls les points encore PROVISOIRES sont modifiés : un libellé déjà corrigé par un
-- administrateur (Paramètres métier) n'est jamais écrasé.
UPDATE point_checklist_dossier_travail p
SET libelle   = v.libelle,
    categorie = 'MANUEL_PROCEDURES'
FROM (VALUES
    ('PT-01', 'Rencontre de l''ensemble des interlocuteurs témoins concernés'),
    ('PT-02', 'Documentation écrite des soupçons'),
    ('PT-03', 'L''ensemble des scénarii d''infractions possibles a-t-il été envisagé ?'),
    ('PT-04', 'A-t-on formalisé une théorie de l''infraction ?'),
    ('PT-05', 'Une réflexion commune sur le périmètre d''analyse et la forme du rapport a-t-elle été menée dans l''équipe et avec le conseiller juridique ?'),
    ('PT-06', 'L''ensemble des travaux prévus dans le plan de travail a-t-il été accompli ?'),
    ('PT-07', 'L''ensemble de la documentation pertinente a-t-il été obtenu et conservé dans le dossier de travail ?'),
    ('PT-08', 'Les pertes potentielles ont-elles été chiffrées avec suffisamment de précision ? L''ensemble des éléments de calculs est-il facilement disponible dans le dossier de travail ?'),
    ('PT-09', 'A-t-on identifié l''ensemble des témoins potentiels ?'),
    ('PT-10', 'A-t-on établi une cartographie des risques ?'),
    ('PT-11', 'A-t-on formalisé un programme de travail adapté au mandat ?'),
    ('PT-12', 'A-t-on obtenu des preuves de l''élément intentionnel ?'),
    ('PT-13', 'A-t-on identifié toutes les personnes à auditionner ?'),
    ('PT-14', 'A-t-on développé une stratégie d''audition ?'),
    ('PT-15', 'L''ensemble des auditions a-t-il été retranscrit dans des PV selon les formes prescrites ?'),
    ('PT-16', 'A-t-on le cas échéant conservé une copie de ces PV signés par les auditionnés ?'),
    ('PT-17', 'Le rapport écrit contient-il une description du contexte et un rappel du mandat du plan d''investigation ?'),
    ('PT-18', 'Le rapport écrit indique-t-il l''ensemble des limitations au travail d''investigation et des points non inclus dans le périmètre d''analyse ?'),
    ('PT-19', 'Le rapport écrit contient-il une note de synthèse des points clés découverts ?'),
    ('PT-20', 'Le rapport écrit contient-il l''ensemble des éléments de preuves pertinentes en annexes ?'),
    ('PT-21', 'Le rapport écrit contient-il les PV signés des auditions et les autres documents obligatoires ?'),
    ('PT-22', 'Le rapport a-t-il subi le contrôle qualité interne ?')
) AS v(code, libelle)
WHERE p.code = v.code AND p.categorie = 'PROVISOIRE';

--rollback UPDATE point_checklist_dossier_travail SET libelle = 'Point de contrôle ' || ordre || ' — contenu à confirmer avec le manuel de procédures ASCE-LC', categorie = 'PROVISOIRE' WHERE categorie = 'MANUEL_PROCEDURES';
