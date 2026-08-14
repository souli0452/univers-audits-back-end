# Requête au Parquet et inventaire des pièces — Design

## Statut

**Lot 5 — Rapport et circuit de validation**, dernier des 4 sous-chantiers :

1. Rapport structuré — livré et mergé le 2026-08-13.
2. Check-list des 22 points du dossier de travail — livré et mergé le 2026-08-13.
3. Circuit de validation renforcé — livré et mergé le 2026-08-14.
4. **Requête et inventaire des pièces pour le Parquet** ← ce document (en cours), **dernier
   sous-chantier du Lot 5**.

## Contexte

Le plan de travail ASCE-LC (`docs/reference/plan-de-travail-asce-lc.md`) mentionne la « requête
et l'inventaire des pièces au Parquet » comme deux documents distincts préparés par le
conseiller juridique (ligne 98), listés comme entités de données séparées aux côtés de
`RapportEnquete`/`NoteRecommandations`/`CircuitValidation`/`ChecklistDossierTravail` (ligne 174),
et comme deux documents générés parmi la liste du §9 (ligne 303). Contrairement à
`RapportEnquete`, le texte source ne détaille aucune structure de champs pour la requête — pas de
grille de rédaction imposée comme celle du rapport (page de titre, méthode, résultats...).

Deux contraintes structurantes extraites du texte source :
- **§77** : « Irrégularité... Ordre administratif, non criminel -> transmission à l'autorité
  hiérarchique, jamais au Parquet. Ferme la branche "requête au Parquet". » — la requête au
  Parquet n'est pertinente que pour une issue de saisine judiciaire, pas pour toute issue.
- **§8.2** : l'index des pièces exigé (« description, provenance, date de remise, caractère
  volontaire, numéro de code ») est déjà entièrement couvert par les champs existants de
  `Attachment` (livrés au Lot 4 sous-chantier 5/6) — `description`, `source`, `uploadedAt`,
  `modeObtention`, `code`.

`InvestigationOutcome.JUDICIAL_REFERRAL` (« Saisine ») est la valeur qui correspond exactement à
la « branche requête au Parquet » — c'est le déclencheur retenu pour ce sous-chantier.

## Objectif

- `RequeteParquet` : entité éditable progressivement (1:1 `Investigation`), même patron que
  `RapportEnquete`/`NoteRecommandations` (sous-chantier 1/4) — contenu texte libre, rédigé par le
  conseiller juridique.
- `GET /investigations/{id}/inventaire-pieces` : vue en lecture seule, dérivée de
  `AttachmentRepository.findByInvestigationId` déjà existant — **aucune nouvelle donnée écrite**,
  restitue les 5 champs déjà exigés par le §8.2.

## Hors périmètre

- **Transmission effective au Parquet** (changement de statut, notification, accusé de
  transmission) : Lot 6 (Post-investigation), pas modélisée ici. Ce sous-chantier ne fait que
  préparer le contenu — aucune transition de `DossierStatus`/`InvestigationStatus` n'est ajoutée.
- **Contrôle bloquant « faits juridiquement avérés »** (§8.3) : c'est une exigence structurelle
  déjà satisfaite par l'architecture existante (masquage, distinction faits/analyse/conclusions
  du rapport) — pas un nouveau gate applicatif à construire ici. Le vrai point de contrôle
  formel (avant transmission réelle) appartient au Lot 6, qui n'existe pas encore.
- **Sélection des pièces à inclure dans l'inventaire** : le texte source ne mentionne aucune
  sélection, seulement un index — l'inventaire couvre systématiquement toutes les pièces de
  l'investigation, pas un sous-ensemble choisi.
- **Génération PDF de la requête/l'inventaire** : le texte source les liste comme « documents
  générés » (§9, aux côtés du rapport et du recepisse déjà générés en PDF ailleurs dans ce
  dépôt), mais aucune maquette n'est disponible (même situation déjà actée pour le rapport
  d'enquête et le recepisse) — reporté à un chantier ultérieur si le besoin PDF est confirmé.

## Architecture

### Décisions de conception

