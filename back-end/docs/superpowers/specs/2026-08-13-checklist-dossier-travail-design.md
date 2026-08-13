# Check-list du dossier de travail (22 points) — Design

## Statut

**Lot 5 — Rapport et circuit de validation**, découpé en 4 sous-chantiers :

1. Rapport structuré — **livré et mergé le 2026-08-13** (branche `rapport-structure`,
   migration `031`, spec `docs/superpowers/specs/2026-08-12-rapport-structure-design.md`).
2. **Check-list des 22 points du dossier de travail** ← ce document (en cours)
3. Circuit de validation renforcé (délais, avis et retours motivés sur le circuit DEI →
   Conseiller Juridique → CGE déjà existant) — à venir
4. Requête et inventaire des pièces pour le Parquet (préparés par le conseiller juridique)
   — à venir

Prérequis livrés dont ce sous-chantier dépend : sous-chantier 1/4 (`RapportEnquete`,
`InvestigationServiceImpl.submitReport()` déjà transformé en contrôle de complétude) et
Lot 4 sous-chantier 6/6 (`SectionDossierTravail`).

## Contexte

Le plan de travail ASCE-LC (`docs/reference/plan-de-travail-asce-lc.md`, Lot 5) impose une
« Check-list des 22 points du dossier de travail, bloquante avant soumission ». Le document
de référence de ce dépôt **ne liste pas le contenu des 22 points** — seulement leur nombre
(vérifié par grep sur `docs/reference/plan-de-travail-asce-lc.md` : une seule occurrence,
ligne 370, sans détail). Le manuel de procédures d'enquête source (qui contient
vraisemblablement le détail) est référencé en section E du backlog comme annexe toujours en
attente — il n'est pas dans ce dépôt.

**Décision utilisateur (2026-08-13)** : construire une check-list *configurable en base*
plutôt que de deviner/coder en dur un contenu non vérifié. Précédent direct dans ce
dépôt : `ParametreDelai` (délais réglementaires configurables via
`ParametreDelaiController`, pas codés en dur dans un enum Java) — même raisonnement
appliqué ici à un référentiel dont le contenu exact fait autorité et doit rester
corrigible sans redéploiement.

## Objectif

- Un référentiel `PointChecklistDossierTravail` (liste des points, administrable) —
  indépendant de toute investigation, comme `ParametreDelai`.
- Un état de coche par investigation (`ChecklistDossierTravailCoche`) — qui a coché quoi,
  quand, avec un commentaire optionnel.
- `submitReport()` (déjà un contrôle de complétude depuis le sous-chantier 1/4) gagne une
  troisième vérification : tous les points actifs du référentiel doivent être cochés pour
  cette investigation.

## Hors périmètre

- Contenu réel des 22 points : **non disponible**, voir ci-dessous « Contenu provisoire ».
- Circuit de validation renforcé, requête/inventaire Parquet : sous-chantiers 3, 4 — non
  traités ici.
- Lien entre un point de check-list et une section précise du dossier de travail
  (`TypeSectionDossierTravail`) : pas demandé, pas construit — `categorie` reste un champ
  texte libre, non contraint par un enum, pour ne pas inventer une structure non confirmée.
- Retour motivé / commentaire obligatoire au décochage : appartient au sous-chantier 3/4
  (circuit de validation avec avis et retours motivés) — ici le commentaire est facultatif
  et non structuré.

## Contenu provisoire — point d'attention majeur

