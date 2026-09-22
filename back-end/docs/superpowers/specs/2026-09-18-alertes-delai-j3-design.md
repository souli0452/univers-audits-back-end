# Alertes de délai J-3 et à échéance — Design

**Sous-chantier 2/4 du chantier transversal "jours ouvrables réels" ASCE-LC.**
Suite de [[2026-09-17-jours-ouvrables-calendrier-design]] (sous-chantier 1/4, livré)
et du sous-chantier de suivi (conversion des méthodes d'entité, livré, commit
`ad40bd8`). Texte source (plan de travail, section 7, ligne 260) : "compteur en
jours ouvrables ..., alerte à J-3, alerte à échéance, escalade automatique au
supérieur hiérarchique, tableau des dossiers en dépassement par acteur".

Ce document couvre uniquement les deux premiers éléments : **alerte à J-3** et
**alerte à échéance**. L'escalade automatique (3/4) reste bloquée sur un
arbitrage métier (notion de hiérarchie absente du modèle). Le tableau de bord
(4/4) est un endpoint de lecture séparé.

## Périmètre

**Décidé avec l'utilisateur** : ce sous-chantier couvre les 4 échéances "tête
d'affiche" du chantier transversal — celles pour lesquelles une déviation est
directement visible par un agent traitant un dossier :

1. `Dossier.acknowledgmentDeadline` (délai d'accusé de réception, code
   `ACCUSE_RECEPTION`)
2. `Dossier.additionalInfoDeadline` (délai de complément d'information, code
   `DEMANDE_COMPLEMENT`)
3. `Investigation` — échéance de fin planifiée ou prolongée (code
   `INVESTIGATION_DUREE_DEFAUT`)
4. `DemandeDocuments.deadline` (délais d'escalade de demande de documents,
   codes `DEMANDE_DOCUMENTS_*`)

**Hors périmètre, explicitement** : les 5 échéances de circuit interne
converties en jours ouvrables au sous-chantier 1/4 (`REVUE_CJ_RAPPORT`,
`ANALYSE_DEI_RAPPORT`, `APPROBATION_CGEA_RAPPORT`, `APPROBATION_CGE`,
`VALIDATION_PLAN_INVESTIGATION_DEI`) n'ont aujourd'hui **aucune alerte**, ni à
échéance ni J-3 — ce n'est donc pas une régression de ne pas les couvrir ici,
mais un gap distinct, documenté pour un futur sous-chantier si le besoin est
confirmé.

## État actuel (découverte)

Un job planifié existe déjà et n'était pas documenté par les chantiers
précédents : `NotificationServiceImpl.sendDeadlineAlerts()`
(`@Scheduled(cron = "0 0 8 * * MON-FRI")`). Il couvre déjà **l'alerte à
échéance dépassée** pour 3 des 4 échéances tête d'affiche :

| Échéance | Requête `DossierRepository` | Type d'alerte créé |
|---|---|---|
| AR dépassé | `findOverdueAcknowledgments` | `DEADLINE_ALERT` |
| Complément dépassé | `findOverdueComplementRequests` | `INTERNAL_ALERT` |
| Investigation dépassée | `findOverdueInvestigations` | `INVESTIGATION_ALERT` |
| Demande de documents | — (n'existe pas) | — |

Déduplication actuelle : `notificationRepository.existsByDossierIdAndType(dossierId, type)`
— une alerte par (dossier, type), jamais répétée, quel que soit le nombre de
jours passés en dépassement.

`Notification.dossier` est une FK obligatoire (`nullable = false`) — toute
notification est rattachée à un dossier. Un dossier confidentiel masque ses
notifications aux agents non habilités (mécanisme existant, `DossierAccessGuard`,
non modifié ici — les alertes créées par ce job héritent de cette protection
sans changement puisqu'elles passent par les mêmes chemins de lecture
(`findByDossierId`, `findByAgentOrRecipient`)).

`NotificationServiceImpl` n'a **aucun test aujourd'hui** (0 fichier
`NotificationServiceImplTest.java`) — gap de couverture préexistant, comblé par
ce sous-chantier pour tout le code touché (existant et nouveau).

## Conception

### 1. Un seul job étendu, pas un second

`sendDeadlineAlerts()` reste l'unique point de responsabilité "alertes de
délai" — même cron. On y ajoute 5 blocs (3 J-3 sur les échéances déjà
couvertes, 2 nouveaux pour `DemandeDocuments` à échéance + J-3), suivant
exactement le patron des 3 blocs existants (requête → boucle → dédup →
`Notification.builder()` → sauvegarde).

