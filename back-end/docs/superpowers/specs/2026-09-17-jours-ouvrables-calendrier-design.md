# Calendrier des jours fériés + calculateur de délais ouvrables — Design

**Chantier transversal "jours ouvrables réels", sous-chantier 1/4.**

## Contexte et texte source

Plan de travail ASCE-LC (`docs/reference/plan-de-travail-asce-lc.md`, section 7, ligne 260) :
exigence d'un « compteur en jours ouvrables (calendrier des jours fériés burkinabè
administrable), alerte à J-3, alerte à échéance, escalade automatique au supérieur
hiérarchique, tableau des dossiers en dépassement par acteur ».

Ce chantier a été identifié comme un gap dès le Lot 6 (2026-08-18) et reporté, jugé « bien
plus large que le seul calcul de date ». Redécoupé en 4 sous-chantiers le 2026-09-17
(voir mémoire projet) :
1. **Calendrier des jours fériés + calculateur partagé** (ce document).
2. Alertes J-3 + à échéance.
3. Escalade automatique — bloqué sur une question métier non tranchée (aucun concept de
   hiérarchie/supérieur dans le modèle de données actuel).
4. Tableau des dossiers en dépassement par acteur.

## État actuel (recherche exhaustive avant conception)

`ParametreDelai.joursOuvrables` (colonne booléenne, existe en base depuis le socle) n'est lu
**nulle part** dans le code de production. `ParametreDelaiService.resolveDelaiJours(code)`
retourne uniquement le nombre de jours (`int`), sans jamais consulter ce flag.

**5 sites dupliquent le même calcul en jours calendaires** (`from.plusSeconds(delaiJours *
24 * 3600)`) :
- `PlanActionsService.resolveDeadline(Instant, String)` (méthode privée)
- `TransmissionAutoriteService.resolveDeadline(Instant, String)` (méthode privée)
- `MissionSuiviService.resolveDeadline(Instant, String)` (méthode privée)
- `InvestigationServiceImpl.resolveDeadline(Instant, String)` (méthode privée, utilisée à
  3 emplacements : lignes ~1271/1277/1283/1289)
- `InvestigationServiceImpl` ligne ~1438-1440 : calcul inline, sans passer par une méthode
  `resolveDeadline` du tout (`dateDelivrance.get().plusSeconds(...)`)

Tous suivent le même patron défensif : `try { ... } catch (ResourceNotFoundException e) {
log.warn(...); return null; }` — si le code de délai est introuvable/inactif, l'échéance
n'est simplement pas calculée (pas d'exception propagée).

**Aucune tâche planifiée (`@Scheduled`) n'existe dans tout le dépôt** — confirmé par
recherche exhaustive. Les sous-chantiers 2 (alertes) et 3 (escalade) nécessiteront cette
capacité, absente à ce jour — hors périmètre de ce document.

**`NotificationService`/`NotificationType` existent déjà** et sont réutilisables pour les
sous-chantiers suivants — hors périmètre ici aussi (ce sous-chantier ne crée aucune
notification, seulement le calcul de date).

## Arbitrages tranchés pendant le brainstorming

1. **Jours fériés en dates fixes, saisies année par année** — pas de moteur de récurrence.
   Certains jours fériés burkinabè sont mobiles (calendrier lunaire : Tabaski, fin du
   Ramadan) ; un référentiel à dates fixes évite de construire et maintenir un moteur de
   règles pour ~12 jours par an, au prix d'une saisie annuelle par un administrateur.
2. **Week-end fixe (samedi + dimanche)**, pas administrable séparément — cohérent avec la
   semaine de travail standard au Burkina Faso, pas de besoin identifié de le paramétrer.

## Composants

### Entité `JourFerie` (`extends AuditEntity`)

Patron structurel identique aux référentiels déjà livrés (`TypeInfraction`,
`IndiceFraude`, `Departement`) :

| Champ | Type | Contrainte |
|---|---|---|
| `date` | `LocalDate` | NOT NULL, UNIQUE |
| `libelle` | `String` | NOT NULL, max 300 |
| `actif` | `Boolean` | NOT NULL, défaut `true` |

Table `jour_ferie`, index unique sur `date`.

### Migration

`db/changelog/migrations/009-create-jour-ferie.sql` — nouveau changelog Liquibase, suit la
numérotation actuelle (dernier fichier : `008-seed-indice-fraude.sql`).

### Repository `JourFerieRepository`

- `existsByDateAndActifTrue(LocalDate date): boolean` — méthode consommée directement par
  `DeadlineCalculator` pour tester si une date donnée est fériée.
- `findByActifTrueOrderByDateAsc(): List<JourFerie>` — lecture admin/publique.
- `findAll(): List<JourFerie>` — lecture admin complète.
- `existsByDate(String)` → en réalité `existsByDate(LocalDate)` pour la garde d'unicité en
  écriture.

### Service `JourFerieService` / `JourFerieServiceImpl`