Le référentiel est seedé avec **22 lignes placeholder** (`libelle` = "Point de contrôle N —
contenu à confirmer avec le manuel de procédures ASCE-LC", `categorie` = `PROVISOIRE`),
plutôt que laissé vide. Deux raisons :

- Un référentiel vide rendrait le gate de `submitReport()` **vacuously vrai** (aucun point
  actif à cocher = jamais bloquant) — contraire à l'exigence explicite du plan de travail
  ("bloquante avant soumission").
- Le nombre "22" est confirmé par le texte source, contrairement au contenu — seedé un
  compte correct avec un contenu explicitement marqué provisoire est plus honnête qu'un
  contenu deviné qui prétendrait être le vrai texte.

**Conséquence opérationnelle explicite, à confirmer avec l'utilisateur avant déploiement
réel** : tant que `ADMIN_DDIC`/`CGEA` n'ont pas remplacé ce contenu placeholder via
`PUT /api/v1/points-checklist-dossier-travail/{code}`, toute investigation devra faire
cocher 22 points au libellé "à confirmer" avant de pouvoir soumettre un rapport — un choix
délibéré (le gate reste réellement bloquant) mais qui peut surprendre en usage réel si le
contenu n'a pas encore été corrigé. Documenté ici plutôt que caché.

## Architecture

### Décisions de conception

| Décision | Choix retenu | Raison |
|---|---|---|
| Référentiel vs état par investigation | Deux entités séparées, comme `ParametreDelai` (référentiel global) vs `Mandat`/`RapportEnquete` (par investigation) | Le référentiel doit survivre indépendamment des investigations et être corrigible une fois pour toutes |
| Suppression du référentiel | Pas de `DELETE` dur — désactivation via `actif=false` uniquement, même patron que `ParametreDelai` | Conforme au principe §8.2 du plan de travail : "Versionnage de tous les documents, suppression logique uniquement — conservation de toute information" |
| Création du référentiel | `POST` disponible (contrairement à `ParametreDelaiController`, qui n'a que `PUT` sur des codes fixes) | Le contenu réel des 22 points n'est pas connu à la livraison — l'admin doit pouvoir en ajouter/remplacer, pas seulement éditer des lignes déjà seedées |
| État de coche : pré-création ou upsert à la volée | Upsert à la volée par `PUT .../checklist/{pointCode}` — rien n'est pré-créé à l'ouverture de l'investigation | Robuste si le référentiel change en cours de route (nouveau point ajouté après le début de l'investigation = simplement non coché, pas de migration de données à fan-out) |
| Relation `ChecklistDossierTravailCoche` ↔ `Investigation`/`PointChecklistDossierTravail` | `@ManyToOne` LAZY vers chacun, contrainte unique composite `(investigation_id, point_id)` | Une ligne par couple, upsert = `findByInvestigationIdAndPointId` puis create-or-update, même style que `RapportEnqueteService.enregistrerRapport` |
| Contrôle d'accès | `ChecklistDossierTravailService` (état par investigation) appelle `DossierAccessGuard.checkReadAccess` + masquage confidentialité (`isConfidential && !canSeeConfidential()` → liste vide), même patron que `InvestigationServiceImpl.getIncidents` (ligne ~922). Le référentiel global (`PointChecklistDossierTravailController`) n'a **pas** de `DossierAccessGuard` — ce n'est pas une sous-ressource de dossier, même situation que `ParametreDelaiController`. | Directement le correctif déjà acté à la revue finale du sous-chantier 1/4 : comparer au niveau service, pas seulement contrôleur — appliqué dès la conception ici plutôt que découvert à la revue |
| Rôles | Écriture (coche) = `CONTROLEUR_ETAT`/`ADMIN_DDIC`, identique à `RapportEnqueteController.WRITE_ROLES`. Lecture (coche) = `CGEA`/`CGE`/`CONTROLEUR_ETAT`/`MEMBRE_CTADP`/`ADMIN_DDIC`, identique à `READ_ROLES`. Référentiel admin = `ADMIN_DDIC`/`CGEA`, identique à `ParametreDelaiController` | Cohérence avec le sous-chantier voisin (même équipe qui rédige le rapport coche sa propre check-list) et avec le seul précédent de référentiel configurable existant |
| Contrôleur | Deux classes séparées : `ChecklistDossierTravailController` (état par investigation, nesté sous `/investigations/{id}`) et `PointChecklistDossierTravailController` (référentiel global) | Suit le précédent : une ressource = un contrôleur dédié (`RapportEnqueteController`, `SectionDossierTravailController`) ; mélanger référentiel global et sous-ressource d'investigation dans une seule classe romprait la distinction déjà établie par `ParametreDelaiController` vs les contrôleurs de sous-ressources |
| Service | Deux classes concrètes sans interface (`ChecklistDossierTravailService`, `PointChecklistDossierTravailService`), comme `RapportEnqueteService`/`SectionDossierTravailService` | Suit le précédent le plus récent de ce Lot |

## Composants

### 1. Entité `PointChecklistDossierTravail`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/entity/PointChecklistDossierTravail.java`

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "point_checklist_dossier_travail", indexes = {
        @Index(name = "idx_point_checklist_code",
                columnList = "code", unique = true)
})
public class PointChecklistDossierTravail extends AuditEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "libelle", nullable = false, length = 500)
    private String libelle;

    @Column(name = "categorie", length = 100)
    private String categorie;

    @Column(name = "ordre", nullable = false)
    private Integer ordre;

    @Column(name = "actif", nullable = false)
    @Builder.Default
    private Boolean actif = true;
}
```

### 2. Entité `ChecklistDossierTravailCoche`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/entity/ChecklistDossierTravailCoche.java`

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "checklist_dossier_travail_coche", indexes = {
        @Index(name = "idx_checklist_coche_investigation",
                columnList = "investigation_id"),
        @Index(name = "idx_checklist_coche_unique",
                columnList = "investigation_id, point_id", unique = true)
})
public class ChecklistDossierTravailCoche extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_id", nullable = false)
    private PointChecklistDossierTravail point;

    @Column(name = "coche", nullable = false)
    @Builder.Default
    private Boolean coche = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coche_par_id")
    private Agent cochePar;

    @Column(name = "coche_at")
    private Instant cocheAt;

    @Column(name = "commentaire", length = 2000)
    private String commentaire;
}
```

### 3. Repositories

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/repository/PointChecklistDossierTravailRepository.java`

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.PointChecklistDossierTravail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PointChecklistDossierTravailRepository
        extends JpaRepository<PointChecklistDossierTravail, UUID> {
    List<PointChecklistDossierTravail> findByActifTrueOrderByOrdreAsc();
    List<PointChecklistDossierTravail> findAllByOrderByOrdreAsc();
    Optional<PointChecklistDossierTravail> findByCode(String code);
    boolean existsByCode(String code);
}
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/repository/ChecklistDossierTravailCocheRepository.java`

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.ChecklistDossierTravailCoche;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChecklistDossierTravailCocheRepository
        extends JpaRepository<ChecklistDossierTravailCoche, UUID> {
    List<ChecklistDossierTravailCoche> findByInvestigationId(UUID investigationId);
    Optional<ChecklistDossierTravailCoche> findByInvestigationIdAndPointId(
            UUID investigationId, UUID pointId);
    long countByInvestigationIdAndCocheTrue(UUID investigationId);
}
```

