# Complétion DemandeDocuments (Lot 4, sous-chantier 1/6) — Design

Statut : approuvé par l'utilisateur le 2026-08-05. Premier sous-chantier du Lot 4
(Conduite de l'investigation), §4/§7/§11 du plan de travail
(`docs/reference/plan-de-travail-asce-lc.md`). Audit complet du Lot 4 réalisé le
2026-08-05 (voir mémoire backlog) avant découpage en 6 sous-chantiers :
1. Complétion `DemandeDocuments` (ce document)
2. `VisiteTerrain` + `PVConstat`
3. Règles métier manquantes sur `Audition` (ordre, 2 enquêteurs, seconde audition)
4. Correction PV + `RegistreAuditions`
5. Extension `Attachment` (chaîne de possession, code auto-généré) + index des pièces
6. `DossierDeTravail` structuré (le plus gros morceau)

## Contexte

Texte exact (§4, Lot 4) : « Demandes de documents avec escalade automatique (rappel,
sommation, saisine judiciaire), **gestion des retours pour adresse erronée avec remise
à zéro des délais**. »

Table des délais (§7) : réponse attendue 10 jours ouvrables → correspondance de rappel
+ 5 jours ouvrables → sommation par huissier + 1 jour franc → à défaut : **saisine
judiciaire, art. 72 loi 004-2015, immédiat**.

**Audit du module existant** (déjà construit, jamais couvert par l'audit initial de
cette session) : `create`/`markReceived`/`escalate` (méthode générique unique faisant
progresser `EscalationLevel` ordinal par ordinal) fonctionnent et sont testés.

**Bug confirmé** : `EscalationLevel.SAISINE_JUDICIAIRE` est **inatteignable**. Deux
causes cumulatives : (1) `DemandeDocumentsServiceImpl.delaiCodeFor()` n'a pas de `case
SAISINE_JUDICIAIRE` — tombe sur le `default` qui lève `BusinessException` ; (2) la
migration `008` ne seed que 3 des 4 lignes `parametre_delai` nécessaires
(`DEMANDE_DOCUMENTS_INITIAL`/`_RELANCE`/`_SOMMATION`, pas `_SAISINE_JUDICIAIRE`).
Escalader depuis `SOMMATION` échoue systématiquement aujourd'hui.

**Précédent confirmé sur les valeurs de délai** : les 3 lignes déjà seedées ont toutes
`valeur_jours = NULL` — c'est un référentiel volontairement laissé vide pour
configuration par un administrateur en production (`PUT
/api/v1/parametres-delai/{code}`), cohérent avec §7 (« paramètres administrables ») et
avec le gap déjà noté depuis le Lot 0 (« référentiels quasi vides »). La 4e ligne à
seeder suit exactement le même patron — ce n'est pas un bug à corriger différemment,
juste la ligne manquante à ajouter.

**Absent, à construire** : aucun champ ni logique pour « adresse erronée avec remise à
zéro des délais ». `recipientLabel` est un texte libre (pas une adresse structurée),
aucune action de signalement de retour n'existe.

## Décision

### 1. Rendre `SAISINE_JUDICIAIRE` atteignable

- Migration : ajoute la ligne `parametre_delai` manquante
  (`DEMANDE_DOCUMENTS_SAISINE_JUDICIAIRE`, `valeur_jours = NULL`, même patron que les
  3 autres).
- `delaiCodeFor()` devient exhaustif sur les 4 valeurs de `EscalationLevel` (ajoute
  `INITIAL` et `SAISINE_JUDICIAIRE`, retire le `default`) — un `switch` exhaustif sur
  un enum sans `default` fait échouer la compilation si une 5e valeur est ajoutée un
  jour sans mettre cette méthode à jour, ce qui est le comportement voulu. `create()`
  est mis à jour pour appeler `delaiCodeFor(EscalationLevel.INITIAL)` au lieu de la
  chaîne `"DEMANDE_DOCUMENTS_INITIAL"` codée en dur, éliminant une duplication rendue
  inutile par l'exhaustivité.

### 2. Gestion des retours pour adresse erronée

Nouvelle action `reportAddressError` — un agent signale qu'une demande de documents
envoyée est revenue pour adresse/destinataire erroné, fournit un destinataire corrigé,
et la demande repart avec un délai frais. **Ce n'est pas une escalade** (le
destinataire n'a pas ignoré la demande, elle ne lui est jamais parvenue) : le niveau
d'escalade (`escalationLevel`) reste inchangé, seul `recipientLabel` est mis à jour et
`sentAt`/`deadline` sont recalculés à partir de maintenant, avec le même délai que le
niveau courant (via `delaiCodeFor(escalationLevel)`, désormais exhaustif).

- `POST /api/v1/investigations/{investigationId}/demandes-documents/{id}/adresse-erronee`
  — réservé aux mêmes rôles que les autres actions d'écriture de ce module
  (`CONTROLEUR_ETAT, CGEA, ADMIN_DDIC`). Corps : nouveau destinataire (obligatoire).
  Rejette si la demande a déjà été reçue (`received = true`) — une demande satisfaite
  n'a pas de sens à reformuler pour adresse erronée. Pas de contrainte
  `isOverdue()` requise (un retour de courrier peut survenir à tout moment, avant ou
  après l'échéance).

## Hors périmètre

- Automatisation par scheduler (relances/escalades déclenchées automatiquement par le
  temps écoulé, sans action manuelle d'un agent) — chantier transversal plus large
  (toucherait potentiellement le mécanisme déjà partiellement construit dans
  `NotificationServiceImpl` pour `Notification`/`Dossier`), décision utilisateur de le
  reporter.
- Génération PDF de la saisine judiciaire (§9, documents générés) — même traitement
  que `Mandat`/`EngagementConfidentialite`/`PlanInvestigation` dans ce dépôt,
  systématiquement reportée.
- Toute action juridique réelle déclenchée par l'atteinte de `SAISINE_JUDICIAIRE`
  (notification externe, lien vers un module juridique) — le texte ne décrit que la
  progression de l'escalade, pas d'intégration externe.
- Champ « adresse structurée » distinct de `recipientLabel` — le champ texte libre
  existant est réutilisé, pas remplacé.

## Tests

- `DemandeDocumentsServiceImplTest` : `escalate` — succès jusqu'à `SAISINE_JUDICIAIRE`
  (plus de `BusinessException` sur le délai), toujours rejeté au-delà (déjà testé,
  comportement inchangé). `reportAddressError` — succès (destinataire mis à jour,
  niveau d'escalade inchangé, délai recalculé), rejet si déjà reçue.