| Décision | Choix retenu | Raison |
|---|---|---|
| `RequeteParquet` : structure des champs | Un seul champ `contenu` (texte libre), comme `NoteRecommandations` | Le texte source ne détaille aucune grille de rédaction pour ce document (contrairement au rapport) — inventer une structure non spécifiée serait fabriquer du contenu légalement significatif sans base |
| Gate de création/édition | Refusé si `investigation.getOutcome() != JUDICIAL_REFERRAL` | Conforme au §77 : les autres issues « ferment la branche requête au Parquet » |
| Verrouillage après décision finale | Refusé si `investigation.getCgeApprovedAt() != null` | Même principe que `RapportEnquete.checkEditable()` (verrouillage à la fin de la phase gouvernante) — ici la phase gouvernante est le circuit de validation (sous-chantier 3/4), dont la fin est marquée par `cgeApprovedAt` |
| `InventairePieces` : entité vs vue calculée | Vue calculée (`GET` seul, pas de nouvelle entité/table) | Les 5 champs exigés par le §8.2 existent déjà tous sur `Attachment` (Lot 4, sous-chantier 5/6) — persister une copie serait une duplication sans valeur ajoutée |
| Rôles lecture (`RequeteParquet` et `InventairePieces`) | `CGEA`,`CGE`,`CONSEILLER_JURIDIQUE`,`CONTROLEUR_ETAT`,`MEMBRE_CTADP`,`ADMIN_DDIC` | Élargit le précédent `RapportEnqueteController.READ_ROLES` (qui n'inclut pas `CONSEILLER_JURIDIQUE`) — ici le CJ est l'auteur réel de la ressource, il doit pouvoir relire ce qu'il vient d'écrire, contrairement au rapport où le CJ n'est qu'approbateur |
| Rôles écriture (`RequeteParquet`) | `CONSEILLER_JURIDIQUE`,`ADMIN_DDIC` | Rôle explicitement nommé dans le texte source pour cette tâche (§ ligne 98) |
| Contrôle d'accès | `checkReadAccess` + masquage confidentialité dans la couche **service**, jamais au contrôleur seul | Correctif déjà acté à la revue finale du sous-chantier 1/4 — appliqué ici dès la conception |
| Emplacement du code | Deux services concrets sans interface (`RequeteParquetService`, `InventairePiecesService`), deux contrôleurs séparés nichés sous `/investigations/{id}` | Suit le précédent le plus proche (`RapportEnqueteService`/`RapportEnqueteController`) |

## Composants

### 1. Entité `RequeteParquet`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/entity/RequeteParquet.java`

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
@Table(name = "requete_parquet")
public class RequeteParquet extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false, unique = true)
    private Investigation investigation;

    @Column(name = "contenu", columnDefinition = "TEXT")
    private String contenu;

    public boolean isComplet() {
        return contenu != null && !contenu.isBlank();
    }
}
```

### 2. Repository

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/repository/RequeteParquetRepository.java`

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.RequeteParquet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RequeteParquetRepository extends JpaRepository<RequeteParquet, UUID> {
    Optional<RequeteParquet> findByInvestigationId(UUID investigationId);
}
```

### 3. DTOs `RequeteParquet`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/RequeteParquetRequest.java`

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequeteParquetRequest {
    private String contenu;
}
```

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/RequeteParquetResponse.java`

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
public class RequeteParquetResponse {
    private UUID id;
    private UUID investigationId;
    private String contenu;
    private boolean complet;
    private Instant createdAt;
    private Instant updatedAt;
}
```

Pas de `@NotBlank` sur `contenu` : un brouillon partiel doit pouvoir être enregistré, même
patron que `RapportEnqueteRequest`.

### 4. Service `RequeteParquetService`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/service/RequeteParquetService.java`

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.InvestigationOutcome;
import gov.bf.ascelc.univers_audits.model.dto.request.RequeteParquetRequest;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.RequeteParquet;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.RequeteParquetRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequeteParquetService {

    private final RequeteParquetRepository requeteParquetRepository;
    private final InvestigationRepository investigationRepository;
    private final DossierAccessGuard accessGuard;

    @Transactional
    public RequeteParquet enregistrer(UUID investigationId, RequeteParquetRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());
        checkEditable(investigation);

        RequeteParquet requete = requeteParquetRepository.findByInvestigationId(investigationId)
                .orElseGet(() -> RequeteParquet.builder().investigation(investigation).build());

        requete.setContenu(request.getContenu());

        RequeteParquet saved = requeteParquetRepository.save(requete);
        log.info("Requête Parquet enregistrée — investigation: {}", investigationId);
        return saved;
    }

    public RequeteParquet getOrThrow(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new ResourceNotFoundException(
                    "Aucune requête Parquet n'a été rédigée pour cette investigation : "
                            + investigationId);
        }

        return requeteParquetRepository.findByInvestigationId(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune requête Parquet n'a été rédigée pour cette investigation : "
                                + investigationId));
    }

    private void checkEditable(Investigation investigation) {
        if (investigation.getOutcome() != InvestigationOutcome.JUDICIAL_REFERRAL) {
            throw new BusinessException(
                    "La requête au Parquet n'est applicable que pour une issue de saisine "
                            + "judiciaire (JUDICIAL_REFERRAL).");
        }
        if (investigation.getCgeApprovedAt() != null) {
            throw new BusinessException(
                    "La requête au Parquet n'est plus modifiable après la décision finale du CGE.");
        }
    }

    private Investigation getInvestigationOrThrow(UUID investigationId) {
        return investigationRepository.findById(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + investigationId));
    }
}
```

**Sur `getOrThrow`** : contrairement à `checkEditable`, la lecture n'est pas restreinte à
`outcome == JUDICIAL_REFERRAL` — si aucune requête n'existe (parce que l'outcome ne l'exigeait
pas), `findByInvestigationId` renvoie simplement `Optional.empty()`, qui produit déjà un 404
naturel. Pas besoin de dupliquer le contrôle d'outcome en lecture.

### 5. Contrôleur `RequeteParquetController`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/controller/RequeteParquetController.java`

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.RequeteParquetRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.RequeteParquetResponse;
import gov.bf.ascelc.univers_audits.model.entity.RequeteParquet;
import gov.bf.ascelc.univers_audits.service.RequeteParquetService;
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
public class RequeteParquetController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CONSEILLER_JURIDIQUE','ADMIN_DDIC')";

    private final RequeteParquetService requeteParquetService;

    @GetMapping("/requete-parquet")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<RequeteParquetResponse> getRequeteParquet(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(toResponse(requeteParquetService.getOrThrow(id)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.noContent().build();
        }
    }

    @PutMapping("/requete-parquet")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<RequeteParquetResponse> putRequeteParquet(
            @PathVariable UUID id,
            @Valid @RequestBody RequeteParquetRequest request) {

        log.info("Enregistrement requête Parquet — investigation {}", id);
        RequeteParquet saved = requeteParquetService.enregistrer(id, request);
        return ResponseEntity.ok(toResponse(saved));
    }

    private RequeteParquetResponse toResponse(RequeteParquet r) {
        return RequeteParquetResponse.builder()
                .id(r.getId())
                .investigationId(r.getInvestigation().getId())
                .contenu(r.getContenu())
                .complet(r.isComplet())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
```

### 6. DTO `InventairePieceItemResponse`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/InventairePieceItemResponse.java`

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.AttachmentSource;
import gov.bf.ascelc.univers_audits.enums.ModeObtention;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventairePieceItemResponse {
    private UUID attachmentId;
    private String code;
    private String description;
    private AttachmentSource source;
    private LocalDateTime uploadedAt;
    private ModeObtention modeObtention;
}
```

`uploadedAt` en `LocalDateTime` (pas `Instant`) — type exact du champ `Attachment.uploadedAt`
existant, à ne pas convertir.

### 7. Service `InventairePiecesService`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/service/InventairePiecesService.java`

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.response.InventairePieceItemResponse;
import gov.bf.ascelc.univers_audits.model.entity.Attachment;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.repository.AttachmentRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventairePiecesService {

    private final InvestigationRepository investigationRepository;
    private final AttachmentRepository attachmentRepository;
    private final DossierAccessGuard accessGuard;

    public List<InventairePieceItemResponse> getInventaire(UUID investigationId) {
        Investigation investigation = investigationRepository.findById(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + investigationId));
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return List.of();
        }

        return attachmentRepository.findByInvestigationId(investigationId).stream()
                .map(this::toItemResponse)
                .toList();
    }

    private InventairePieceItemResponse toItemResponse(Attachment attachment) {
        return InventairePieceItemResponse.builder()
                .attachmentId(attachment.getId())
                .code(attachment.getCode())
                .description(attachment.getDescription())
                .source(attachment.getSource())
                .uploadedAt(attachment.getUploadedAt())
                .modeObtention(attachment.getModeObtention())
                .build();
    }
}
```

Même patron de masquage confidentialité que `ChecklistDossierTravailService.getChecklist`/
`InvestigationServiceImpl.getIncidents` : liste vide plutôt que 404, puisque c'est une
sous-ressource de type liste.

### 8. Contrôleur `InventairePiecesController`

Fichier : `src/main/java/gov/bf/ascelc/univers_audits/controller/InventairePiecesController.java`

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.response.InventairePieceItemResponse;
import gov.bf.ascelc.univers_audits.service.InventairePiecesService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}")
public class InventairePiecesController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";

    private final InventairePiecesService inventairePiecesService;

    @GetMapping("/inventaire-pieces")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<List<InventairePieceItemResponse>> getInventaire(
            @PathVariable UUID id) {
        return ResponseEntity.ok(inventairePiecesService.getInventaire(id));
    }
}
```

