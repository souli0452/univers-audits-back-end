# Rapport structuré — Design

## Statut

**Lot 5 — Rapport et circuit de validation**, découpé en 4 sous-chantiers :

1. **Rapport structuré** ← ce document (en cours)
2. Check-list des 22 points du dossier de travail (bloquante avant soumission) — à venir
3. Circuit de validation renforcé (délais, avis et retours motivés sur le circuit DEI → Conseiller Juridique → CGE déjà existant) — à venir
4. Requête et inventaire des pièces pour le Parquet (préparés par le conseiller juridique) — à venir

Prérequis livrés dont ce sous-chantier dépend : Lot 4 dans son intégralité, en particulier le
**sous-chantier 6/6 "DossierDeTravail structuré"** (`SectionDossierTravail`,
`Attachment.section`) — les annexes et le cahier de pièces du rapport s'appuient sur ces
structures existantes plutôt que de les dupliquer.

## Contexte

Le plan de travail ASCE-LC (`docs/reference/plan-de-travail-asce-lc.md`, Lot 5) impose une
rédaction structurée du rapport final : page de titre, introduction, méthode suivie,
informations collectées, résultats (faits, quantification du préjudice, réserves),
conclusions, et une **note de recommandations séparée** — la distinction entre faits,
analyse, conclusions et recommandations doit être explicite dans la structure des données,
pas seulement dans un guide de rédaction.

Aujourd'hui, `Investigation` porte trois champs texte libres (`finalReport`, `conclusions`,
`recommendations`) remplis d'un coup par `submitReport()`, sans aucune structure ni
possibilité de rédaction progressive pendant que l'investigation est en cours. Ce
sous-chantier remplace ces trois champs par deux entités dédiées.

## Objectif

- `RapportEnquete` (1:1 avec `Investigation`) : titre, introduction, méthodologie,
  informations collectées, exposé factuel des anomalies, quantification du préjudice,
  réserves (facultatif), conclusions.
- `NoteRecommandations` (1:1 avec `RapportEnquete`) : contenu séparé, dans un document
  distinct — conformément à l'exigence du plan de travail ("recommandations en note
  séparée").
- Les deux entités sont éditables progressivement (créées/mises à jour via `PUT`, en
  brouillon, sans validation de complétude) tant que l'investigation est `IN_PROGRESS`.
- `submitReport()` (endpoint `PATCH /investigations/{id}/submit-report` déjà existant)
  bascule de "j'accepte du texte brut et je termine" à "je vérifie que le rapport et la
  note existent et sont complets, puis je termine" — son rôle se limite désormais à
  enregistrer l'`outcome` et déclencher la transition d'état.

## Hors périmètre (rappel des décisions déjà actées)

- Table des matières : rendue à l'export (PDF/impression), aucun champ dédié.
- Annexes et cahier de pièces : dérivés des `SectionDossierTravail` /
  `Attachment` déjà en place (Lot 4), pas de duplication de données.
- Check-list des 22 points, circuit de validation renforcé (délais, avis motivés),
  requête et inventaire Parquet : sous-chantiers 2, 3, 4 — non traités ici.
- `outcome` (`InvestigationOutcome`) reste porté par `Investigation`, inchangé.
- Le circuit d'approbation existant (`approveDei` / `approveLegalAdvisor` / `approveCge`,
  `InvestigationServiceImpl.java:407-513`) n'est pas modifié par ce sous-chantier : il
  continue de s'appliquer après `submitReport()`, sans changement de comportement.

## Architecture

### Décisions de conception