### 4. DTOs

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/PointChecklistDossierTravailRequest.java`

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointChecklistDossierTravailRequest {
    @NotBlank(message = "Le libellé est obligatoire")
    private String libelle;
    private String categorie;
    @NotNull(message = "L'ordre est obligatoire")
    private Integer ordre;
    private Boolean actif;
}
```

Pas de `code` dans ce DTO : sur `POST`, généré côté service (`PT-{n}`, prochain numéro
libre) ; sur `PUT /{code}`, le code vient du chemin, pas du corps — même convention que
`ParametreDelaiController.update(String code, ParametreDelaiRequest)`.

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/ChecklistCocheRequest.java`

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChecklistCocheRequest {
    @NotNull(message = "L'état coché/non coché est obligatoire")
    private Boolean coche;
    private String commentaire;
}
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/ChecklistDossierTravailItemResponse.java`

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChecklistDossierTravailItemResponse {
    private UUID pointId;
    private String code;
    private String libelle;
    private String categorie;
    private Integer ordre;
    private boolean coche;
    private String cocheParNom;
    private Instant cocheAt;
    private String commentaire;
}
```

### 5. Service `PointChecklistDossierTravailService` (référentiel)

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/service/PointChecklistDossierTravailService.java`

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.PointChecklistDossierTravailRequest;
import gov.bf.ascelc.univers_audits.model.entity.PointChecklistDossierTravail;
import gov.bf.ascelc.univers_audits.repository.PointChecklistDossierTravailRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PointChecklistDossierTravailService {

    private final PointChecklistDossierTravailRepository repository;

    public List<PointChecklistDossierTravail> findAllActifs() {
        return repository.findByActifTrueOrderByOrdreAsc();
    }

    public List<PointChecklistDossierTravail> findAll() {
        return repository.findAllByOrderByOrdreAsc();
    }

    @Transactional
    public PointChecklistDossierTravail create(PointChecklistDossierTravailRequest request) {
        String code = nextCode();
        PointChecklistDossierTravail point = PointChecklistDossierTravail.builder()
                .code(code)
                .libelle(request.getLibelle())
                .categorie(request.getCategorie())
                .ordre(request.getOrdre())
                .actif(request.getActif() == null || request.getActif())
                .build();
        return repository.save(point);
    }

    @Transactional
    public PointChecklistDossierTravail update(String code, PointChecklistDossierTravailRequest request) {
        PointChecklistDossierTravail point = repository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Point de check-list introuvable : " + code));
        point.setLibelle(request.getLibelle());
        point.setCategorie(request.getCategorie());
        point.setOrdre(request.getOrdre());
        if (request.getActif() != null) {
            point.setActif(request.getActif());
        }
        return repository.save(point);
    }

    private String nextCode() {
        int n = 1;
        String candidate;
        do {
            candidate = String.format("PT-%02d", n++);
        } while (repository.existsByCode(candidate));
        return candidate;
    }
}
```

Note sur `nextCode()` : même limitation de concurrence déjà tolérée par
`AttachmentStorageService.generateUniqueAttachmentCode` (boucle re-vérifiant l'existence,
pas de séquence atomique dédiée) — acceptable ici car la création de points de référentiel
est une opération d'administration rare, pas un flux à volume.

### 6. Service `ChecklistDossierTravailService` (état par investigation)

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/service/ChecklistDossierTravailService.java`

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.ChecklistCocheRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.ChecklistDossierTravailItemResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.ChecklistDossierTravailCocheRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.PointChecklistDossierTravailRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChecklistDossierTravailService {

    private final InvestigationRepository investigationRepository;
    private final PointChecklistDossierTravailRepository pointRepository;
    private final ChecklistDossierTravailCocheRepository cocheRepository;
    private final DossierAccessGuard accessGuard;
    private final AgentContextResolver agentContextResolver;

    @Transactional(readOnly = true)
    public List<ChecklistDossierTravailItemResponse> getChecklist(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return List.of();
        }

        List<PointChecklistDossierTravail> points = pointRepository.findByActifTrueOrderByOrdreAsc();
        Map<UUID, ChecklistDossierTravailCoche> etatParPoint = cocheRepository
                .findByInvestigationId(investigationId).stream()
                .collect(Collectors.toMap(c -> c.getPoint().getId(), c -> c));

        return points.stream()
                .map(point -> toItemResponse(point, etatParPoint.get(point.getId())))
                .toList();
    }

    @Transactional
    public ChecklistDossierTravailItemResponse setCoche(
            UUID investigationId, String pointCode, ChecklistCocheRequest request) {

        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        PointChecklistDossierTravail point = pointRepository.findByCode(pointCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Point de check-list introuvable : " + pointCode));

        ChecklistDossierTravailCoche etat = cocheRepository
                .findByInvestigationIdAndPointId(investigationId, point.getId())
                .orElseGet(() -> ChecklistDossierTravailCoche.builder()
                        .investigation(investigation)
                        .point(point)
                        .build());

        etat.setCoche(request.getCoche());
        etat.setCommentaire(request.getCommentaire());
        if (Boolean.TRUE.equals(request.getCoche())) {
            etat.setCochePar(agentContextResolver.getCurrentAgent());
            etat.setCocheAt(Instant.now());
        } else {
            etat.setCochePar(null);
            etat.setCocheAt(null);
        }

        ChecklistDossierTravailCoche saved = cocheRepository.save(etat);
        log.info("Check-list dossier de travail — investigation {}, point {}, coché={}",
                investigationId, pointCode, request.getCoche());
        return toItemResponse(point, saved);
    }

    /** Utilisé par InvestigationServiceImpl.submitReport() — pas de contrôle d'accès ici,
     *  le contrôle a déjà eu lieu dans submitReport() lui-même. */
    public boolean isComplete(UUID investigationId) {
        long actifs = pointRepository.findByActifTrueOrderByOrdreAsc().size();
        if (actifs == 0) {
            return true;
        }
        long coches = cocheRepository.countByInvestigationIdAndCocheTrue(investigationId);
        return coches >= actifs;
    }

    private ChecklistDossierTravailItemResponse toItemResponse(
            PointChecklistDossierTravail point, ChecklistDossierTravailCoche etat) {
        return ChecklistDossierTravailItemResponse.builder()
                .pointId(point.getId())
                .code(point.getCode())
                .libelle(point.getLibelle())
                .categorie(point.getCategorie())
                .ordre(point.getOrdre())
                .coche(etat != null && Boolean.TRUE.equals(etat.getCoche()))
                .cocheParNom(etat != null && etat.getCochePar() != null
                        ? etat.getCochePar().getNomComplet() : null)
                .cocheAt(etat != null ? etat.getCocheAt() : null)
                .commentaire(etat != null ? etat.getCommentaire() : null)
                .build();
    }

    private Investigation getInvestigationOrThrow(UUID investigationId) {
        return investigationRepository.findById(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + investigationId));
    }
}
```

**Sur `isComplete()`** : compte les points actifs vs le nombre de coches `true` pour cette
investigation, plutôt que d'itérer et comparer point par point — suffisant car
`ChecklistDossierTravailCoche` n'a qu'un état booléen par couple (investigation, point) et
la contrainte unique empêche les doublons ; un point désactivé après avoir été coché ne
compte simplement plus dans `actifs`, cohérent avec le comportement de lecture de
`getChecklist()` qui ne renvoie que les points actifs.

### 7. Contrôleur `ChecklistDossierTravailController`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/controller/ChecklistDossierTravailController.java`

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.ChecklistCocheRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.ChecklistDossierTravailItemResponse;
import gov.bf.ascelc.univers_audits.service.ChecklistDossierTravailService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/checklist")
public class ChecklistDossierTravailController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CONTROLEUR_ETAT','ADMIN_DDIC')";

    private final ChecklistDossierTravailService checklistDossierTravailService;

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<List<ChecklistDossierTravailItemResponse>> getChecklist(
            @PathVariable UUID id) {
        return ResponseEntity.ok(checklistDossierTravailService.getChecklist(id));
    }

    @PutMapping("/{pointCode}")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<ChecklistDossierTravailItemResponse> setCoche(
            @PathVariable UUID id,
            @PathVariable String pointCode,
            @Valid @RequestBody ChecklistCocheRequest request) {

        log.info("Check-list — investigation {}, point {}", id, pointCode);
        return ResponseEntity.ok(
                checklistDossierTravailService.setCoche(id, pointCode, request));
    }
}
```

### 8. Contrôleur `PointChecklistDossierTravailController` (référentiel)

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/controller/PointChecklistDossierTravailController.java`