## Migration

Fichier : `src/main/resources/db/changelog/migrations/034-create-requete-parquet.sql`

Numéro confirmé libre (dernier existant : `033`).

```sql
--liquibase formatted sql
--changeset dev:034-create-requete-parquet

CREATE TABLE requete_parquet (
    id                UUID      PRIMARY KEY,
    investigation_id  UUID      NOT NULL UNIQUE REFERENCES investigation(id),
    contenu           TEXT,
    version           BIGINT    NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP,
    created_by_id     VARCHAR(100),
    updated_by_id     VARCHAR(100)
);

COMMENT ON TABLE requete_parquet IS 'Requete au Parquet (Lot 5 sous-chantier 4/4) - redigee par le conseiller juridique, uniquement pour une issue JUDICIAL_REFERRAL';
```

Aucune migration nécessaire pour `InventairePieces` (vue calculée, pas de nouvelle table).

## Gestion des erreurs

| Cas | Comportement |
|---|---|
| `PUT /requete-parquet` alors que `outcome != JUDICIAL_REFERRAL` | `BusinessException` |
| `PUT /requete-parquet` alors que `cgeApprovedAt != null` (décision finale déjà rendue) | `BusinessException` |
| `GET /requete-parquet` alors que rien n'a encore été rédigé | `204 No Content` (même convention que `RapportEnqueteController`) |
| `GET /requete-parquet` sur dossier confidentiel sans privilège | `204 No Content` (masquage via `ResourceNotFoundException`, même convention) |
| `GET /inventaire-pieces` sur dossier confidentiel sans privilège | Liste vide (`[]`), pas d'erreur |
| Investigation inexistante sur n'importe quel endpoint | `ResourceNotFoundException` → 404 |
| Accès refusé (agent non habilité, non privilégié) | `BusinessException` (levée par `accessGuard.checkReadAccess`) |