| Décision | Choix retenu | Raison |
|---|---|---|
| Relation `RapportEnquete` ↔ `Investigation` | `@ManyToOne` LAZY + `@JoinColumn(unique = true)`, comme `Mandat` ↔ `Investigation` | Cohérent avec le pattern 1:1 déjà utilisé dans le code (`Mandat.java`) plutôt qu'un vrai `@OneToOne` |
| Relation `NoteRecommandations` ↔ `RapportEnquete` | Même pattern, clé sur `RapportEnquete` (pas directement sur `Investigation`) | Respecte la décision déjà validée : "note séparée, 1:1 avec le rapport" — la note ne peut exister sans rapport |
| Validation de complétude | Aucune contrainte `NOT NULL` en base sur les champs de contenu ; validation applicative uniquement, au moment de `submitReport()` | Le rapport est rédigé progressivement pendant `IN_PROGRESS` — un brouillon partiel doit pouvoir être sauvegardé |
| Verrouillage après soumission | Pas de champ `soumisAt` dédié sur les deux nouvelles entités — le verrou s'appuie sur `Investigation.status` (déjà `IN_PROGRESS` → `COMPLETED` via `complete()`) | Évite une redondance d'horodatage : `Investigation.reportSubmittedAt` existant capture déjà cet instant |
| Contrôle d'accès des nouveaux endpoints | Rôles `@PreAuthorize` uniquement (mêmes rôles que `submit-report` en écriture, mêmes rôles que `findById` en lecture) — pas de `DossierAccessGuard` | Cohérent avec le reste de `InvestigationController`, qui n'appelle jamais `DossierAccessGuard` (contrairement à `SectionDossierTravailController`, qui est nested sous `/dossiers/{id}` et gère des pièces confidentielles au niveau dossier) |
| Suppression des anciens champs | `Investigation.finalReport` / `conclusions` / `recommendations` supprimés (entité + colonnes + `InvestigationResponse`), pas dépréciés | Remplacement, pas coexistence — décision déjà validée par l'utilisateur ; confirmé par grep qu'aucun autre code (mapper, autre DTO) ne les référence en dehors de `Investigation`/`InvestigationServiceImpl`/`InvestigationUpdateRequest`/`InvestigationResponse` |
| `Investigation.reportSubmittedAt` | Conservé tel quel | Continue de capturer l'instant de soumission, indépendamment du contenu du rapport |
| Contrôleur | Nouvelle classe `RapportEnqueteController`, pas d'ajout à `InvestigationController` (déjà 614 lignes) | Suit le précédent `SectionDossierTravailController` : une ressource imbriquée nouvelle obtient son propre contrôleur |
| Service | Nouvelle classe concrète `@Service RapportEnqueteService` (pas d'interface séparée) | Suit le précédent `SectionDossierTravailService`, qui est aussi une classe concrète sans interface — contrairement à `InvestigationService`, plus ancien et plus large |

## Composants

### 1. Entité `RapportEnquete`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/entity/RapportEnquete.java`

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
@Table(name = "rapport_enquete")
public class RapportEnquete extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false, unique = true)
    private Investigation investigation;

    @Column(name = "titre", columnDefinition = "TEXT")
    private String titre;

    @Column(name = "introduction", columnDefinition = "TEXT")
    private String introduction;

    @Column(name = "methodologie", columnDefinition = "TEXT")
    private String methodologie;

    @Column(name = "informations_collectees", columnDefinition = "TEXT")
    private String informationsCollectees;

    @Column(name = "expose_factuel_anomalies", columnDefinition = "TEXT")
    private String exposeFactuelAnomalies;

    @Column(name = "quantification_prejudice", columnDefinition = "TEXT")
    private String quantificationPrejudice;

    @Column(name = "reserves", columnDefinition = "TEXT")
    private String reserves;

    @Column(name = "conclusions", columnDefinition = "TEXT")
    private String conclusions;

    /** Réserves exclues : c'est le seul champ facultatif du rapport. */
    public boolean isComplet() {
        return isPresent(titre)
                && isPresent(introduction)
                && isPresent(methodologie)
                && isPresent(informationsCollectees)
                && isPresent(exposeFactuelAnomalies)
                && isPresent(quantificationPrejudice)
                && isPresent(conclusions);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
```

Vérifié par grep (`isBlank()` sur `String != null`, `AgentController.java:37`,
`AuditService.java:98` et ailleurs) : c'est la convention native déjà utilisée partout
dans ce projet — pas de dépendance à `commons-lang3` (absente de `pom.xml`).

### 2. Entité `NoteRecommandations`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/entity/NoteRecommandations.java`

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
@Table(name = "note_recommandations")
public class NoteRecommandations extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rapport_enquete_id", nullable = false, unique = true)
    private RapportEnquete rapportEnquete;

    @Column(name = "contenu", columnDefinition = "TEXT")
    private String contenu;

    public boolean isComplet() {
        return contenu != null && !contenu.isBlank();
    }
}
```

### 3. Repositories

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/repository/RapportEnqueteRepository.java`

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.RapportEnquete;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RapportEnqueteRepository extends JpaRepository<RapportEnquete, UUID> {
    Optional<RapportEnquete> findByInvestigationId(UUID investigationId);
}
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/repository/NoteRecommandationsRepository.java`

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.NoteRecommandations;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NoteRecommandationsRepository extends JpaRepository<NoteRecommandations, UUID> {
    Optional<NoteRecommandations> findByRapportEnqueteId(UUID rapportEnqueteId);
}
```

