# Dérivation de la nature de saisine (NatureSaisine) — Design

Statut : approuvé par l'utilisateur le 2026-07-28. Périmètre : fermeture partielle du Lot 1
du plan de travail ASCE-LC (réception/enregistrement).

## Contexte

Le manuel ASCE-LC (§4.1) impose que la nature d'une saisine citoyenne (Dénonciation /
Plainte) soit **dérivée** de la qualité du déposant (Victime / Représentant de la victime /
Témoin), avec une contrainte d'anonymat : l'anonymat est interdit pour Victime et
Représentant de la victime, autorisé pour Témoin.

Le code actuel ne respecte pas cette règle :

- `Dossier.type` (`TypeSaisine{COMPLAINT,DENUNCIATION,AUTO_REFERRAL,ANONYMOUS}`) est choisi
  librement par le client dans `DossierCreateRequest`, sans lien avec la qualité du déposant.
- `Declarant.quality` est une chaîne libre non validée, non typée.
- `TypeSaisine` inclut `ANONYMOUS` comme type à part entière, alors que `Declarant.anonymous`
  (booléen) porte déjà cette information — doublon de modélisation.
- `resolveDeclarant()` réutilise un `Declarant` existant (recherche par email/téléphone) entre
  dossiers différents : une donnée "qualité" stockée sur `Declarant` serait donc partagée à
  tort entre des saisines indépendantes de la même personne.

## Décisions

### 1. Refonte de `TypeSaisine`

`TypeSaisine{DENONCIATION, PLAINTE, SIGNALEMENT, AUTO_SAISINE}` remplace
`{COMPLAINT, DENUNCIATION, AUTO_REFERRAL, ANONYMOUS}`. `ANONYMOUS` disparaît comme type ;
l'anonymat reste porté uniquement par `Declarant.anonymous`.

Impact code recensé (faible, 3 fichiers hors enum lui-même) : `DossierResponse`,
`DossierCreateRequest`, `Dossier` (déclaration du champ), `PdfExportService.getTypeLabel()`.
Aucun usage en logique métier (switch/if) trouvé ailleurs sur les anciennes valeurs.

### 2. Nouvel enum `QualiteDeclarant`

`QualiteDeclarant{VICTIME, REPRESENTANT_VICTIME, TEMOIN}`, remplace la chaîne libre
`Declarant.quality`.

### 3. Emplacement de la qualité : sur `Dossier`, pas sur `Declarant`

La qualité dépend de la saisine, pas de la personne (le même déclarant peut être témoin sur
un dossier et victime sur un autre). Nouvelle colonne `Dossier.quality` (`QualiteDeclarant`,
nullable). `Declarant.quality` (colonne existante) est supprimée.

### 4. Matrice de dérivation

Validée avec l'utilisateur — dérivation large, pas limitée à `CITIZEN` :

| TypeDeclarant | Quality | → TypeSaisine | Anonymat |
|---|---|---|---|
| CITIZEN / COMPANY / ASSOCIATION | VICTIME ou REPRESENTANT_VICTIME | PLAINTE | interdit |
| CITIZEN / COMPANY / ASSOCIATION | TEMOIN | DENONCIATION | autorisé |
| ANONYMOUS | TEMOIN (seule valeur acceptée) | DENONCIATION | — (déjà anonyme) |
| ANONYMOUS | VICTIME / REPRESENTANT_VICTIME | rejeté | — |
| PUBLIC_AUTHORITY | ignorée | SIGNALEMENT (forcé) | n/a |
| ASCE_SELF_REFERRAL | ignorée | AUTO_SAISINE (forcé) | n/a |

Règle d'anonymat : `anonymous=true` incompatible avec `quality ∈ {VICTIME, REPRESENTANT_VICTIME}`
→ rejet. `quality` obligatoire pour CITIZEN/COMPANY/ASSOCIATION/ANONYMOUS, ignorée pour
PUBLIC_AUTHORITY/ASCE_SELF_REFERRAL.

### 5. Composant de dérivation

Nouveau service pur `NatureSaisineResolver` (pas de dépendance JPA), appelé depuis
`DossierServiceImpl.submit()` après résolution du `Declarant`, avant le mapping vers
l'entité `Dossier`. Remplace toute valeur de `type` fournie par le client.

Erreurs → `BusinessException`, même famille que la vérification protection lanceur d'alerte
déjà en place dans `submit()`.

### 6. API

- `DossierCreateRequest` : suppression du champ `type` (calculé côté serveur, breaking
  change assumé) ; ajout de `quality` (`QualiteDeclarant`, nullable).
- `DeclarantCreateRequest` : suppression du champ `quality`.
- `DossierResponse` : ajoute `quality`, conserve `type` (désormais calculé).

### 7. Migration des données

Changelog Liquibase `009-nature-saisine-derivation.sql` :
- ajoute `dossier.quality`
- remappe `dossier.type` existant : `COMPLAINT→PLAINTE`, `DENUNCIATION→DENONCIATION`,
  `AUTO_REFERRAL→AUTO_SAISINE`, `ANONYMOUS→DENONCIATION` (avec `declarant.anonymous=true`
  déjà cohérent dans la majorité des cas — à vérifier ligne par ligne au moment du script)
- supprime `declarant.quality`

### 8. Effets de bord

- `PdfExportService.getTypeLabel()` : nouveau mapping de libellés (Plainte, Dénonciation,
  Signalement, Auto-saisine).
- `DossierMapper` (MapStruct) : retirer le mapping direct de `type` depuis la requête,
  ajouter le mapping de `quality`.

## Hors périmètre de cette spec

- L'entité `InformationPreoccupante` (Lot 9) — nature "Information préoccupante" n'ouvre pas
  de dossier au sens du Processus B, donc hors modèle `Dossier`.
- L'habilitation nominative par dossier (cloisonnement) — spec séparée, à venir.
- Les formulaires distincts pour signalement institutionnel / soit-transmis Procureur /
  auto-saisine (mentionnés en §4.1 du plan) — l'API actuelle ne couvre qu'un seul endpoint de
  soumission ; sa possible scission en écrans dédiés n'est pas traitée ici.

## Tests

- Unitaires `NatureSaisineResolver` : les 6 lignes de la matrice + rejets (anonyme+victime,
  quality manquante pour CITIZEN, ANONYMOUS+victime, quality fournie mais ignorée pour
  SIGNALEMENT/AUTO_SAISINE).
- Intégration `DossierServiceImpl.submit()` : `type` calculé correct, absence de `type` dans
  le payload accepté.
- Migration : vérifie le remappage sur un jeu de données existant (incluant le cas
  `ANONYMOUS` → `DENONCIATION`).
