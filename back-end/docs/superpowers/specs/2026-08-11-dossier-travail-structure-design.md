# Dossier de travail structuré (arborescence normalisée) — Design

Statut : approuvé par l'utilisateur le 2026-08-11. Sixième et dernier sous-chantier du
Lot 4 (Conduite de l'investigation), §11 du plan de travail
(`docs/reference/plan-de-travail-asce-lc.md`). Les sous-chantiers 1/6
(`DemandeDocuments`), 2/6 (`VisiteTerrain`/`PVConstat`), 3/6 (règles métier
`Audition`), 4/6 (correction PV + `RegistreAuditions`) et 5/6 (chaîne de possession
`Attachment`) sont livrés et mergés.

Texte exact (§11, Lot 4) : « Dossier de travail structuré selon l'arborescence
normalisée : administration de la mission, prise de connaissance de l'entité, prise
de connaissance de l'environnement, puis étapes ou entités ou sites ou cycles
comptables. »

**Périmètre volontairement réduit** : ce sous-chantier couvre uniquement la structure
de classement (l'arborescence elle-même et le rattachement des pièces). La check-list
des 22 points du dossier de travail (§11, mentionnée aussi en Lot 5 : « bloquante
avant soumission ») est **hors périmètre** — c'est un contrôle de complétude qui
consomme cette arborescence, pas l'arborescence elle-même ; elle mérite sa propre
conception une fois le Lot 5 abordé.

## Contexte

**Terrain vierge** : aucun code existant ne modélise ce concept (`DossierDeTravail`,
`SectionDossierTravail` ou équivalent) — confirmé par recherche exhaustive dans
`model/entity/`. `Attachment.type` (`AttachmentType` : `PHOTO`/`DOCUMENT`/`VIDEO`/
`AUDIO_EVIDENCE`/`SCREENSHOT`/`OTHER`) est une classification de **nature** du
fichier, pas un rattachement à une section de l'arborescence — aucun chevauchement.

**Décisions utilisateur (2026-08-11)** :
1. Le dossier de travail est une **arborescence de classement des pièces**, pas une
   simple checklist de progression déconnectée des `Attachment` — chaque pièce peut
   être rattachée à une section précise.
2. Les 3 premières sections (administration de la mission, prise de connaissance de
   l'entité, prise de connaissance de l'environnement) sont **fixes** — une seule
   instance par dossier, créées automatiquement, jamais créées ni supprimées par
   l'utilisateur.
3. La 4ᵉ section (« étapes ou entités ou sites ou cycles comptables ») a une
   **organisation variable** : le type de mission détermine si elle s'organise par
   étape, par entité, par site ou par cycle comptable — **un seul choix par dossier**,
   pas par sous-section. Une fois ce choix fait, l'enquêteur crée autant de
   sous-sections `DETAIL` que nécessaire (ex. si `PAR_SITE` : « Site de Ouagadougou »,
   « Site de Bobo-Dioulasso », découvertes au fil de l'enquête, nombre non
   prévisible à l'avance).
4. UX : ne pas forcer le choix d'organisation en amont (au démarrage de
   l'investigation) — le demander seulement au moment où l'enquêteur tente de créer
   sa première section `DETAIL`. Le rattachement d'une pièce à une section est
   **optionnel au dépôt**, jamais bloquant, reclassable après coup. La liste des
   sections retourne toujours les 3 sections fixes même vides, avec un compteur de
   pièces par section (prépare le terrain pour la future check-list du Lot 5 sans
   la construire).

## Objectif

Introduire le classement des pièces d'un dossier dans une arborescence normalisée à 2
niveaux :
1. `SectionDossierTravail` — 4 sections par dossier au maximum en type, dont 3 fixes
   (une instance chacune) et 1 type `DETAIL` (N instances, libellées librement).
2. `Dossier.organisationDetail` — le principe d'organisation retenu pour les sections
   `DETAIL` de ce dossier (immuable une fois défini).
3. `Attachment.section` — rattachement optionnel d'une pièce à une section.

## Architecture

Pas de nouvelle entité racine `DossierDeTravail` — inutile : `SectionDossierTravail`
référence directement `Dossier` (`@ManyToOne`), à l'image de `Attachment.dossier`.
Structure plate à 2 niveaux, pas d'arbre générique récursif : rien dans le texte
source ne demande une profondeur au-delà de section fixe / section détail.

Les 3 sections fixes sont créées automatiquement dans
`InvestigationServiceImpl.start()` — la porte `EQUIPE_CONSTITUEE`/`PLAN_VALIDE` déjà
en place (sous-chantier 6/6 du Lot 3, livré) est le point d'entrée naturel : c'est
exactement le moment où l'investigation démarre réellement et où le dossier de
travail doit exister. Idempotence défensive via `existsByDossierIdAndType` avant
chaque création (bien que `start()` ne soit atteignable qu'une fois par investigation
via la garde `InvestigationStatus.INITIATED`, cette vérification coûte peu et évite
tout doublon si la méthode était un jour rappelée).

## Composants

### 1. Nouveaux enums

```java
package gov.bf.ascelc.univers_audits.enums;

public enum TypeSectionDossierTravail {
    ADMINISTRATION_MISSION,
    PRISE_CONNAISSANCE_ENTITE,
    PRISE_CONNAISSANCE_ENVIRONNEMENT,
    DETAIL
}
```

```java
package gov.bf.ascelc.univers_audits.enums;

public enum OrganisationDetail {
    PAR_ETAPE,
    PAR_ENTITE,
    PAR_SITE,
    PAR_CYCLE_COMPTABLE
}
```

### 2. `Dossier` — champ ajouté

Après le bloc `autoReferralSource` :

```java
    @Enumerated(EnumType.STRING)
    @Column(name = "organisation_detail", length = 25)
    private OrganisationDetail organisationDetail;
```

Nullable — non défini tant que l'enquêteur n'a pas créé de première section `DETAIL`
(voir UX ci-dessus). Immuable une fois défini (voir `SectionDossierTravailService`).

### 3. Nouvelle entité `SectionDossierTravail`

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.TypeSectionDossierTravail;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "section_dossier_travail", indexes = {
        @Index(name = "idx_section_dossier", columnList = "dossier_id"),
        @Index(name = "idx_section_dossier_type", columnList = "dossier_id, type")
})
public class SectionDossierTravail extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 35)
    private TypeSectionDossierTravail type;

    @Column(name = "libelle", length = 255)
    private String libelle;
}
```

`libelle` nullable au niveau colonne : uniquement renseigné pour le type `DETAIL` (les
3 sections fixes n'en ont pas besoin, leur nom vient de leur `type`).

### 4. `Attachment` — champ ajouté

Après le bloc `code` (fin des champs de chaîne de possession du sous-chantier 5/6) :

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private SectionDossierTravail section;
```