### 4. DTOs

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/RapportEnqueteRequest.java`

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RapportEnqueteRequest {
    private String titre;
    private String introduction;
    private String methodologie;
    private String informationsCollectees;
    private String exposeFactuelAnomalies;
    private String quantificationPrejudice;
    private String reserves;
    private String conclusions;
}
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/RapportEnqueteResponse.java`

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
public class RapportEnqueteResponse {
    private UUID id;
    private UUID investigationId;
    private String titre;
    private String introduction;
    private String methodologie;
    private String informationsCollectees;
    private String exposeFactuelAnomalies;
    private String quantificationPrejudice;
    private String reserves;
    private String conclusions;
    private boolean complet;
    private Instant createdAt;
    private Instant updatedAt;
}
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/NoteRecommandationsRequest.java`

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteRecommandationsRequest {
    private String contenu;
}
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/NoteRecommandationsResponse.java`

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
public class NoteRecommandationsResponse {
    private UUID id;
    private UUID rapportEnqueteId;
    private String contenu;
    private boolean complet;
    private Instant createdAt;
    private Instant updatedAt;
}
```

Aucun de ces DTO de requête ne porte de contrainte `@NotBlank` : un brouillon partiel doit
pouvoir être enregistré tant que l'investigation est `IN_PROGRESS`. La complétude n'est
vérifiée qu'au moment de `submitReport()`.

### 5. Service `RapportEnqueteService`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/service/RapportEnqueteService.java`

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.InvestigationStatus;
import gov.bf.ascelc.univers_audits.model.dto.request.NoteRecommandationsRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RapportEnqueteRequest;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.NoteRecommandations;
import gov.bf.ascelc.univers_audits.model.entity.RapportEnquete;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.NoteRecommandationsRepository;
import gov.bf.ascelc.univers_audits.repository.RapportEnqueteRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RapportEnqueteService {

    private final RapportEnqueteRepository rapportEnqueteRepository;
    private final NoteRecommandationsRepository noteRecommandationsRepository;
    private final InvestigationRepository investigationRepository;

    @Transactional
    public RapportEnquete enregistrerRapport(UUID investigationId, RapportEnqueteRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        checkEditable(investigation);

        RapportEnquete rapport = rapportEnqueteRepository.findByInvestigationId(investigationId)
                .orElseGet(() -> RapportEnquete.builder().investigation(investigation).build());

        rapport.setTitre(request.getTitre());
        rapport.setIntroduction(request.getIntroduction());
        rapport.setMethodologie(request.getMethodologie());
        rapport.setInformationsCollectees(request.getInformationsCollectees());
        rapport.setExposeFactuelAnomalies(request.getExposeFactuelAnomalies());
        rapport.setQuantificationPrejudice(request.getQuantificationPrejudice());
        rapport.setReserves(request.getReserves());
        rapport.setConclusions(request.getConclusions());

        RapportEnquete saved = rapportEnqueteRepository.save(rapport);
        log.info("Rapport d'enquête enregistré — investigation: {}", investigationId);
        return saved;
    }

    public RapportEnquete getRapportOrThrow(UUID investigationId) {
        return rapportEnqueteRepository.findByInvestigationId(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun rapport d'enquête n'a été rédigé pour cette investigation : "
                                + investigationId));
    }

    @Transactional
    public NoteRecommandations enregistrerNote(UUID investigationId, NoteRecommandationsRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        checkEditable(investigation);

        RapportEnquete rapport = rapportEnqueteRepository.findByInvestigationId(investigationId)
                .orElseThrow(() -> new BusinessException(
                        "Rédigez d'abord le rapport d'enquête "
                                + "(PUT /investigations/{id}/rapport) avant la note de recommandations."));

        NoteRecommandations note = noteRecommandationsRepository
                .findByRapportEnqueteId(rapport.getId())
                .orElseGet(() -> NoteRecommandations.builder().rapportEnquete(rapport).build());

        note.setContenu(request.getContenu());

        NoteRecommandations saved = noteRecommandationsRepository.save(note);
        log.info("Note de recommandations enregistrée — investigation: {}", investigationId);
        return saved;
    }

    public NoteRecommandations getNoteOrThrow(UUID investigationId) {
        RapportEnquete rapport = getRapportOrThrow(investigationId);
        return noteRecommandationsRepository.findByRapportEnqueteId(rapport.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune note de recommandations n'a été rédigée pour cette investigation : "
                                + investigationId));
    }

    private void checkEditable(Investigation investigation) {
        if (investigation.getStatus() != InvestigationStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "Le rapport d'enquête et la note de recommandations ne sont modifiables "
                            + "que pendant que l'investigation est en cours.");
        }
    }

    private Investigation getInvestigationOrThrow(UUID investigationId) {
        return investigationRepository.findById(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + investigationId));
    }
}
```

### 6. Contrôleur `RapportEnqueteController`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/controller/RapportEnqueteController.java`

