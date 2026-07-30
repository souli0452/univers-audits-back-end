# Champs manquants du formulaire de dépôt — Design

Statut : approuvé par l'utilisateur le 2026-07-30. Périmètre : suite de la fermeture
du Lot 1 (réception/enregistrement) du plan de travail ASCE-LC.

## Contexte

Le 2026-07-30, l'utilisateur a fourni deux formulaires papier réels de l'ASCE-LC
(`Formulaire-de-plainte-et-denonciation-ASCE-LC.pdf`, `Fiche affectation RPD.pdf`).
Leur comparaison champ par champ avec `Dossier`/`Declarant`/`DossierCreateRequest`/
`DeclarantCreateRequest` a fait apparaître 8 champs présents sur le formulaire papier
mais absents du modèle actuel — voir la mémoire projet
`project_asce_backlog_2026_07_30.md` section B pour le détail de chaque écart.

Décision de nommage : les nouveaux champs reprennent le vocabulaire exact du
formulaire (comme `motifs` le fait déjà pour un champ existant), pas une traduction
ou un terme technique inventé — pour rester reconnaissable par le métier qui a
appris le formulaire papier.

## Décisions

### 1. Périmètre : additif uniquement

Aucun champ existant n'est renommé, retiré ou réinterprété. `object` reste tel
quel (titre court, déjà utilisé dans l'UI/le PDF) ; le point 6 du formulaire
("Attentes vis-à-vis de l'ASCE-LC") devient un nouveau champ distinct `attentes`,
pas une réutilisation d'`object`. Conséquence : contrairement aux deux chantiers
précédents de ce Lot (dérivation NatureSaisine, habilitation), **aucun breaking
change API** — tous les nouveaux champs sont optionnels côté requête.

### 2. Champs ajoutés

| Formulaire | Champ | Entité | Type |
|---|---|---|---|
| Cellulaire | `cellulaire` | `Declarant` | `String`, longueur 20 (aligné sur `phoneNumber`) |
| Localité | `localite` | `Declarant` | `String`, longueur 100 (aligné sur `commune`/`province`) |
| Par fax | `FAX` | `SubmissionMode` | nouvelle valeur d'enum |
| Lieu de la dénonciation/plainte | `lieuDepot` | `Dossier` | `String`, longueur 300 (aligné sur `incidentLocation`) |
| Organisme où les faits ont été perpétrés | `organismeFaitsDenomination`, `organismeFaitsAdresse` | `Dossier` | `String` 200, `String` 300 |
| Ce que vous attendez que l'ASCE-LC fasse | `attentes` | `Dossier` | `TEXT` |
| Décision de justice déjà rendue / instance en cours | `decisionJusticeExistante`, `decisionJusticePrecision` | `Dossier` | `Boolean` (défaut `false`), `TEXT` nullable |
| Autre institution déjà saisie | `autreInstitutionSaisie`, `autreInstitutionNom`, `autreInstitutionAdresse` | `Dossier` | `Boolean` (défaut `false`), `String` 200, `String` 300 |

`lieuDepot` est distinct d'`incidentLocation` (lieu des faits, déjà existant) —
ce sont deux informations différentes du formulaire (item 1 vs item 5 de la
« Nature des faits »). `organismeFaitsDenomination`/`Adresse` sont distincts de
`TargetedParty` (partie visée) — l'organisme où les faits ont eu lieu n'est pas
nécessairement la partie visée par la plainte.

### 3. Contrôles de recevabilité (points 7 et 8)

`decisionJusticeExistante` et `autreInstitutionSaisie` alimentent directement la
grille d'étude d'opportunité mentionnée dans le plan de travail original (Lot 2,
litispendance / compétence d'une autre institution) — signalé dès l'audit initial
comme critique. Ce chantier se limite à la **capture** de ces champs à
l'enregistrement ; leur exploitation dans un écran d'étude d'opportunité reste
hors périmètre (Lot 2, non commencé).

### 4. DTOs et réponses

Tous les nouveaux champs sont ajoutés à `DeclarantCreateRequest`/`DeclarantResponse`
et `DossierCreateRequest`/`DossierResponse`, sans annotation `@NotNull`/`@NotBlank`
(le formulaire papier ne les rend pas tous obligatoires, et forcer leur saisie
casserait la compatibilité avec les clients existants du portail). `MapStruct`
les mappe automatiquement (noms de champs identiques entre requête/entité/réponse,
pas de `@Mapping` explicite nécessaire).

## Hors périmètre de cette spec

- Le format du numéro de dossier (`ASCE-YYYY-NNNNNN` vs `NNNNN/YYYY` sur le
  formulaire) — point distinct déjà noté dans la mémoire backlog, pas traité ici.
- L'exploitation d'`decisionJusticeExistante`/`autreInstitutionSaisie` dans un
  écran d'étude d'opportunité (Lot 2).
- L'ajout de ces champs au récépissé (Annexe B4) ou à la fiche dossier PDF — le
  récépissé actuel reste volontairement minimal (voir sa propre spec) ; les
  nouveaux champs restent accessibles via l'API/l'écran de détail du dossier.
- Le trou `PdfExportService.addDeclarantInfo` (fallback `displayName` manquant
  pour un lanceur d'alerte protégé), déjà noté séparément dans la mémoire backlog.

## Tests

- Aucune logique métier nouvelle (champs additifs simples) — pas de nouveau test
  unitaire de service nécessaire au-delà de la vérification de compilation et,
  si un mapper existant a un test, de sa mise à jour éventuelle.
- Pas de test de migration automatisé (même limite déjà documentée pour les
  chantiers précédents de ce Lot — pas d'infrastructure Testcontainers dans ce
  dépôt) ; vérification manuelle post-déploiement.