Nullable : le dépôt d'une pièce ne doit jamais être bloqué par l'absence de
classement (voir UX ci-dessus).

### 5. `SectionDossierTravailRepository` (nouveau)

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.TypeSectionDossierTravail;
import gov.bf.ascelc.univers_audits.model.entity.SectionDossierTravail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SectionDossierTravailRepository
        extends JpaRepository<SectionDossierTravail, UUID> {

    List<SectionDossierTravail> findByDossierId(UUID dossierId);

    boolean existsByDossierIdAndType(UUID dossierId, TypeSectionDossierTravail type);

    boolean existsByDossierIdAndTypeAndLibelle(
            UUID dossierId, TypeSectionDossierTravail type, String libelle);
}
```

### 6. `AttachmentRepository` — méthode ajoutée

```java
    long countBySectionId(UUID sectionId);
```

Utilisée pour le compteur de pièces par section dans `listerSections()` (voir plus
bas). Nécessite d'ajouter `import java.util.UUID;` si absent (déjà présent, utilisé
par `findByDossierId`).

### 7. Nouveau service `SectionDossierTravailService`

Classe concrète directe (pas d'interface séparée), même style que
`AttachmentStorageService` :

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.OrganisationDetail;
import gov.bf.ascelc.univers_audits.enums.TypeSectionDossierTravail;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.SectionDossierTravail;
import gov.bf.ascelc.univers_audits.repository.AttachmentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.SectionDossierTravailRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SectionDossierTravailService {

    private static final List<TypeSectionDossierTravail> SECTIONS_FIXES = List.of(
            TypeSectionDossierTravail.ADMINISTRATION_MISSION,
            TypeSectionDossierTravail.PRISE_CONNAISSANCE_ENTITE,
            TypeSectionDossierTravail.PRISE_CONNAISSANCE_ENVIRONNEMENT);

    private final SectionDossierTravailRepository sectionRepository;
    private final DossierRepository dossierRepository;
    private final AttachmentRepository attachmentRepository;

    @Transactional
    public void creerSectionsFixes(Dossier dossier) {
        for (TypeSectionDossierTravail type : SECTIONS_FIXES) {
            if (!sectionRepository.existsByDossierIdAndType(dossier.getId(), type)) {
                sectionRepository.save(SectionDossierTravail.builder()
                        .dossier(dossier)
                        .type(type)
                        .build());
            }
        }
        log.info("Sections fixes du dossier de travail creees — dossier: {}",
                dossier.getNumber());
    }

    @Transactional
    public void definirOrganisationDetail(UUID dossierId, OrganisationDetail organisationDetail) {
        Dossier dossier = getDossierOrThrow(dossierId);
        if (dossier.getOrganisationDetail() != null) {
            throw new BusinessException(
                    "Le mode d'organisation du detail est deja defini pour ce dossier : "
                            + dossier.getOrganisationDetail());
        }
        dossier.setOrganisationDetail(organisationDetail);
        dossierRepository.save(dossier);
    }

    @Transactional
    public SectionDossierTravail creerSectionDetail(UUID dossierId, String libelle) {
        Dossier dossier = getDossierOrThrow(dossierId);
        if (dossier.getOrganisationDetail() == null) {
            throw new BusinessException(
                    "Definissez d'abord le mode d'organisation du detail "
                            + "(POST /organisation-detail) avant de creer une section.");
        }
        if (sectionRepository.existsByDossierIdAndTypeAndLibelle(
                dossierId, TypeSectionDossierTravail.DETAIL, libelle)) {
            throw new BusinessException(
                    "Une section DETAIL avec ce libelle existe deja pour ce dossier : "
                            + libelle);
        }
        return sectionRepository.save(SectionDossierTravail.builder()
                .dossier(dossier)
                .type(TypeSectionDossierTravail.DETAIL)
                .libelle(libelle)
                .build());
    }

    public List<SectionDossierTravailResponse> listerSections(UUID dossierId) {
        getDossierOrThrow(dossierId);
        return sectionRepository.findByDossierId(dossierId).stream()
                .map(s -> new SectionDossierTravailResponse(
                        s.getId(), s.getType(), s.getLibelle(),
                        attachmentRepository.countBySectionId(s.getId())))
                .toList();
    }

    public record SectionDossierTravailResponse(
            UUID id, TypeSectionDossierTravail type, String libelle, long nombrePieces) {}

    private Dossier getDossierOrThrow(UUID dossierId) {
        return dossierRepository.findById(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier introuvable : " + dossierId));
    }
}
```