### 2. Fenêtre J-3

Une échéance est "à J-3" quand `now <= deadline <= now + 3 jours calendaires`
et qu'elle n'est pas encore dépassée. Le nombre de jours (3) est un délai
d'anticipation fixe, lu contre l'horloge murale — **pas** un nouveau calcul en
jours ouvrables (le calcul en jours ouvrables a déjà produit la date `deadline`
elle-même ; la fenêtre d'alerte est un simple compte à rebours calendaire
avant cette date). Calculé via `deadlineCalculator.addCalendarDays(Instant.now(), 3)`,
réutilisant le composant déjà partagé plutôt que `plusSeconds` inline.

Le job tournant une fois par jour, une échéance entre dans la fenêtre J-3 un
jour donné et y reste jusqu'à son passage en dépassement ; la déduplication
par (dossier, type) — ou (demande, type) pour `DemandeDocuments` — garantit
qu'une seule alerte J-3 est créée, pas une par jour de la fenêtre.

### 3. Nouveaux types d'alerte

Ajoutés à `NotificationType` (enum, `@Enumerated(EnumType.STRING)`, colonne
`length = 35` — la plus longue valeur ajoutée, `DEMANDE_DOCUMENTS_ALERT_J3`,
fait 24 caractères, aucun changement de colonne requis) :