Même patron exact que `ParametreDelaiController` (`GET` public authentifié, `GET /admin`,
écriture réservée `ADMIN_DDIC`/`CGEA`), avec un `POST` en plus.

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.PointChecklistDossierTravailRequest;
import gov.bf.ascelc.univers_audits.model.entity.PointChecklistDossierTravail;
import gov.bf.ascelc.univers_audits.service.PointChecklistDossierTravailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/points-checklist-dossier-travail")
@RequiredArgsConstructor
public class PointChecklistDossierTravailController {

    private final PointChecklistDossierTravailService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PointChecklistDossierTravail>> getActifs() {
        return ResponseEntity.ok(service.findAllActifs());
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<List<PointChecklistDossierTravail>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<PointChecklistDossierTravail> create(
            @Valid @RequestBody PointChecklistDossierTravailRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<PointChecklistDossierTravail> update(
            @PathVariable String code,
            @Valid @RequestBody PointChecklistDossierTravailRequest request) {
        return ResponseEntity.ok(service.update(code, request));
    }
}
```

Retourne l'entité directement (pas de DTO response dédié), exactement comme
`ParametreDelaiController` — précédent direct dans ce dépôt pour ce type de référentiel
global.

### 9. Modification de `InvestigationServiceImpl.submitReport()`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java`

Injecter `private final ChecklistDossierTravailService checklistDossierTravailService;`
au même endroit que les autres dépendances du constructeur `@RequiredArgsConstructor`.

Ajouter, juste après le contrôle `NoteRecommandations` existant (après la ligne
`if (!note.isComplet()) { throw ... }`, avant `inv.setOutcome(...)`) :

```java
if (!checklistDossierTravailService.isComplete(investigationId)) {
    throw new BusinessException(
            "La check-list du dossier de travail n'est pas entièrement cochée — "
                    + "tous les points actifs doivent être validés avant soumission.");
}
```

Le reste de la méthode (et des 34 autres méthodes de la classe) n'est pas modifié.

## Migration

Fichier : `src/main/resources/db/changelog/migrations/032-create-checklist-dossier-travail.sql`

Numéro confirmé libre (dernier existant : `031`, sous-chantier 1/4).

```sql
--liquibase formatted sql
--changeset dev:032-create-checklist-dossier-travail

CREATE TABLE point_checklist_dossier_travail (
    id            UUID         PRIMARY KEY,
    code          VARCHAR(50)  NOT NULL,
    libelle       VARCHAR(500) NOT NULL,
    categorie     VARCHAR(100),
    ordre         INTEGER      NOT NULL,
    actif         BOOLEAN      NOT NULL DEFAULT TRUE,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP,
    created_by_id VARCHAR(100),
    updated_by_id VARCHAR(100),
    CONSTRAINT uq_point_checklist_code UNIQUE (code)
);

CREATE TABLE checklist_dossier_travail_coche (
    id              UUID      PRIMARY KEY,
    investigation_id UUID     NOT NULL REFERENCES investigation(id),
    point_id        UUID      NOT NULL REFERENCES point_checklist_dossier_travail(id),
    coche           BOOLEAN   NOT NULL DEFAULT FALSE,
    coche_par_id    UUID      REFERENCES agent(id),
    coche_at        TIMESTAMP,
    commentaire     VARCHAR(2000),
    version         BIGINT    NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP,
    created_by_id   VARCHAR(100),
    updated_by_id   VARCHAR(100),
    CONSTRAINT uq_checklist_coche_investigation_point UNIQUE (investigation_id, point_id)
);

CREATE INDEX idx_checklist_coche_investigation
    ON checklist_dossier_travail_coche (investigation_id);

-- Contenu PROVISOIRE : le manuel de procédures ASCE-LC listant les 22 points réels
-- n'est pas disponible dans ce dépôt (voir spec 2026-08-13). Ces libellés doivent être
-- corrigés via PUT /api/v1/points-checklist-dossier-travail/{code} avant tout usage réel.
INSERT INTO point_checklist_dossier_travail (id, code, libelle, categorie, ordre, actif, version, created_at)
SELECT gen_random_uuid(),
       'PT-' || LPAD(n::text, 2, '0'),
       'Point de contrôle ' || n || ' — contenu à confirmer avec le manuel de procédures ASCE-LC',
       'PROVISOIRE',
       n,
       TRUE,
       0,
       now()
FROM generate_series(1, 22) AS n;

COMMENT ON TABLE point_checklist_dossier_travail IS 'Referentiel configurable des points de la check-list du dossier de travail (Lot 5 sous-chantier 2/4) - contenu initial PROVISOIRE, a corriger via l administration avant usage reel';
COMMENT ON TABLE checklist_dossier_travail_coche IS 'Etat de coche par investigation pour chaque point actif du referentiel - bloque submitReport() tant qu un point actif reste non coche';
```

`gen_random_uuid()` confirmé déjà utilisé dans plusieurs migrations existantes (`004`,
`005`, `008`, `010`) — pas de nouvelle dépendance introduite par ce seed.

## Gestion des erreurs

| Cas | Comportement |
|---|---|
| `GET /investigations/{id}/checklist` sur un dossier confidentiel sans privilège | Liste vide (`[]`), pas d'erreur — même convention que `getIncidents` |
| `PUT .../checklist/{pointCode}` avec un code de point inexistant | `ResourceNotFoundException` → 404 |
| `PUT .../checklist/{pointCode}` avec `coche` absent du corps | 400 (validation Bean Validation `@NotNull`) |
| `submitReport()` avec au moins un point actif non coché | `BusinessException` |
| `POST /points-checklist-dossier-travail` | 201, code généré automatiquement (`PT-NN`) |
| `PUT /points-checklist-dossier-travail/{code}` avec un code inexistant | `ResourceNotFoundException` → 404 |
| Désactivation d'un point déjà coché sur une investigation en cours | Autorisé (pas de garde) — le point sort simplement de `getChecklist()`/`isComplete()` pour toutes les investigations, y compris celles où il était déjà coché ; aucune perte de la ligne `ChecklistDossierTravailCoche` elle-même (conservée, juste ignorée) |

## Tests

- **`PointChecklistDossierTravailServiceTest`** (nouveau, Mockito) : `create` génère le
  premier code libre (`PT-01` si vide, `PT-03` si `PT-01`/`PT-02` existent déjà) ; `update`
  lève `ResourceNotFoundException` si le code n'existe pas ; `findAllActifs` ne renvoie que
  les points `actif=true`.
- **`ChecklistDossierTravailServiceTest`** (nouveau, Mockito) :
  - `getChecklist` fusionne référentiel actif + état existant, un point sans ligne de coche
    apparaît `coche=false` ;
  - `getChecklist` renvoie liste vide si dossier confidentiel et agent non privilégié (même
    scénario que le test existant sur `getIncidents`) ;
  - `getChecklist` lève si `checkReadAccess` refuse (agent non habilité, non privilégié) ;
  - `setCoche` crée une ligne la première fois, met à jour la même ensuite (pas de doublon) ;
  - `setCoche(coche=true)` renseigne `cochePar`/`cocheAt` ; `setCoche(coche=false)` les
    remet à `null` ;
  - `isComplete` : `true` si 0 point actif (cas dégénéré, documenté) ; `false` si au moins un
    point actif non coché ; `true` si tous les points actifs sont cochés ; `true` si un point
    coché a ensuite été désactivé (n'entre plus dans le décompte des `actifs`).
- **`InvestigationServiceImplTest`** (existant, à étendre) : nouveau test
  `submitReport_rejetteSiChecklistIncomplete` (stub `checklistDossierTravailService
  .isComplete(...)` → `false`, vérifie `BusinessException`, vérifie qu'aucun état n'est
  modifié) ; le test `submitReport_succeedsAvecRapportEtNoteComplets` existant (ou
  équivalent du sous-chantier 1/4) doit être mis à jour pour stuber `isComplete(...)` →
  `true`, sinon il échouera après ce changement.
- **Entités** : test simple `PointChecklistDossierTravail`/`ChecklistDossierTravailCoche`
  — construction via builder, valeurs par défaut (`actif=true`, `coche=false`).

## Risques et points d'attention pour le plan d'implémentation

- **Contenu provisoire** : le plan d'implémentation doit citer explicitement le texte du
  commentaire SQL et s'assurer que la tâche de migration ne « invente » pas un contenu plus
  élaboré en cours de route — rester sur le placeholder décrit ici, qui est un choix
  délibéré documenté, pas un oubli à combler pendant l'implémentation.
- **Ordre des tâches** : `ChecklistDossierTravailService` doit exister et être injectable
  avant la modification de `InvestigationServiceImpl.submitReport()` — même dépendance
  séquentielle déjà rencontrée sur le sous-chantier 1/4 (RapportEnquete avant modification
  de submitReport).
- **Test existant à identifier** : comme pour le sous-chantier 1/4, le test
  `submitReport_succeedsAvecRapportEtNoteComplets` (ou son nom réel dans
  `InvestigationServiceImplTest` après le sous-chantier 1/4) doit être localisé et son
  contenu cité verbatim dans le plan, pas paraphrasé — il cassera à la compilation/exécution
  si `checklistDossierTravailService` n'est pas stubbé.