`SectionDossierTravailResponse` en record imbriqué, même patron que
`AttachmentSummary` dans `AttachmentStorageService`.

### 8. `InvestigationServiceImpl.start()` — appel ajouté

Après `Investigation saved = investigationRepository.save(inv);` (ligne ~240
actuelle), avant le `auditRecorder.addObservation(...)` :

```java
        sectionDossierTravailService.creerSectionsFixes(inv.getDossier());
```

Nécessite d'injecter `SectionDossierTravailService` dans `InvestigationServiceImpl`
(champ `final` supplémentaire, `@RequiredArgsConstructor` déjà en place le câble
automatiquement).

### 9. Nouveau `SectionDossierTravailController`

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.enums.OrganisationDetail;
import gov.bf.ascelc.univers_audits.service.SectionDossierTravailService;
import gov.bf.ascelc.univers_audits.service.SectionDossierTravailService.SectionDossierTravailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/dossiers/{dossierId}/dossier-travail")
@RequiredArgsConstructor
public class SectionDossierTravailController {

    private static final String WRITE_ROLES =
            "hasAnyRole('CONTROLEUR_ETAT','AGENT_BRPD','ADMIN_DDIC')";

    private final SectionDossierTravailService sectionDossierTravailService;

    @GetMapping("/sections")
    public ResponseEntity<List<SectionDossierTravailResponse>> listerSections(
            @PathVariable String dossierId) {
        return ResponseEntity.ok(
                sectionDossierTravailService.listerSections(UUID.fromString(dossierId)));
    }

    @PreAuthorize(WRITE_ROLES)
    @PostMapping("/organisation-detail")
    public ResponseEntity<?> definirOrganisationDetail(
            @PathVariable String dossierId,
            @RequestBody Map<String, OrganisationDetail> body) {
        sectionDossierTravailService.definirOrganisationDetail(
                UUID.fromString(dossierId), body.get("organisationDetail"));
        return ResponseEntity.ok().build();
    }