- `DEADLINE_ALERT_J3` (AR, J-3)
- `COMPLEMENT_ALERT_J3` (complément, J-3)
- `INVESTIGATION_ALERT_J3` (investigation, J-3)
- `DEMANDE_DOCUMENTS_ALERT` (demande de documents, à échéance — n'existait pas)
- `DEMANDE_DOCUMENTS_ALERT_J3` (demande de documents, J-3)

### 4. Déduplication précise pour `DemandeDocuments` — FK nullable ajoutée

`existsByDossierIdAndType` suffit pour les 3 échéances portées directement par
`Dossier` (un seul AR, un seul complément, une seule investigation actives par
dossier). Ce n'est **pas** vrai pour `DemandeDocuments` : une investigation
peut avoir plusieurs demandes de documents concurrentes et non reçues (ex. deux
tiers sollicités en parallèle). Dédupliquer par dossier seul supprimerait à
tort l'alerte d'une deuxième demande dès que la première a déjà déclenché une
alerte du même type.

Correction : ajout d'une FK nullable `demande_documents_id` sur `Notification`
(nulle pour tous les autres types d'alerte), utilisée uniquement pour
`DEMANDE_DOCUMENTS_ALERT`/`DEMANDE_DOCUMENTS_ALERT_J3`. Nouvelle méthode de
dédup `existsByDemandeDocumentsIdAndType(UUID, NotificationType)`. Le champ
`dossier` de la notification reste rempli (`demande.getInvestigation().getDossier()`)
pour que les mécanismes de visibilité/lecture existants (`findByDossierId`,
`findByAgentOrRecipient` via `dossier.agentInCharge`) fonctionnent sans
modification.

### 5. Contenu des alertes

10 nouvelles lignes `portal_config` (5 paires sujet/contenu), suivant
exactement le patron des lignes existantes (`notif_subject_deadline_ar`, etc.) :
`notif_subject_deadline_ar_j3`, `notif_content_deadline_ar_j3`,
`notif_subject_deadline_complement_j3`, `notif_content_deadline_complement_j3`,
`notif_subject_deadline_investigation_j3`, `notif_content_deadline_investigation_j3`,
`notif_subject_deadline_demande_documents`, `notif_content_deadline_demande_documents`,
`notif_subject_deadline_demande_documents_j3`, `notif_content_deadline_demande_documents_j3`.
Placeholder `{numero}` (numéro du dossier), cohérent avec l'existant.
`resolveNotificationText` ne lève jamais si une clé manque (retombe sur `""`)
— un oubli de seed ne casserait pas le job, juste une alerte au contenu vide ;
les migrations listent néanmoins les 10 clés explicitement pour éviter ce cas.

### 6. Nouvelles requêtes

**`DossierRepository`** — 3 nouvelles, miroir exact des 3 requêtes "overdue"
existantes, remplaçant `deadline < :now` par `deadline BETWEEN :now AND :in3Days` :
`findAcknowledgmentsDueWithin`, `findComplementsDueWithin`,
`findInvestigationsDueWithin` (même jointure/statuts que leurs équivalents
"overdue").

**`DemandeDocumentsRepository`** — 2 nouvelles (le repository n'a qu'une seule
méthode aujourd'hui, `findByInvestigationIdOrderBySentAtDesc`) :

```java
@Query("""
        SELECT dd FROM DemandeDocuments dd
        WHERE dd.received = false
        AND dd.deadline < :now
        ORDER BY dd.deadline ASC
        """)
List<DemandeDocuments> findOverdue(@Param("now") Instant now);

@Query("""
        SELECT dd FROM DemandeDocuments dd
        WHERE dd.received = false
        AND dd.deadline BETWEEN :now AND :in3Days
        ORDER BY dd.deadline ASC
        """)
List<DemandeDocuments> findDueWithin(
        @Param("now") Instant now, @Param("in3Days") Instant in3Days);
```

**`NotificationRepository`** — 1 nouvelle méthode dérivée :
`boolean existsByDemandeDocumentsIdAndType(UUID demandeDocumentsId, NotificationType type);`

### 7. Migrations

- `011-add-demande-documents-to-notification.sql` : `ALTER TABLE notification
  ADD COLUMN demande_documents_id UUID`, FK vers `demande_documents(id)`,
  colonne nullable (pas de `NOT NULL`, pas de valeur par défaut requise —
  toutes les lignes existantes restent valides).
- `012-seed-notification-templates-j3.sql` : les 10 lignes `portal_config`
  décrites en §5, même structure de colonnes que `005-seed-portal-config.sql`
  (`id, config_key, config_value, label, description, value_type, group_name,
  updated_at, version` — `PortalConfig` n'étend pas `AuditEntity`, toutes les
  colonnes doivent être fournies explicitement).

### 8. `NotificationServiceImpl`

Nouvelle dépendance injectée : `DeadlineCalculator` (déjà un `@Component`
partagé, pas de nouvelle classe) et `DemandeDocumentsRepository`.
`sendDeadlineAlerts()` calcule `Instant in3Days` une seule fois en tête de
méthode, puis exécute les 3 blocs existants (inchangés) suivis de 5 nouveaux
blocs suivant le même patron requête → boucle → `existsBy...AndType` → build →
save → log.

## Tests

`NotificationServiceImplTest` créé de zéro (`@ExtendWith(MockitoExtension.class)`,
mocks `NotificationRepository`/`DossierRepository`/`DemandeDocumentsRepository`/
`AgentRepository`/`DossierDetailsMapper`/`DossierAccessGuard`/`PortalConfigService`/
`DeadlineCalculator`). Couverture minimale exigée pour `sendDeadlineAlerts()` :

- Pour chacune des 3 échéances existantes ET des 2 nouvelles (`DemandeDocuments`) :
  un test "à échéance dépassée crée l'alerte" et un test "J-3 crée l'alerte
  J-3", chacun vérifiant le type exact et le contenu résolu via
  `portalConfigService.resolveNotificationText`.
- Un test de non-duplication (élément déjà alerté d'un type → pas de second
  `save`) pour au moins un cas dossier-scoped et le cas `DemandeDocuments`
  scoped (celui qui motive la FK ajoutée).
- Un test prouvant qu'une **deuxième** `DemandeDocuments` non reçue sur le
  même dossier, avec sa propre échéance dépassée, reçoit bien sa propre alerte
  même si la première a déjà été alertée (preuve directe que la dédup par
  `demande_documents_id` fonctionne, pas seulement par dossier).
- Tests de non-régression sur les 3 blocs existants (comportement inchangé).

## Hors périmètre (rappel)

- Les 5 échéances de circuit interne (voir §Périmètre) — gap distinct, non
  traité ici.
- L'escalade automatique (3/4) et le tableau de dépassement par acteur (4/4) —
  sous-chantiers suivants du même chantier transversal.
- Le canal d'envoi réel (email/SMS) des alertes — ces alertes utilisent
  `NotificationChannel.PORTAL` comme l'existant, aucun changement de canal
  demandé ni nécessaire ici.

## Correction post-livraison (revue finale de branche)

La revue finale de branche (Opus) a trouvé 1 Critical et 3 Important (l'un
d'eux, l'index manquant, regroupé dans le même correctif que le Critical
ci-dessous), tous corrigés dans une vague de fix unique avant merge :

1. **Critical** : le CHECK constraint SQL `notification_type_check` (figé dans
   `001-baseline-schema.sql` au moment de la régénération du schéma de
   référence, 2026-09-17) ne connaissait que les 11 valeurs `NotificationType`
   d'alors — les 5 valeurs ajoutées par ce sous-chantier étaient rejetées à
   l'insertion, annulant la transaction complète de `sendDeadlineAlerts()`
   (y compris les 3 alertes "à échéance" préexistantes du même run). Corrigé
   par une nouvelle migration (`013`) élargissant le constraint + ajoutant
   l'index manquant sur `notification.demande_documents_id`.
2. **Important** : la déduplication `DemandeDocuments` par `(id, type)` seul
   supprimait définitivement le re-déclenchement après une escalade (qui
   réinitialise `sentAt`/`deadline` sur la même ligne) — corrigé en ajoutant
   la récence (`existsByDemandeDocumentsIdAndTypeAndCreatedAtAfter`, comparé
   à `demande.getSentAt()`).
3. **Important** : aucun test ne persistait réellement une `Notification`
   contre le vrai schéma (100% Mockito) — c'est directement ce qui a permis
   au Critical #1 de passer inaperçu à travers les 4 revues de tâche. Corrigé
   par un test d'intégration dédié (`NotificationTypePersistenceTest`) qui
   persiste une notification de chacun des 5 nouveaux types contre la vraie
   base, plus des assertions de clé `portal_config` exacte sur les tests
   existants.

**Limitation connue, non corrigée (décision explicite, hors périmètre de la
vague de fix)** : le même défaut de déduplication touche potentiellement les
2 alertes J-3 côté `Dossier`/`Investigation` — `Investigation.extendedDeadline`
(une prolongation d'investigation) et `Dossier.additionalInfoDeadline` (une
nouvelle demande de complément après un premier cycle) peuvent également être
réinitialisées sur la même ligne, et la dédup `(dossier, type)` ne re-déclenche
pas l'alerte J-3 pour le nouveau cycle. Contrairement à `DemandeDocuments` (dont
l'escalade est le cas d'usage principal justifiant la correction immédiate),
ces deux cas sont plus rares en pratique (une prolongation ou une deuxième
demande de complément) et corriger les trois en une seule vague de fix aurait
dépassé le principe "un seul fix wave" du processus SDD. À traiter si le besoin
se confirme en usage réel — probablement en généralisant le même correctif
(dédup par récence plutôt que par type seul) aux 2 cas restants.
