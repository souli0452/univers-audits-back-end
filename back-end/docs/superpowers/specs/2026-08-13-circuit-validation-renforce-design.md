# Circuit de validation renforcé du rapport — Design

## Statut

**Lot 5 — Rapport et circuit de validation**, découpé en 4 sous-chantiers :

1. Rapport structuré — livré et mergé le 2026-08-13.
2. Check-list des 22 points du dossier de travail — livré et mergé le 2026-08-13.
3. **Circuit de validation renforcé** ← ce document (en cours)
4. Requête et inventaire des pièces pour le Parquet — à venir

## Contexte

Le plan de travail ASCE-LC (`docs/reference/plan-de-travail-asce-lc.md`) décrit un circuit de
validation du rapport en 4 étapes (§6, machine à états) : `REVUE_JURIDIQUE -> ANALYSE_DEI ->
APPROBATION_CGEA -> APPROBATION_CGE`, avec des délais par étape (§7) et des transitions de
retour motivées (§6) :

- `ANALYSE_DEI -> REVUE_JURIDIQUE` (problème résoluble à ce niveau)
- `ANALYSE_DEI -> INVESTIGATION_EN_COURS` (approfondissement demandé)
- `APPROBATION_CGE -> ANALYSE_DEI` (non-approbation, avec remarques, justifications et
  recommandations)

Le code existant (`InvestigationServiceImpl.approveDei`/`approveLegalAdvisor`/`approveCge`,
lignes ~434-540) implémente un circuit à **3** étapes seulement (`DEI -> Conseiller Juridique ->
CGE`, confirmé par la précondition de `approveLegalAdvisor` qui exige `deiApprovedAt != null`) —
ni la 4e étape CGEA, ni aucune capacité de rejet/retour n'existent aujourd'hui. Ce circuit à 3
étapes est antérieur à ce Lot et n'a pas été construit par les sous-chantiers 1/4 ou 2/4 (tous
deux notent explicitement qu'ils ne le touchent pas).

## Arbitrages métier actés le 2026-08-13 (voir mémoire projet)