Rôles repris tels quels des endpoints voisins de `InvestigationController` : écriture =
mêmes rôles que `submit-report` (`CONTROLEUR_ETAT`, `ADMIN_DDIC`) ; lecture = mêmes rôles
que `findById` (`CGEA`, `CGE`, `CONTROLEUR_ETAT`, `MEMBRE_CTADP`, `ADMIN_DDIC`).

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.NoteRecommandationsRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RapportEnqueteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.NoteRecommandationsResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.RapportEnqueteResponse;
import gov.bf.ascelc.univers_audits.model.entity.NoteRecommandations;
import gov.bf.ascelc.univers_audits.model.entity.RapportEnquete;
import gov.bf.ascelc.univers_audits.service.RapportEnqueteService;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}")
public class RapportEnqueteController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CONTROLEUR_ETAT','ADMIN_DDIC')";

    private final RapportEnqueteService rapportEnqueteService;

    @GetMapping("/rapport")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<RapportEnqueteResponse> getRapport(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(toResponse(rapportEnqueteService.getRapportOrThrow(id)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.noContent().build();
        }
    }

    @PutMapping("/rapport")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<RapportEnqueteResponse> putRapport(
            @PathVariable UUID id,
            @Valid @RequestBody RapportEnqueteRequest request) {

        log.info("Enregistrement rapport d'enquête — investigation {}", id);
        RapportEnquete saved = rapportEnqueteService.enregistrerRapport(id, request);
        return ResponseEntity.ok(toResponse(saved));
    }

    @GetMapping("/note-recommandations")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<NoteRecommandationsResponse> getNote(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(toResponse(rapportEnqueteService.getNoteOrThrow(id)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.noContent().build();
        }
    }

    @PutMapping("/note-recommandations")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<NoteRecommandationsResponse> putNote(
            @PathVariable UUID id,
            @Valid @RequestBody NoteRecommandationsRequest request) {

        log.info("Enregistrement note de recommandations — investigation {}", id);
        NoteRecommandations saved = rapportEnqueteService.enregistrerNote(id, request);
        return ResponseEntity.ok(toResponse(saved));
    }

    private RapportEnqueteResponse toResponse(RapportEnquete r) {
        return RapportEnqueteResponse.builder()
                .id(r.getId())
                .investigationId(r.getInvestigation().getId())
                .titre(r.getTitre())
                .introduction(r.getIntroduction())
                .methodologie(r.getMethodologie())
                .informationsCollectees(r.getInformationsCollectees())
                .exposeFactuelAnomalies(r.getExposeFactuelAnomalies())
                .quantificationPrejudice(r.getQuantificationPrejudice())
                .reserves(r.getReserves())
                .conclusions(r.getConclusions())
                .complet(r.isComplet())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    private NoteRecommandationsResponse toResponse(NoteRecommandations n) {
        return NoteRecommandationsResponse.builder()
                .id(n.getId())
                .rapportEnqueteId(n.getRapportEnquete().getId())
                .contenu(n.getContenu())
                .complet(n.isComplet())
                .createdAt(n.getCreatedAt())
                .updatedAt(n.getUpdatedAt())
                .build();
    }
}
```

### 7. Modification de `Investigation`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Investigation.java`

Supprimer ces trois champs (et leurs getters/setters Lombok) :

```java
@Column(name = "final_report", columnDefinition = "TEXT")
private String finalReport;
@Column(name = "conclusions", columnDefinition = "TEXT")
private String conclusions;
@Column(name = "recommendations", columnDefinition = "TEXT")
private String recommendations;
```

Tous les autres champs (`outcome`, `reportSubmittedAt`, `deiApprovedAt`/`deiApprovedBy`,
`legalAdvisorApprovedAt`/`legalAdvisorApprovedBy`, `cgeApprovedAt`/`cgeApprovedBy`) et la
méthode `complete()` restent inchangés.

### 8. Modification de `InvestigationResponse`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/InvestigationResponse.java`

Supprimer ces trois lignes :

```java
private String finalReport;
private String conclusions;
private String recommendations;
```

`outcome` et `reportSubmittedAt` restent inchangés. Aucune modification requise dans
`InvestigationMapper` : le mapping MapStruct est par nom de champ sans `@Mapping` explicite
pour ces trois champs — leur suppression symétrique côté entité et DTO suffit.

### 9. Modification de `InvestigationUpdateRequest`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/InvestigationUpdateRequest.java`

Remplacer intégralement le contenu par :

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.InvestigationOutcome;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestigationUpdateRequest {
    @NotNull(message = "Le résultat de l'investigation est obligatoire")
    private InvestigationOutcome outcome;
}
```

Confirmé par grep : cette classe n'est utilisée nulle part ailleurs que
`InvestigationController.submitReport()` / `InvestigationServiceImpl.submitReport()` — sa
réduction ne casse aucun autre appelant.

### 10. Modification de `InvestigationServiceImpl.submitReport()`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java:365-405`

Injecter deux nouvelles dépendances (`private final RapportEnqueteRepository
rapportEnqueteRepository;` et `private final NoteRecommandationsRepository
noteRecommandationsRepository;`) au même endroit que les autres repositories du
constructeur `@RequiredArgsConstructor`.

Remplacer le corps de la méthode :

```java
@Override
@Transactional
public InvestigationResponse submitReport(
        UUID investigationId,
        InvestigationUpdateRequest request,
        String ipAddress) {

    Investigation inv = getInvestigationOrThrow(investigationId);

    if (inv.getStatus() != InvestigationStatus.IN_PROGRESS) {
        throw new BusinessException(
                "Le rapport ne peut être soumis "
                        + "que pour une investigation en cours");
    }

    RapportEnquete rapport = rapportEnqueteRepository.findByInvestigationId(investigationId)
            .orElseThrow(() -> new BusinessException(
                    "Aucun rapport d'enquête n'a été rédigé pour cette investigation. "
                            + "Renseignez-le via PUT /investigations/{id}/rapport avant soumission."));
    if (!rapport.isComplet()) {
        throw new BusinessException(
                "Le rapport d'enquête est incomplet — tous les champs sont obligatoires "
                        + "(les réserves exceptées).");
    }

    NoteRecommandations note = noteRecommandationsRepository
            .findByRapportEnqueteId(rapport.getId())
            .orElseThrow(() -> new BusinessException(
                    "Aucune note de recommandations n'a été rédigée pour cette investigation. "
                            + "Renseignez-la via PUT /investigations/{id}/note-recommandations "
                            + "avant soumission."));
    if (!note.isComplet()) {
        throw new BusinessException("La note de recommandations est vide.");
    }

    inv.setOutcome(request.getOutcome());
    inv.complete();

    Dossier dossier = inv.getDossier();
    dossier.setStatus(DossierStatus.RAPPORT_PRODUIT);
    dossierRepository.save(dossier);

    auditRecorder.recordStatusChange(dossier,
            DossierStatus.EN_INVESTIGATION,
            DossierStatus.RAPPORT_PRODUIT,
            "Rapport d'investigation soumis",
            agentContextResolver.getCurrentAgent(), ipAddress);

    Investigation saved = investigationRepository.save(inv);

    auditRecorder.addObservation(dossier,
            ObservationType.FIELD_FINDING,
            "Rapport final soumis. Conclusions : " + rapport.getConclusions(),
            true, agentContextResolver.getCurrentAgent());

    log.info("Rapport soumis — investigation: {}", investigationId);
    return investigationMapper.toResponse(saved);
}
```

Le reste de `InvestigationServiceImpl` (les 34 autres méthodes, y compris `approveDei` /
`approveLegalAdvisor` / `approveCge` à partir de la ligne 407) n'est pas modifié.

## Migration

Fichier : `src/main/resources/db/changelog/migrations/031-create-rapport-enquete-note-recommandations.sql`

```sql
--liquibase formatted sql
--changeset dev:031-create-rapport-enquete-note-recommandations

CREATE TABLE rapport_enquete (
    id                        UUID      PRIMARY KEY,
    investigation_id          UUID      NOT NULL UNIQUE REFERENCES investigation(id),
    titre                     TEXT,
    introduction              TEXT,
    methodologie              TEXT,
    informations_collectees   TEXT,
    expose_factuel_anomalies  TEXT,
    quantification_prejudice  TEXT,
    reserves                  TEXT,
    conclusions               TEXT,
    version                   BIGINT    NOT NULL DEFAULT 0,
    created_at                TIMESTAMP NOT NULL,
    updated_at                TIMESTAMP,
    created_by_id             VARCHAR(100),
    updated_by_id             VARCHAR(100)
);

CREATE TABLE note_recommandations (
    id                 UUID      PRIMARY KEY,
    rapport_enquete_id UUID      NOT NULL UNIQUE REFERENCES rapport_enquete(id),
    contenu            TEXT,
    version            BIGINT    NOT NULL DEFAULT 0,
    created_at         TIMESTAMP NOT NULL,
    updated_at         TIMESTAMP,
    created_by_id      VARCHAR(100),
    updated_by_id      VARCHAR(100)
);

ALTER TABLE investigation DROP COLUMN final_report;
ALTER TABLE investigation DROP COLUMN conclusions;
ALTER TABLE investigation DROP COLUMN recommendations;

COMMENT ON TABLE rapport_enquete IS 'Rapport d enquete structure (Lot 5 sous-chantier 1/4) - remplace les anciens champs texte libres finalReport/conclusions/recommendations de investigation';
COMMENT ON COLUMN rapport_enquete.reserves IS 'Seul champ facultatif du rapport - les autres sont requis avant soumission (submitReport)';
COMMENT ON TABLE note_recommandations IS 'Note de recommandations, document distinct du rapport d enquete conformement au plan de travail ASCE-LC (Lot 5)';
```

Numéro confirmé libre par `ls db/changelog/migrations/` (dernier existant : `030`).
Aucune wiring manuelle requise : le changelog racine inclut le dossier `migrations/` via
`includeAll`.

**Perte de données actée** : les valeurs déjà présentes dans `final_report` / `conclusions`
/ `recommendations` sur d'éventuelles investigations existantes sont perdues par ce
`DROP COLUMN`. Étant donné que ce projet est en développement actif sans jeu de données de
production, et que le sous-chantier précédent (DossierDeTravail) a déjà acté l'absence de
besoin de backfill à ce stade, aucune migration de reprise de données n'est prévue ici. À
revérifier avant tout déploiement réel, au même titre que le rappel déjà tracé pour le
sous-chantier DossierDeTravail.

## Gestion des erreurs

| Cas | Comportement |
|---|---|
| `PUT /rapport` ou `/note-recommandations` alors que l'investigation n'est pas `IN_PROGRESS` | `BusinessException` — "ne sont modifiables que pendant que l'investigation est en cours" |
| `PUT /note-recommandations` alors qu'aucun `RapportEnquete` n'existe encore | `BusinessException` — invite à créer le rapport d'abord |
| `GET /rapport` ou `/note-recommandations` alors que rien n'a encore été rédigé | `204 No Content` (même convention que `InvestigationController.findByDossierId`) |
| `submitReport()` sans `RapportEnquete` associé | `BusinessException` — bloque la soumission |
| `submitReport()` avec un `RapportEnquete` incomplet (un des 7 champs requis vide) | `BusinessException` |
| `submitReport()` sans `NoteRecommandations` associée, ou avec un contenu vide | `BusinessException` |
| Investigation inexistante (`id` invalide) sur n'importe quel endpoint | `ResourceNotFoundException` → 404, via le pattern déjà établi |

## Tests

- **`RapportEnqueteServiceTest`** (nouveau, Mockito) : `enregistrerRapport` crée un nouveau
  rapport la première fois puis met à jour le même en réappelant avec le même
  `investigationId` ; rejette si `investigation.status != IN_PROGRESS` ;
  `enregistrerNote` rejette si aucun rapport n'existe encore ; crée puis met à jour la
  même note ; `getRapportOrThrow`/`getNoteOrThrow` lèvent `ResourceNotFoundException`
  quand rien n'existe.
- **`RapportEnqueteControllerTest`** ou test d'intégration léger (selon convention déjà
  utilisée pour `SectionDossierTravailController`) : `GET` renvoie 204 quand rien n'est
  écrit, 200 avec le contenu sinon ; `PUT` renvoie 200 avec la ressource à jour.
- **`InvestigationServiceImplTest`** (existant, à étendre) : nouveau test
  `submitReport_rejetteSiAucunRapportRedige` (stub `rapportEnqueteRepository
  .findByInvestigationId` → `Optional.empty()`, vérifie `BusinessException`) ; nouveau
  test `submitReport_rejetteSiRapportIncomplet` (stub un `RapportEnquete` avec un champ
  requis vide) ; nouveau test `submitReport_rejetteSiAucuneNote` ; nouveau test
  `submitReport_succeedsAvecRapportEtNoteComplets` (remplace/adapte l'ancien test qui
  posait `request.getFinalReport()` etc. — à identifier et corriger, car il échouera à la
  compilation une fois `InvestigationUpdateRequest` réduit à `outcome`).
- **Entités** : test unitaire simple `RapportEnquete.isComplet()` /
  `NoteRecommandations.isComplet()` (cas complet, cas avec un champ vide, cas avec
  seulement `reserves` vide → toujours complet).

## Risques et points d'attention pour le plan d'implémentation

- **Ordre de compilation** : comme rencontré sur le sous-chantier DossierDeTravail, la
  suppression des champs de `Investigation`/`InvestigationResponse` et la réécriture de
  `submitReport()` doivent atterrir dans un ordre où le module compile à chaque étape
  intermédiaire — le plan d'implémentation devra regrouper ces changements avec soin
  (probablement en une seule tâche, vu leur couplage fort).
- **Test existant à identifier** : le test actuel qui couvre `submitReport()` avec
  l'ancien comportement (`request.getFinalReport()`, `request.getConclusions()`, etc.)
  doit être localisé dans `InvestigationServiceImplTest.java` et adapté — le plan devra
  citer son nom exact et son contenu actuel verbatim, pas une paraphrase.
