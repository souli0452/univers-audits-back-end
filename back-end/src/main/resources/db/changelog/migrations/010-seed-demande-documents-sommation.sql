--liquibase formatted sql
--changeset dev:010-seed-demande-documents-sommation

-- Corrige un bug preexistant, sans rapport avec ce changeset : le code
-- DEMANDE_DOCUMENTS_SOMMATION est reference par
-- DemandeDocumentsServiceImpl.delaiCodeFor(EscalationLevel.SOMMATION) mais
-- n'a jamais ete seede (ni dans les migrations d'origine, ni dans la
-- restauration 003-seed-parametre-delai.sql). Escalader une demande de
-- documents au niveau SOMMATION levait ResourceNotFoundException :
-- "introuvable ou inactif" (404 brut).
--
-- Valeur en jours volontairement NULL, comme ses voisins
-- DEMANDE_DOCUMENTS_INITIAL/RELANCE : le nombre de jours pour ce palier
-- n'a jamais ete arbitre par le metier. Corrige : la ligne existe desormais
-- (resolveJoursOuvrables ne leve plus "code introuvable"), mais
-- resolveDelaiJours continue de lever une exception explicite ("valeur non
-- configuree") jusqu'a ce que le metier fixe ce delai — pas un delai
-- invente ici.
INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'DEMANDE_DOCUMENTS_SOMMATION', 'Délai de réponse après sommation', NULL, TRUE, TRUE, 0, now());