- **Ordre du circuit** (§13 point 5 du plan de travail, C.3.10 vs tableau B.3) : retenue —
  `Conseiller Juridique -> DEI -> CGEA -> CGE` (recommandation par défaut du document,
  confirmée par l'utilisateur).
- **Écart circuit existant vs décision** : le code actuel (DEI avant CJ, pas de CGEA) est
  **corrigé** pour se conformer à l'ordre ci-dessus — décision explicite de l'utilisateur
  (reordonner + ajouter CGEA), malgré le risque de toucher des méthodes déjà stables.
- **Portée des retours motivés** : rejet simple à chaque étape, renvoyant systématiquement à
  l'étape précédente du circuit — **pas** de réouverture de `InvestigationStatus`/
  `DossierStatus` (pas de retour vers `INVESTIGATION_EN_COURS`, la 2e transition de retour
  documentée par le plan pour DEI est explicitement hors périmètre).
- **Délai d'approbation CGE** (§13 point 1 du plan de travail, 10 vs 20 jours) : **reste non
  tranché**. La valeur existante (20, migration `004`) n'est pas modifiée par ce sous-chantier —
  hors périmètre, à trancher séparément si besoin.

## Objectif

- Circuit à 4 étapes dans l'ordre CJ → DEI → CGEA → CGE, chaque étape pouvant approuver ou
  rejeter avec motif obligatoire (sauf CJ, qui n'a pas d'étape antérieure vers laquelle
  renvoyer — cohérent avec le texte source, qui ne documente de retour que depuis DEI et CGE).
- Un rejet à l'étape N efface l'approbation de l'étape N-1, renvoyant le circuit un cran en
  arrière ; aucun rejet ne touche `InvestigationStatus`/`DossierStatus`.
- Délais informatifs par étape (échéance + indicateur de dépassement), calculés à la volée
  depuis l'horodatage de l'étape précédente, même patron dégradé que
  `PlanInvestigation.validationDeadline` (paramètre absent/désactivé → `null`/`false`, jamais
  d'exception).

## Hors périmètre

- Réouverture terrain (`ANALYSE_DEI -> INVESTIGATION_EN_COURS`) : explicitement exclue par
  décision utilisateur.
- Arbitrage du délai CGE (10 vs 20 jours) : non tranché, valeur existante conservée telle quelle.
- Alertes/escalade automatique (J-3, escalade hiérarchique) : ce dépôt n'a aucun scheduler pour
  ce type de mécanisme (seuls 2 `@Scheduled` existent, dans `NotificationServiceImpl`, sans
  rapport avec ce circuit) — mêmes limites déjà actées pour `DemandeDocuments`/
  `PlanInvestigation`. Les échéances restent purement informatives (exposées dans la réponse
  API), aucune notification/tâche planifiée.
- Correction du flag `jours_ouvrables` (purement décoratif partout ailleurs dans ce dépôt,
  jamais lu par la logique métier) : hors périmètre, affecte 12 lignes `parametre_delai`
  existantes, pas seulement celles-ci — mérite son propre chantier déjà noté au backlog.
- Requête et inventaire des pièces pour le Parquet : sous-chantier 4/4, non traité ici.

## Architecture

### Décisions de conception

| Décision | Choix retenu | Raison |
|---|---|---|
| Emplacement du code | Reste dans `InvestigationServiceImpl`/`InvestigationController` | Les 3 méthodes modifiées (`approveDei`/`approveLegalAdvisor`/`approveCge`) y vivent déjà — les extraire vers un nouveau service serait un refactoring hors périmètre et plus risqué que d'étendre sur place |
| Champs `cgeaApprovedAt`/`cgeaApprovedBy` | Mêmes patron exact que les 3 champs existants (`Instant` + `@ManyToOne` `Agent`) sur `Investigation` | Cohérence stricte avec `deiApprovedAt`/`legalAdvisorApprovedAt`/`cgeApprovedAt` déjà en place |
| Rejet : DTO vs `@RequestParam` | `@RequestParam String motif` au contrôleur, contrôle manuel (`motif == null \|\| motif.isBlank()` → `BusinessException`) dans le service | Suit exactement le précédent le plus proche (`approveCge` a déjà `@RequestParam String reason`) plutôt que d'introduire un nouveau DTO à un seul champ — `ProcedureUrgenceDecisionRequest.rejeterProcedureUrgence` valide aussi le motif manuellement en service, pas via Bean Validation |
| Pas de `rejectLegal` | CJ (1re étape) n'a pas de méthode de rejet | Le texte source (§6) ne documente de transition de retour que depuis `ANALYSE_DEI` et `APPROBATION_CGE` — aucune depuis `REVUE_JURIDIQUE`. Généraliser un rejet à CJ inventerait une transition non documentée sans destination cohérente (il n'y a pas d'étape antérieure vers laquelle renvoyer sans rouvrir le statut, explicitement exclu) |
| Rôles des nouvelles méthodes | `approveCgea`/`rejectDei` = `CGEA`,`ADMIN_DDIC` (même rôle que `approveDei` existant) ; `rejectCgea` = `CGEA`,`ADMIN_DDIC` ; `rejectCge` = `CGE`,`ADMIN_DDIC` | Reprend exactement les rôles déjà assignés aux étapes homologues. Le chevauchement CGEA sur deux étapes consécutives (DEI et CGEA) n'est pas une invention de ce sous-chantier : `approveDei` est déjà gated `CGEA`,`ADMIN_DDIC` aujourd'hui — ce dépôt n'a pas de rôle DEI dédié (même approximation déjà actée pour `PlanInvestigation.valider`) |
| Délais : nouveaux codes | `REVUE_CJ_RAPPORT` (10j), `ANALYSE_DEI_RAPPORT` (15j), `APPROBATION_CGEA_RAPPORT` (10j) — nouveaux ; `APPROBATION_CGE` réutilisé tel quel (déjà seedé, valeur 20) | Valeurs exactes du §7 du plan de travail ; ne pas dupliquer un code déjà existant pour la 4e étape |
| Calcul des échéances | Nouvelle méthode privée dans `InvestigationServiceImpl`, même patron try/catch que `toPlanInvestigationResponse` (ligne ~1262-1283) : `ResourceNotFoundException` → `log.warn` + `null`, jamais de propagation | Patron déjà établi et testé dans ce même fichier pour un besoin identique |
| Point d'injection dans la réponse | `buildResponseWithFreshMembers` (ligne ~1124, déjà le point d'enrichissement unique de `InvestigationResponse`) | `approveDei`/`approveLegalAdvisor`/`approveCge` appellent aujourd'hui `investigationMapper.toResponse(saved)` directement (pas le wrapper) — corrigé au passage pour qu'ils passent tous par `buildResponseWithFreshMembers`, qui portera désormais aussi le calcul des échéances. Effet de bord positif accepté : les réponses d'approbation exposeront aussi `members`/`memberCount` à jour, ce qu'elles ne faisaient pas avant (gap préexistant, corrigé en touchant de toute façon ces mêmes méthodes) |

## Composants

### 1. Modification de `Investigation`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Investigation.java`

Ajouter, juste après le bloc `cgeApprovedBy` existant (ligne ~100) :

```java
    @Column(name = "cgea_approved_at")
    private Instant cgeaApprovedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cgea_approved_by_id")
    private Agent cgeaApprovedBy;
```

Aucun autre champ de cette entité n'est modifié.

### 2. Migration

Fichier : `src/main/resources/db/changelog/migrations/033-circuit-validation-renforce.sql`

Numéro confirmé libre (dernier existant : `032`).

```sql
--liquibase formatted sql
--changeset dev:033-circuit-validation-renforce

ALTER TABLE investigation ADD COLUMN cgea_approved_at TIMESTAMP;
ALTER TABLE investigation ADD COLUMN cgea_approved_by_id UUID REFERENCES agent(id);

INSERT INTO parametre_delai (id, code, libelle, valeur_jours, jours_ouvrables, actif, version, created_at)
VALUES
    (gen_random_uuid(), 'REVUE_CJ_RAPPORT', 'Délai de revue du rapport par le conseiller juridique', 10, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'ANALYSE_DEI_RAPPORT', 'Délai d''analyse du rapport par le DEI', 15, TRUE, TRUE, 0, now()),
    (gen_random_uuid(), 'APPROBATION_CGEA_RAPPORT', 'Délai d''approbation du rapport par le CGEA', 10, TRUE, TRUE, 0, now());

COMMENT ON COLUMN investigation.cgea_approved_at IS 'Circuit de validation du rapport (Lot 5 sous-chantier 3/4) - 3e etape, entre DEI et CGE';
```

Aucune donnée existante n'est perdue (ajout de colonnes nullable, pas de `DROP`). Le seed
`APPROBATION_CGE` (migration `004`, valeur `20`) n'est pas modifié.

### 3. Réordonnancement et ajout du circuit — `InvestigationServiceImpl`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java`

#### 3.1 `approveLegalAdvisor` (1re étape désormais — aucune précondition d'approbation)

Remplacer le corps actuel (lignes ~458-483) :

```java
    @Override
    @Transactional
    public InvestigationResponse approveLegalAdvisor(
            UUID investigationId, String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        if (inv.getStatus() != InvestigationStatus.COMPLETED) {
            throw new BusinessException(
                    "La revue du conseiller juridique n'est possible qu'après soumission du rapport.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        inv.setLegalAdvisorApprovedAt(Instant.now());
        inv.setLegalAdvisorApprovedBy(agent);
        Investigation saved = investigationRepository.save(inv);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.ADMISSIBILITY_ANALYSIS,
                "Rapport approuvé par le Conseiller Juridique "
                        + "(délai légal : 10 jours ouvrables).",
                true, agent);

        log.info("Conseiller juridique approuvé — investigation: {}", investigationId);
        return buildResponseWithFreshMembers(saved, investigationId);
    }
```

Changement : la précondition passe de « aucune » (implicite) à un contrôle explicite sur
`InvestigationStatus.COMPLETED` (identique à celui qu'avait `approveDei` avant ce
sous-chantier, puisque CJ est désormais la 1re étape) ; le retour utilise
`buildResponseWithFreshMembers` au lieu de `investigationMapper.toResponse` direct.

#### 3.2 `approveDei` (2e étape désormais — précondition CJ)

Remplacer le corps actuel (lignes ~434-456) :

```java
    @Override
    @Transactional
    public InvestigationResponse approveDei(UUID investigationId, String ipAddress) {
        Investigation inv = getInvestigationOrThrow(investigationId);

        if (inv.getLegalAdvisorApprovedAt() == null) {
            throw new BusinessException(
                    "Le rapport doit d'abord être approuvé par le Conseiller Juridique.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        inv.setDeiApprovedAt(Instant.now());
        inv.setDeiApprovedBy(agent);
        Investigation saved = investigationRepository.save(inv);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Rapport approuvé par le DEI (délai légal : 15 jours ouvrables).",
                true, agent);

        log.info("DEI approuvé — investigation: {}", investigationId);
        return buildResponseWithFreshMembers(saved, investigationId);
    }
```

Changement : précondition inversée (`legalAdvisorApprovedAt != null` au lieu de
`status == COMPLETED`).

#### 3.3 `approveCgea` (nouvelle 3e étape)

Ajouter juste après `approveDei` :

```java
    @Override
    @Transactional
    public InvestigationResponse approveCgea(UUID investigationId, String ipAddress) {
        Investigation inv = getInvestigationOrThrow(investigationId);

        if (inv.getDeiApprovedAt() == null) {
            throw new BusinessException(
                    "Le rapport doit d'abord être approuvé par le DEI.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        inv.setCgeaApprovedAt(Instant.now());
        inv.setCgeaApprovedBy(agent);
        Investigation saved = investigationRepository.save(inv);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Rapport approuvé par le CGEA (délai légal : 10 jours ouvrables).",
                true, agent);

        log.info("CGEA approuvé — investigation: {}", investigationId);
        return buildResponseWithFreshMembers(saved, investigationId);
    }
```

#### 3.4 `approveCge` (4e étape — précondition CGEA au lieu de DEI+CJ)

Modifier uniquement le bloc de préconditions (lignes ~491-501) et la dernière ligne de retour
(ligne ~539) ; le reste du corps (gestion `outcome`, transition `DossierStatus`, audit) est
**inchangé** :

```java
        Investigation inv = getInvestigationOrThrow(investigationId);

        if (inv.getCgeaApprovedAt() == null) {
            throw new BusinessException(
                    "Le rapport doit être approuvé par le CGEA avant la décision CGE.");
        }
```

(Supprime les deux anciens contrôles `deiApprovedAt`/`legalAdvisorApprovedAt`, remplacés par
celui-ci seul — DEI et CJ sont déjà garantis approuvés puisque ce sont des préconditions
transitives de `cgeaApprovedAt != null`.)

Dernière ligne de la méthode, remplacer :

```java
        return investigationMapper.toResponse(saved);
```

par :

```java
        return buildResponseWithFreshMembers(saved, investigationId);
```

### 4. Rejet motivé — 3 nouvelles méthodes

Ajouter dans `InvestigationServiceImpl`, après `approveCge` :

```java
    @Override
    @Transactional
    public InvestigationResponse rejectDei(
            UUID investigationId, String motif, String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        if (inv.getLegalAdvisorApprovedAt() == null) {
            throw new BusinessException(
                    "Le rapport doit d'abord être approuvé par le Conseiller Juridique.");
        }
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Le motif du rejet est obligatoire.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        inv.setLegalAdvisorApprovedAt(null);
        inv.setLegalAdvisorApprovedBy(null);
        Investigation saved = investigationRepository.save(inv);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Rapport rejeté par le DEI, renvoyé au Conseiller Juridique. Motif : " + motif,
                true, agent);

        log.info("DEI rejeté — investigation: {}", investigationId);
        return buildResponseWithFreshMembers(saved, investigationId);
    }

    @Override
    @Transactional
    public InvestigationResponse rejectCgea(
            UUID investigationId, String motif, String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        if (inv.getDeiApprovedAt() == null) {
            throw new BusinessException(
                    "Le rapport doit d'abord être approuvé par le DEI.");
        }
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Le motif du rejet est obligatoire.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        inv.setDeiApprovedAt(null);
        inv.setDeiApprovedBy(null);
        Investigation saved = investigationRepository.save(inv);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Rapport rejeté par le CGEA, renvoyé au DEI. Motif : " + motif,
                true, agent);

        log.info("CGEA rejeté — investigation: {}", investigationId);
        return buildResponseWithFreshMembers(saved, investigationId);
    }

    @Override
    @Transactional
    public InvestigationResponse rejectCge(
            UUID investigationId, String motif, String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        if (inv.getCgeaApprovedAt() == null) {
            throw new BusinessException(
                    "Le rapport doit être approuvé par le CGEA avant la décision CGE.");
        }
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Le motif du rejet est obligatoire.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        inv.setCgeaApprovedAt(null);
        inv.setCgeaApprovedBy(null);
        Investigation saved = investigationRepository.save(inv);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Rapport rejeté par le CGE, renvoyé au CGEA. Motif : " + motif,
                true, agent);

        log.info("CGE rejeté — investigation: {}", investigationId);
        return buildResponseWithFreshMembers(saved, investigationId);
    }
```

**Aucune de ces 3 méthodes ne touche `Investigation.status` ni `Dossier.status`** — conforme à
la décision actée. `ObservationType.INTERNAL_NOTE` réutilisé (même type que `approveDei`, pas de
nouveau type d'observation).

### 5. Interface `InvestigationService`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/service/InvestigationService.java`

Ajouter 4 signatures (à côté de `approveDei`/`approveLegalAdvisor`/`approveCge` existantes) :

```java
    InvestigationResponse approveCgea(UUID investigationId, String ipAddress);
    InvestigationResponse rejectDei(UUID investigationId, String motif, String ipAddress);
    InvestigationResponse rejectCgea(UUID investigationId, String motif, String ipAddress);
    InvestigationResponse rejectCge(UUID investigationId, String motif, String ipAddress);
```

### 6. Échéances par étape — calcul et exposition

#### 6.1 `InvestigationResponse`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/InvestigationResponse.java`

Ajouter, après `cgeApprovedAt` :

```java
    private Instant cgeaApprovedAt;
    private Instant cjRevueDeadline;
    private Boolean cjRevueOverdue;
    private Instant deiAnalyseDeadline;
    private Boolean deiAnalyseOverdue;
    private Instant cgeaApprobationDeadline;
    private Boolean cgeaApprobationOverdue;
    private Instant cgeApprobationDeadline;
    private Boolean cgeApprobationOverdue;
```

`cgeaApprovedAt` mappé automatiquement par nom (MapStruct), même mécanisme que
`deiApprovedAt`/`legalAdvisorApprovedAt`/`cgeApprovedAt` déjà en place — aucune modification de
`InvestigationMapper` nécessaire pour ce seul champ. Les 8 champs `*Deadline`/`*Overdue` sont
calculés en dehors du mapper (voir 6.2), comme `overdue`/`remainingDays` le sont déjà.

#### 6.2 Calcul dans `buildResponseWithFreshMembers`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java`

Modifier `buildResponseWithFreshMembers` (lignes ~1124-1140) :

```java
    private InvestigationResponse buildResponseWithFreshMembers(
            Investigation inv, UUID investigationId) {

        InvestigationResponse response = investigationMapper.toResponse(inv);

        List<InvestigationMember> freshMembers =
                memberRepository.findByInvestigationIdAndActiveTrue(investigationId);

        List<InvestigationMemberResponse> memberResponses = freshMembers.stream()
                .map(investigationMapper::toMemberResponse)
                .toList();

        response.setMembers(memberResponses);
        response.setMemberCount(memberResponses.size());

        fillCircuitValidationDeadlines(response, inv);

        return response;
    }

    private void fillCircuitValidationDeadlines(InvestigationResponse response, Investigation inv) {
        if (inv.getReportSubmittedAt() != null) {
            response.setCjRevueDeadline(
                    resolveDeadline(inv.getReportSubmittedAt(), "REVUE_CJ_RAPPORT"));
            response.setCjRevueOverdue(isOverdue(
                    response.getCjRevueDeadline(), inv.getLegalAdvisorApprovedAt()));
        }
        if (inv.getLegalAdvisorApprovedAt() != null) {
            response.setDeiAnalyseDeadline(
                    resolveDeadline(inv.getLegalAdvisorApprovedAt(), "ANALYSE_DEI_RAPPORT"));
            response.setDeiAnalyseOverdue(isOverdue(
                    response.getDeiAnalyseDeadline(), inv.getDeiApprovedAt()));
        }
        if (inv.getDeiApprovedAt() != null) {
            response.setCgeaApprobationDeadline(
                    resolveDeadline(inv.getDeiApprovedAt(), "APPROBATION_CGEA_RAPPORT"));
            response.setCgeaApprobationOverdue(isOverdue(
                    response.getCgeaApprobationDeadline(), inv.getCgeaApprovedAt()));
        }
        if (inv.getCgeaApprovedAt() != null) {
            response.setCgeApprobationDeadline(
                    resolveDeadline(inv.getCgeaApprovedAt(), "APPROBATION_CGE"));
            response.setCgeApprobationOverdue(isOverdue(
                    response.getCgeApprobationDeadline(), inv.getCgeApprovedAt()));
        }
    }

    private Instant resolveDeadline(Instant from, String delaiCode) {
        try {
            int delaiJours = parametreDelaiService.resolveDelaiJours(delaiCode);
            return from.plusSeconds((long) delaiJours * 24 * 3600);
        } catch (ResourceNotFoundException e) {
            log.warn("Délai {} indisponible — échéance non calculée : {}", delaiCode, e.getMessage());
            return null;
        }
    }

    private boolean isOverdue(Instant deadline, Instant completedAt) {
        return deadline != null && completedAt == null && Instant.now().isAfter(deadline);
    }
```

`resolveDeadline`/`isOverdue` sont volontairement génériques (réutilisés pour les 4 étapes) —
`toPlanInvestigationResponse` (ligne ~1262) n'a qu'un seul appel et ne les factorise pas
aujourd'hui, ne pas le retoucher pour éviter tout risque de régression sur du code déjà stable
hors périmètre de ce sous-chantier.

### 7. Contrôleur `InvestigationController`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/controller/InvestigationController.java`

Aucune modification de `approve-dei`/`approve-legal`/`approve-cge` (mêmes chemins, mêmes rôles,
seul le comportement service change). Ajouter 4 endpoints après `approve-cge` (ligne ~300) :

```java
    @PatchMapping("/{id}/approve-cgea")
    @PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> approveCgea(
            @PathVariable UUID id,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Approbation CGEA — investigation {}", id);
        InvestigationResponse result = investigationService.approveCgea(
                id, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "APPROUVER_CGEA", "INVESTIGATION", id.toString(),
                "Approbation CGEA", AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/reject-dei")
    @PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> rejectDei(
            @PathVariable UUID id,
            @RequestParam String motif,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Rejet DEI — investigation {}", id);
        InvestigationResponse result = investigationService.rejectDei(
                id, motif, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "REJETER_DEI", "INVESTIGATION", id.toString(),
                "Rejet DEI — " + motif, AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/reject-cgea")
    @PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> rejectCgea(
            @PathVariable UUID id,
            @RequestParam String motif,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Rejet CGEA — investigation {}", id);
        InvestigationResponse result = investigationService.rejectCgea(
                id, motif, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "REJETER_CGEA", "INVESTIGATION", id.toString(),
                "Rejet CGEA — " + motif, AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/reject-cge")
    @PreAuthorize("hasAnyRole('CGE','ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> rejectCge(
            @PathVariable UUID id,
            @RequestParam String motif,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Rejet CGE — investigation {}", id);
        InvestigationResponse result = investigationService.rejectCge(
                id, motif, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "REJETER_CGE", "INVESTIGATION", id.toString(),
                "Rejet CGE — " + motif, AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
    }
```

Pas de `@NotBlank`/`@Valid` sur `motif` (simple `@RequestParam String`, comme `reason` sur
`approve-cge` existant) — la validation « obligatoire » est déjà portée par le service (§4),
cohérent avec le seul précédent comparable (`ProcedureUrgenceDecisionRequest`, validé en
service, pas en Bean Validation).

## Gestion des erreurs

| Cas | Comportement |
|---|---|
| `approve-legal` alors que `status != COMPLETED` | `BusinessException` |
| `approve-dei` alors que `legalAdvisorApprovedAt == null` | `BusinessException` |
| `approve-cgea` alors que `deiApprovedAt == null` | `BusinessException` |
| `approve-cge` alors que `cgeaApprovedAt == null` | `BusinessException` |
| `reject-dei`/`reject-cgea`/`reject-cge` sur une étape pas encore atteinte (même précondition que l'approve correspondant) | `BusinessException` |
| `reject-*` sans motif ou motif vide | `BusinessException` — "Le motif du rejet est obligatoire." |
| Délai indisponible (paramètre désactivé/absent) pour le calcul d'une échéance | Dégradation silencieuse — `null`/`null`, jamais d'exception, `log.warn` |
| Investigation inexistante | `ResourceNotFoundException` → 404, pattern déjà établi |

## Tests

**Constat vérifié par grep (`grep -rn "approveDei\|approveLegalAdvisor\|approveCge"
src/test/`) : aucun test n'existe aujourd'hui pour ces 3 méthodes, dans aucun fichier.** Ce
sous-chantier est donc le premier à les tester — pas de test existant à casser/adapter sur ces
3 méthodes précises (contrairement à ce qu'un parallèle avec le sous-chantier 1/4 aurait
suggéré). Seul `submitReport` a des tests existants (voir sous-chantier 2/4, déjà stables et
non affectés par ce sous-chantier — `submitReport` ne touche à aucun champ `*ApprovedAt`).

- **`InvestigationServiceImplTest`** (existant, à étendre) :
  - `approveLegalAdvisor_rejetteSiStatusNestPasCompleted` / `approveLegalAdvisor_succeeds`
    (nouveaux — 1re étape du circuit)
  - `approveDei_rejetteSiConseillerJuridiqueNaPasApprouve` / `approveDei_succeedsApresApprobationCj`
    (nouveaux, précondition inversée par rapport au code actuel)
  - `approveCgea_rejetteSiDeiNaPasApprouve` / `approveCgea_succeedsApresApprobationDei` (nouveaux)
  - `approveCge_rejetteSiCgeaNaPasApprouve` / `approveCge_succeedsApresApprobationCgea` (nouveaux —
    remplacent la logique de précondition double DEI+CJ qui n'était de toute façon jamais testée)
  - `rejectDei_rejetteSiConseillerJuridiqueNaPasApprouve` / `rejectDei_rejetteSiMotifVide` /
    `rejectDei_remetLegalAdvisorApprovedAtANull`
  - `rejectCgea_*` / `rejectCge_*` (mêmes 3 cas, mêmes noms adaptés)
  - Tests d'échéance : `fillCircuitValidationDeadlines` (ou la méthode publique qui l'expose) —
    échéance CJ calculée depuis `reportSubmittedAt` quand `legalAdvisorApprovedAt == null` ;
    `cjRevueOverdue = true` seulement si `Instant.now()` dépasse l'échéance ET
    `legalAdvisorApprovedAt` toujours `null` ; dégradation vers `null` si
    `parametreDelaiService.resolveDelaiJours` lève `ResourceNotFoundException` (mock
    `ParametreDelaiService`, pas `ParametreDelaiRepository` — c'est déjà l'injection existante
    dans ce fichier).

## Risques et points d'attention pour le plan d'implémentation

- **Absence de tests existants sur le circuit d'approbation, vérifiée par grep** (voir section
  Tests) — pas de risque de casse sur ces 3 méthodes précises, mais aussi aucun filet de
  sécurité préexistant : le plan doit couvrir le comportement complet (pas seulement le delta),
  puisque personne ne l'a jamais testé avant ce sous-chantier.
- **Ordre des tâches** : les champs `cgeaApprovedAt`/`cgeaApprovedBy` (entité + migration)
  doivent exister avant toute méthode qui les utilise — même dépendance séquentielle que sur
  les sous-chantiers précédents.
- **`buildResponseWithFreshMembers` est appelé depuis plusieurs endroits** (`findById`,
  `submitReport`, et désormais les 7 méthodes du circuit) — vérifier par grep qu'aucun appelant
  existant ne suppose que cette méthode n'a pas d'effet de bord sur les échéances avant de la
  modifier.