    @PreAuthorize(WRITE_ROLES)
    @PostMapping("/sections")
    public ResponseEntity<SectionDossierTravailResponse> creerSectionDetail(
            @PathVariable String dossierId,
            @RequestBody Map<String, String> body) {
        var section = sectionDossierTravailService.creerSectionDetail(
                UUID.fromString(dossierId), body.get("libelle"));
        return ResponseEntity.ok(new SectionDossierTravailResponse(
                section.getId(), section.getType(), section.getLibelle(), 0));
    }
}
```

**Rôles d'écriture — décision à confirmer** : `CONTROLEUR_ETAT` proposé en tête (ce
sont les contrôleurs d'État qui conduisent les visites terrain et collectent les
pièces selon le texte du Lot 4), `AGENT_BRPD`/`ADMIN_DDIC` en complément
administratif, à l'image du rôle déjà accordé sur `AttachmentController.delete()`.
Lecture (`GET /sections`) ouverte à tout utilisateur authentifié pouvant déjà accéder
au dossier (pas de `@PreAuthorize` dédié ici, cohérent avec `AttachmentController.
uploadFiles()` qui n'en porte pas non plus) — la sécurité de premier niveau reste le
filtrage Spring Security global déjà en place sur `/api/v1/**`.

### 10. `AttachmentController.uploadFiles()` / `AttachmentStorageService.upload()` —
paramètre `sectionId` ajouté

Même patron que `source`/`modeObtention`/`personneRemettante` (sous-chantier 5/6) :
paramètre optionnel, aucun appelant existant cassé.

```java
            @RequestParam(value = "sectionId", required = false) String sectionId,
```

Dans `upload()`, résolution simple (pas de validation d'existence bloquante — un
`sectionId` invalide provoquerait une contrainte FK en base, acceptable ici puisque
ce n'est jamais l'utilisateur final qui saisit cet UUID à la main, toujours le
frontend depuis la liste retournée par `GET /sections`) :

```java
                .section(sectionId != null
                        ? sectionDossierTravailRepository.getReferenceById(UUID.fromString(sectionId))
                        : null)
```

Nécessite d'injecter `SectionDossierTravailRepository` dans `AttachmentStorageService`
(pas encore une dépendance).

### 11. Nouveau endpoint de reclassement `PATCH /api/v1/attachments/{attachmentId}/section`

Dans `AttachmentController` :

```java
    @PatchMapping("/{attachmentId}/section")
    public ResponseEntity<?> reclasser(
            @PathVariable String attachmentId,
            @RequestBody Map<String, String> body) {
        attachmentStorageService.reclasser(
                UUID.fromString(attachmentId), body.get("sectionId"));
        return ResponseEntity.ok().build();
    }
```

Dans `AttachmentStorageService`, nouvelle méthode :

```java
    @Transactional
    public void reclasser(UUID attachmentId, String sectionId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Piece introuvable : " + attachmentId));
        attachment.setSection(sectionId != null
                ? sectionDossierTravailRepository.getReferenceById(UUID.fromString(sectionId))
                : null);
        attachmentRepository.save(attachment);
    }
```

`sectionId` nullable en entrée : passer `null` permet de **déclasser** une pièce
(retour à « non classée »), pas seulement de la reclasser d'une section à une autre.

## Migration

`030-create-section-dossier-travail.sql` :

```sql
--liquibase formatted sql
--changeset dev:030-create-section-dossier-travail

CREATE TABLE section_dossier_travail (
    id            UUID         PRIMARY KEY,
    dossier_id    UUID         NOT NULL REFERENCES dossier(id),
    type          VARCHAR(35)  NOT NULL,
    libelle       VARCHAR(255),
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP,
    created_by_id VARCHAR(100),
    updated_by_id VARCHAR(100)
);

CREATE INDEX idx_section_dossier ON section_dossier_travail (dossier_id);
CREATE INDEX idx_section_dossier_type ON section_dossier_travail (dossier_id, type);
CREATE UNIQUE INDEX idx_section_dossier_detail_libelle
    ON section_dossier_travail (dossier_id, libelle)
    WHERE type = 'DETAIL';

ALTER TABLE dossier ADD COLUMN organisation_detail VARCHAR(25);

ALTER TABLE attachment ADD COLUMN section_id UUID REFERENCES section_dossier_travail(id);
CREATE INDEX idx_attachment_section ON attachment (section_id);

COMMENT ON TABLE section_dossier_travail IS 'Sections de l arborescence normalisee du dossier de travail (Lot 4 sous-chantier 6/6)';
COMMENT ON COLUMN section_dossier_travail.type IS '3 sections fixes creees automatiquement au demarrage de l investigation (une instance chacune), + type DETAIL cree a la demande (N instances)';
COMMENT ON COLUMN section_dossier_travail.libelle IS 'Libelle libre, renseigne uniquement pour le type DETAIL (ex: nom du site/entite/cycle/etape)';
COMMENT ON COLUMN dossier.organisation_detail IS 'Principe d organisation retenu pour les sections DETAIL de ce dossier (PAR_ETAPE/PAR_ENTITE/PAR_SITE/PAR_CYCLE_COMPTABLE) - defini une seule fois, immuable';
COMMENT ON COLUMN attachment.section_id IS 'Classement optionnel de la piece dans une section du dossier de travail - nullable au depot, modifiable apres coup via PATCH /section';
```

Contrainte d'unicité du libellé `DETAIL` en index partiel (`WHERE type = 'DETAIL'`) —
les 3 sections fixes n'ayant pas de `libelle` (toujours `NULL`), un index partiel
restreint aux lignes `DETAIL` évite tout conflit avec les `NULL` des sections fixes
et documente l'intention explicitement, même patron que l'index partiel sur
`attachment.code` du sous-chantier 5/6.

Numérotation : `030`, confirmé libre après `029-add-attachment-chain-of-custody.sql`.

## Gestion des erreurs

Aucune nouvelle classe d'exception. `BusinessException` (déjà utilisée partout dans
ce service) pour : organisation déjà définie, organisation non définie avant création
d'une section `DETAIL`, libellé `DETAIL` dupliqué. `ResourceNotFoundException` pour
dossier/pièce introuvable — même patron que le reste du dépôt.

## Tests

- `SectionDossierTravailServiceTest` (nouveau) :
  - `creerSectionsFixes()` crée bien les 3 sections de type fixe, aucune section
    `DETAIL`.
  - `creerSectionsFixes()` appelé une seconde fois sur le même dossier ne crée pas de
    doublon (idempotence défensive).
  - `definirOrganisationDetail()` réussit quand `organisationDetail` est encore
    `null`.
  - `definirOrganisationDetail()` rejette si déjà défini (immutabilité).
  - `creerSectionDetail()` rejette si `organisationDetail` est encore `null`.
  - `creerSectionDetail()` réussit une fois `organisationDetail` défini, section de
    type `DETAIL` avec le bon libellé.
  - `creerSectionDetail()` rejette un libellé déjà utilisé sur ce dossier.
  - `listerSections()` retourne les 3 sections fixes même sans aucune pièce
    (compteur à 0).
  - `listerSections()` retourne un compteur de pièces correct par section après
    rattachement d'`Attachment`.
- `InvestigationServiceImplTest` (étendu) :
  - `start()` déclenche bien la création des 3 sections fixes pour le dossier de
    l'investigation démarrée (non-régression du flux existant : toutes les
    assertions déjà en place sur `start()` restent valides, ce test s'ajoute).
- `AttachmentStorageServiceTest` (étendu) :
  - `upload()` sans `sectionId` fourni → pièce sauvegardée avec `section = null`
    (non-régression).
  - `upload()` avec `sectionId` explicite → pièce rattachée à la bonne section.
  - `reclasser()` change bien la section d'une pièce déjà déposée.
  - `reclasser()` avec `sectionId = null` déclasse la pièce (retour à `null`).

## Hors périmètre

- Check-list des 22 points du dossier de travail (§11, "bloquante avant soumission",
  explicitement rattachée au Lot 5) — contrôle de complétude qui **consomme**
  l'arborescence construite ici, pas la structure elle-même. Sa conception est
  reportée à l'abord du Lot 5.
- Arbre générique récursif à profondeur illimitée — rien dans le texte source ne le
  demande ; la structure à 2 niveaux (section fixe / section détail) couvre
  entièrement le besoin décrit.
- Suppression des sections `DETAIL` — non demandée, pas de risque métier identifié à
  ne pas la fournir (une section créée par erreur reste inoffensive, vide de pièces
  ou non).
- Renommage d'une section `DETAIL` après création — non demandé ; si un libellé est
  mal saisi, rien n'empêche d'en créer une nouvelle et de reclasser les pièces déjà
  rattachées via `PATCH /section`.
- Validation d'existence de `sectionId` au dépôt (`upload()`) au-delà de la
  contrainte FK base de données — un `sectionId` invalide ne peut provenir que d'un
  bug frontend (l'UUID vient toujours de `GET /sections`), pas d'une saisie
  utilisateur directe ; une `DataIntegrityViolationException` à la contrainte FK est
  un signal suffisant pour ce cas, pas de garde applicative dédiée à ajouter.