CRUD standard (`@Transactional(readOnly = true)` classe, `@Transactional` sur écriture) :
`findAllActifs()`, `findAll()`, `create(JourFerieRequest)` (vérifie l'unicité de `date`
avant insertion), `update(UUID id, JourFerieRequest)` (identifiant par `id`, pas de `code`
métier ici — une date n'a pas de code naturel comme les autres référentiels).

### Controller `JourFerieController`

Route `/api/v1/jours-feries`, patron identique à `ParametreDelaiController` :

| Méthode | Route | Rôles |
|---|---|---|
| `GET` | `/api/v1/jours-feries` | `isAuthenticated()` |
| `GET` | `/api/v1/jours-feries/admin` | `hasAnyRole('ADMIN_DDIC','CGEA')` |
| `POST` | `/api/v1/jours-feries` | `hasAnyRole('ADMIN_DDIC','CGEA')` |
| `PUT` | `/api/v1/jours-feries/{id}` | `hasAnyRole('ADMIN_DDIC','CGEA')` |

### `DeadlineCalculator` (nouveau, `shared/utils`)

`@Component`, injecte `JourFerieRepository` directement (pas besoin de passer par le
service — cohérent avec l'usage interne, pas exposé en façade REST).

```java
public Instant addBusinessDays(Instant from, int joursOuvrables)
```

Algorithme : avance jour civil par jour civil depuis `from`, décrémente un compteur
uniquement les jours qui ne sont ni samedi, ni dimanche, ni un jour férié actif
(`jourFerieRepository.existsByDateAndActifTrue(...)`), jusqu'à atteindre `joursOuvrables`
jours ouvrables franchis. Retourne l'`Instant` du dernier jour ouvrable atteint (même heure
que `from`, cohérent avec le comportement `plusSeconds` actuel).

```java
public Instant addCalendarDays(Instant from, int jours)
```

Simple délégation vers `from.plusSeconds((long) jours * 24 * 3600)` — extrait des 5 sites
existants pour centraliser la logique, même si le calcul lui-même ne change pas. Permet aux
5 appelants de basculer entre les deux méthodes selon `ParametreDelai.joursOuvrables` sans
dupliquer le calcul calendaire non plus.

### `ParametreDelaiService` — méthode ajoutée

```java
boolean resolveJoursOuvrables(String code)
```

Retourne la valeur du flag `joursOuvrables` pour un code donné (même gestion d'erreur que
`resolveDelaiJours` : `ResourceNotFoundException` si le code est introuvable/inactif —
laissée à la charge de l'appelant, cohérent avec le patron try/catch déjà en place dans les
5 sites).

### Refactor des 5 sites existants

Chaque `resolveDeadline(Instant from, String delaiCode)` devient :

```java
private Instant resolveDeadline(Instant from, String delaiCode) {
    try {
        int delaiJours = parametreDelaiService.resolveDelaiJours(delaiCode);
        boolean joursOuvrables = parametreDelaiService.resolveJoursOuvrables(delaiCode);
        return joursOuvrables
                ? deadlineCalculator.addBusinessDays(from, delaiJours)
                : deadlineCalculator.addCalendarDays(from, delaiJours);
    } catch (ResourceNotFoundException e) {
        log.warn("Délai {} indisponible — échéance non calculée : {}", delaiCode, e.getMessage());
        return null;
    }
}
```

`DeadlineCalculator` injecté par constructeur dans chacun des 4 services concernés
(`InvestigationServiceImpl` en a besoin une seule fois malgré 2 sites d'usage — la méthode
privée `resolveDeadline` déjà partagée en interne, plus le site inline ligne ~1438-1440 à
convertir vers un appel à cette même méthode plutôt que son calcul dupliqué).

**Comportement pour `MISSION_SUIVI_PLAN_ACTIONS`** (`joursOuvrables = FALSE`, 365 jours,
« dans l'année ») : bascule vers `addCalendarDays`, comportement strictement identique à
aujourd'hui — aucune régression attendue sur ce délai précis.

**Comportement pour les 13 autres codes** (`joursOuvrables = TRUE`) : bascule vers
`addBusinessDays` — **changement de comportement assumé et voulu** : les échéances
affichées avanceront (ou reculeront selon le nombre de week-ends/jours fériés dans
l'intervalle) par rapport à aujourd'hui. C'est l'objet même de ce chantier.

## Tests

- `DeadlineCalculatorTest` (unitaire, `JourFerieRepository` mocké) : vendredi + 1 jour
  ouvrable → lundi (saute le week-end) ; jour férié actif au milieu de l'intervalle → décalé
  d'un jour de plus ; jour férié inactif → ignoré (compte comme jour ouvrable) ; 0 jour
  ouvrable → retourne `from` inchangé ; `addCalendarDays` → délégation simple vérifiée.
- `JourFerieServiceImplTest` : patron standard des référentiels déjà livrés (création,
  conflit d'unicité sur `date`, mise à jour, introuvable).
- Par service refactoré (`PlanActionsServiceTest`, `TransmissionAutoriteServiceTest`,
  `MissionSuiviServiceTest`, `InvestigationServiceImplTest` existants) : ajouter un test
  ciblé vérifiant que `resolveDeadline` délègue au bon calcul selon `joursOuvrables`
  (mock de `ParametreDelaiService`/`DeadlineCalculator`, pas de récriture de la suite
  existante).

## Hors périmètre (explicitement)

- Alertes J-3 / à échéance (sous-chantier 2/4).
- Escalade automatique (sous-chantier 3/4, bloqué sur arbitrage métier).
- Tableau des dossiers en dépassement par acteur (sous-chantier 4/4).
- Tâche planifiée (`@Scheduled`) — n'est nécessaire qu'à partir du sous-chantier 2.
- Contenu réel du calendrier des jours fériés burkinabè pour les années à venir — le
  référentiel est livré vide (comme `Departement` l'a été), à peupler séparément une fois
  la liste officielle confirmée. Pas une donnée à deviner.
