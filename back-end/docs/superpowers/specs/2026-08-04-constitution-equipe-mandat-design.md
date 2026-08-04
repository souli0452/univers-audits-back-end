# Constitution d'équipe + Mandat (Lot 3, sous-chantier 1/6) — Design

Statut : approuvé par l'utilisateur le 2026-08-04. Périmètre : premier sous-chantier du
Lot 3 (Lancement de mission), §11 du plan de travail
(`docs/reference/plan-de-travail-asce-lc.md`). Le Lot 2 est entièrement livré. Découpage du
Lot 3 en 6 sous-chantiers approuvé par l'utilisateur (voir mémoire backlog) :
1. Constitution d'équipe + Mandat (ce document)
2. Engagement de confidentialité + Déclaration de conflit d'intérêts
3. Plan d'investigation
4. Incident d'objectivité
5. Procédure d'urgence + mesures conservatoires
6. Insertion effective de la porte `EQUIPE_CONSTITUEE`/`PLAN_VALIDE` dans le flux
   `open()`/`start()` existant (le plus risqué, en dernier, une fois 1-3 posés)

## Contexte

Texte exact (§11) : « Constitution d'équipe (chef de mission DEI + au moins deux
investigateurs + personnes ressources + conseil juridique obligatoire), contrôle de conflit
d'intérêts, appréciation finale et délivrance des mandats par le CGE, engagements de
confidentialité... »

**Découverte importante** : un module `Investigation`/`InvestigationMember` existe déjà dans
le code (construit lors d'une session antérieure, non couvert par l'audit initial de cette
session) — `InvestigationController`/`InvestigationServiceImpl` couvrent déjà une bonne partie
du Lot 3 (ajout/retrait de membres via `addMember`/`removeMember`) **et** du Lot 5 (circuit
d'approbation complet : `approve-dei`/`approve-legal`/`approve-cge`).

**Trou structurel confirmé** : `InvestigationServiceImpl.open()` fait passer
`Dossier.status` de `RECEVABLE` à `EN_INVESTIGATION` **immédiatement à la création** de
l'investigation, avant même qu'un seul membre soit affecté. `start()` ne vérifie aujourd'hui
que `memberCount == 0` (« au moins un membre ») — aucune vérification de la composition exigée
par le texte (chef de mission, ≥2 investigateurs, conseil juridique obligatoire). C'est ce trou
que ce sous-chantier comble, sans toucher à `open()` (la porte `EQUIPE_CONSTITUEE`/
`PLAN_VALIDE` proprement dite reste le sous-chantier 6, une fois les briques 1-3 posées).

**`TeamRole` actuel trop grossier** : seulement `TEAM_LEADER`/`MEMBER`, alors que le texte
exige 4 rôles distincts.

**« Chef de mission DEI » — vérifié et clarifié avec l'utilisateur** : le référentiel
`Departement` (table `code`/`libelle`) existe mais ne contient aucune donnée `DEI` semée
(aucune migration ne la crée) — même trou de référentiel que celui déjà noté au Lot 0. Décision
utilisateur : la règle de composition vérifie uniquement le **rôle** `CHEF_MISSION` (via
`TeamRole`), sans exiger `Agent.departement.code == 'DEI'` — cohérent avec le traitement déjà
fait pour d'autres arbitrages de structure non tranchés (BRPD, CJ/OR/CJ/OO).

**Contrainte de colonne découverte** : `investigation_member.team_role` est actuellement
`VARCHAR(15)` — trop court pour `PERSONNE_RESSOURCE` (18 caractères) et
`CONSEIL_JURIDIQUE` (17 caractères). Nécessite un élargissement de colonne.

**Renommage, pas ajout** : `TEAM_LEADER`/`MEMBER` sont **remplacés** par les 4 nouvelles
valeurs (pas coexistence) — une vraie migration de remappage des données existantes est donc
nécessaire, pas un simple ajout additif comme les chantiers précédents.

## Décision

### 1. `TeamRole` — renommage complet

```java
public enum TeamRole {
    CHEF_MISSION,
    INVESTIGATEUR,
    PERSONNE_RESSOURCE,
    CONSEIL_JURIDIQUE
}
```

`TEAM_LEADER` et `MEMBER` disparaissent. Remappage en migration : `TEAM_LEADER ->
CHEF_MISSION` (correspondance directe, même rôle de coordination) ; `MEMBER -> INVESTIGATEUR`
(rôle de terrain générique le plus proche sémantiquement — approximation assumée pour les
données historiques, aucune autre information ne permet de distinguer rétroactivement un
`MEMBER` qui aurait dû être `PERSONNE_RESSOURCE` ou `CONSEIL_JURIDIQUE`).

### 2. Règle de composition dans `start()`

Remplace l'actuel `memberCount == 0` par une vérification de composition complète :
- Exactement 1 membre actif en rôle `CHEF_MISSION`.
- Au moins 2 membres actifs en rôle `INVESTIGATEUR`.
- Exactement 1 membre actif en rôle `CONSEIL_JURIDIQUE`.
- `PERSONNE_RESSOURCE` : aucune contrainte de nombre (0 ou plus).

Messages d'erreur distincts par règle violée (comme le reste du code de ce service —
`BusinessException` avec message explicite, pas une erreur générique).

### 3. Entité `Mandat`

1:1 avec `Investigation` (une investigation = un mandat, délivré une fois — pas de
réémission modélisée dans ce sous-chantier).

| Champ | Type |
|---|---|
| `investigation` | FK unique, non nul |
| `dateDelivrance` | `Instant`, non nul |
| `agentCGE` | FK `Agent`, non nul (signataire) |

Délivré via un nouvel endpoint distinct de `start()` — le mandat CGE et le démarrage effectif
de l'investigation sont deux actes distincts dans le texte (« appréciation finale et
délivrance des mandats par le CGE » précède le démarrage terrain). `start()` exige qu'un
`Mandat` existe, en plus de la règle de composition.

### 4. API

- `POST /api/v1/investigations/{id}/mandat` — délivrer le mandat, réservé `CGE, ADMIN_DDIC`.
  Rejette si un mandat existe déjà, ou si la règle de composition (point 2) n'est pas
  respectée au moment de la délivrance.
- `start()` (existant, modifié) : rejette désormais aussi si aucun `Mandat` n'a été délivré.

## Hors périmètre

- Vérification du département du chef de mission (`DEI`) — décision utilisateur, voir
  Contexte.
- Réémission/révision d'un mandat déjà délivré.
- Contrôle de conflit d'intérêts (sous-chantier 2/6).
- Insertion des statuts `EQUIPE_CONSTITUEE`/`PLAN_VALIDE` dans `DossierStatus` et modification
  de `open()` (sous-chantier 6/6).

## Tests

- `InvestigationServiceImplTest` : `start()` — rejet pour chacune des 3 règles de composition
  violées individuellement (0 chef de mission, 1 seul investigateur, 0 conseil juridique),
  rejet si aucun mandat, succès si composition complète + mandat délivré.
- Nouveau test pour `deliverMandat` (ou nom équivalent) : succès, rejet si mandat déjà
  existant, rejet si composition incomplète au moment de la délivrance.
- Migration `019` : vérifier le remappage `TEAM_LEADER -> CHEF_MISSION` / `MEMBER ->
  INVESTIGATEUR` sur un jeu de données existant, et le nouvel élargissement de colonne.