## Tests

- **`RequeteParquetServiceTest`** (nouveau, Mockito, même style que `RapportEnqueteServiceTest`) :
  - `enregistrer_creeUneNouvelleRequeteSiAucuneNExisteEncore` / `enregistrer_metAJourLaRequeteExistanteAuDeuxiemeAppel`
  - `enregistrer_rejetteSiOutcomeNestPasJudicialReferral`
  - `enregistrer_rejetteSiDecisionFinaleDejaRendue` (stub `cgeApprovedAt` non nul)
  - `getOrThrow_leveResourceNotFoundExceptionSiAucuneRequete`
  - `getOrThrow_propageBusinessExceptionSiAccesRefuse`
  - `getOrThrow_leveResourceNotFoundExceptionSiDossierConfidentielEtAgentNonPrivilegie`
- **`InventairePiecesServiceTest`** (nouveau, Mockito) :
  - `getInventaire_mappeLesChampsDesPiecesJointes` (2 `Attachment` avec des `source`/`modeObtention`/`code` différents, vérifie le mapping champ par champ)
  - `getInventaire_renvoieListeVideSiAucunePieceJointe`
  - `getInventaire_renvoieListeVideSiDossierConfidentielEtAgentNonPrivilegie`
  - `getInventaire_leveSiAccesRefuse`
- **Entité** : test simple `RequeteParquet.isComplet()` (cas complet, cas vide, cas `null`),
  même style que `NoteRecommandationsTest`.

## Risques et points d'attention pour le plan d'implémentation

- **Aucun test de contrôleur** : confirmé par grep repo-wide (`find . -iname
  "*ControllerTest.java"` → 0 résultat) — convention déjà établie, ne pas en écrire.
- **Ordre des tâches** : l'entité/repository (`RequeteParquet`) doivent exister avant le
  service, qui doit exister avant le contrôleur — même séquence que tous les sous-chantiers
  précédents de ce Lot. `InventairePieces` n'a aucune dépendance sur `RequeteParquet` (peut
  être développé dans n'importe quel ordre relatif, mais le plan les traite en série par
  simplicité).
- **`InvestigationRepository`/`AttachmentRepository`/`DossierAccessGuard` sont déjà des beans
  Spring existants** — aucune modification requise sur ces classes.
