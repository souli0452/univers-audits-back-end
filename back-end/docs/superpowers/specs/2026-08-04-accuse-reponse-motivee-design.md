# Accusé de réception / Réponse motivée (Lot 2, sous-chantier 4/4) — Design

Statut : approuvé par l'utilisateur le 2026-08-04. Périmètre : quatrième et dernier
sous-chantier du Lot 2 (Étude d'opportunité et priorisation), §11 du plan de travail
(`docs/reference/plan-de-travail-asce-lc.md`). Les sous-chantiers 1/4 (EtudeOpportunite),
2/4 (SeanceCTADP) et 3/4 (DecisionCGE) sont livrés et fusionnés — ce chantier clôt le Lot 2.

## Contexte

Texte exact du Lot 2 (§11) : « Génération de l'accusé de réception / suites à donner ou de
la réponse motivée. » Le modèle de domaine (§5) les nomme comme deux documents **distincts** :
`Recepisse` — `AccuseReceptionSuitesADonner` — `ReponseMotiveeDeRejet`. Délai (§7) : « Accusé
de réception / suites à donner : 3 jours après séance CTADP » ; « Réponse motivée en cas de
rejet : 3 jours ouvrables ». Machine à états (§6) : `CLASSEE (réponse motivée au plaignant)`
est la seule issue explicitement associée à une réponse motivée — les 3 autres issues
(`VALIDEE_POUR_INVESTIGATION`, `TRANSMISE_INSTITUTION_PARTENAIRE`, `ORIENTEE_ADMINISTRATIF`)
relèvent de l'accusé de réception avec suites à donner.

**Contrainte explicite du document source** (§9 et §14) : les maquettes exactes de ces deux
documents (annexe B5) sont marquées « EN ATTENTE ANNEXES » — non disponibles à ce jour. C'est
la même situation que pour le récépissé (annexe B4), déjà livré sans la maquette exacte, avec
un format générique aux couleurs institutionnelles ASCE-LC
(`docs/superpowers/specs/2026-07-30-recepisse-et-upload-securise-design.md`). Ce chantier
suit le même précédent, pas d'invention de maquette non vérifiée.

L'entité `DecisionCGE` (sous-chantier 3/4) porte déjà toute la donnée structurée nécessaire :
`decision` (`RecommandationCtadp` : `VALIDATION_INVESTIGATION`/`CLASSEMENT`/
`TRANSMISSION_INSTITUTION_PARTENAIRE`/`ORIENTATION_ADMINISTRATIVE`), `motif`, `dateDecision`,
`agentCGE`. Déjà exposée dans `DossierResponse.decisionCGE` (masquée sur le canal public de
suivi par code d'accès, cf. sous-chantier 3/4).

## Décision

Deux documents distincts, deux méthodes de service, deux endpoints — pas un document
adaptatif unique (choix initial révisé par l'utilisateur, pour coller au vocabulaire exact du
§5).

### 1. `PdfExportService.exportAccuseReception(UUID dossierId)`

- Récupère le dossier via `dossierService.findById(dossierId)` (même garde d'accès/
  confidentialité que `exportRecepisse`/`exportDossier`).
- Rejette (`BusinessException`) si `dossier.getDecisionCGE() == null` (aucune décision rendue).
- Rejette si `decisionCGE.getDecision()` n'est **pas** `VALIDATION_INVESTIGATION`,
  `TRANSMISSION_INSTITUTION_PARTENAIRE` ou `ORIENTATION_ADMINISTRATIVE` (mauvais type de
  document pour cette décision — le déposant doit recevoir la réponse motivée, pas l'accusé).
- Contenu : en-tête institutionnel (identique au récépissé), numéro de dossier, date de la
  décision, libellé de la décision (réutilise/étend `getStatusLabel`), motif s'il est
  renseigné, mention des suites à donner, pied de page institutionnel (identique au récépissé).

### 2. `PdfExportService.exportReponseMotivee(UUID dossierId)`

- Mêmes gardes d'accès et de décision rendue.
- Rejette si `decisionCGE.getDecision()` n'est **pas** `CLASSEMENT`.
- Contenu : en-tête institutionnel, numéro de dossier, date de la décision, motif mis en
  avant (c'est le cœur de ce document — la justification du classement sans suite),
  formule de clôture, pied de page institutionnel.

### 3. Endpoints

- `GET /api/v1/pdf/accuse-reception/{id}` — `@PreAuthorize("isAuthenticated()")`, même garde
  que `/pdf/recepisse/{id}`/`/pdf/dossier/{id}` (le contrôle fin est déjà fait par
  `dossierService.findById` en amont).
- `GET /api/v1/pdf/reponse-motivee/{id}` — même garde.

### 4. Effet de bord assumé sur `PdfExportService.getStatusLabel`

`ORIENTEE_ADMINISTRATIF` (ajouté au sous-chantier 3/4) n'a pas de libellé dédié dans
`getStatusLabel` — trouvé lors de la revue finale du 3/4, noté pour être corrigé ici puisque ce
fichier est de toute façon modifié par ce chantier. Ajout de `case "ORIENTEE_ADMINISTRATIF" ->
"Orienté (autorité hiérarchique)"`.

## Hors périmètre

- Maquette exacte de l'annexe B5 — non disponible (voir Contexte), même traitement que le
  récépissé.
- Envoi automatique de ces documents (email/SMS) — génération à la demande uniquement, comme
  le récépissé et la fiche dossier existants.
- Délai automatisé « 3 jours après séance CTADP » / « 3 jours ouvrables » (§7) — aucun
  mécanisme de compteur de délai n'existe pour cette étape précise ; génération manuelle à la
  demande de l'agent, pas de déclenchement automatique.

## Tests

- `PdfExportServiceTest` : `exportAccuseReception` — succès pour chacune des 3 décisions
  éligibles, rejet si `decisionCGE == null`, rejet si `decision == CLASSEMENT` (mauvais
  document). `exportReponseMotivee` — succès si `decision == CLASSEMENT`, rejet si
  `decisionCGE == null`, rejet si une autre décision (mauvais document). Vérification de
  contenu (comme les tests récépissé existants) : le motif apparaît dans le PDF généré pour
  `exportReponseMotivee`.
