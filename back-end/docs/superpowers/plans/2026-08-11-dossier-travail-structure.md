# Dossier de travail structuré (arborescence normalisée) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduire le classement des pièces (`Attachment`) dans une arborescence de dossier de travail à 2 niveaux — 3 sections fixes créées automatiquement au démarrage de l'investigation, plus des sections `DETAIL` créées à la demande selon un principe d'organisation choisi une fois par dossier.

**Architecture:** `SectionDossierTravail` référence directement `Dossier` (`@ManyToOne`), pas de nouvelle entité racine. `Dossier` gagne `organisationDetail` (immuable une fois défini). `Attachment` gagne `section` (nullable, classable après le dépôt). Les 3 sections fixes sont créées dans `InvestigationServiceImpl.start()`, au même point que la porte `EQUIPE_CONSTITUEE`/`PLAN_VALIDE` déjà en place.

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA, Liquibase (formatted SQL), Lombok `@SuperBuilder`/`@RequiredArgsConstructor`, JUnit 5 + Mockito + AssertJ.

## Global Constraints

- Les 3 sections fixes (`ADMINISTRATION_MISSION`, `PRISE_CONNAISSANCE_ENTITE`, `PRISE_CONNAISSANCE_ENVIRONNEMENT`) sont créées automatiquement dans `InvestigationServiceImpl.start()`, jamais par l'utilisateur — une seule instance par dossier, idempotence défensive via `existsByDossierIdAndType`.
- `Dossier.organisationDetail` : un seul choix par dossier (pas par section), **immuable une fois défini** — rejeter toute tentative de redéfinition avec `BusinessException`.
- Une section `DETAIL` ne peut être créée que si `Dossier.organisationDetail` est déjà défini — sinon `BusinessException` explicite invitant à définir l'organisation d'abord.
- Libellé `DETAIL` unique par dossier (contrainte applicative + index partiel en base `WHERE type = 'DETAIL'`).
- `Attachment.section` est **nullable** — le dépôt d'une pièce ne doit **jamais** être bloqué par l'absence de classement. Reclassement possible après coup via `PATCH /api/v1/attachments/{id}/section`, y compris le déclassement (`sectionId = null`).
- Pas de nouvelle entité racine `DossierDeTravail` — `SectionDossierTravail` référence `Dossier` directement.
- Pas d'arbre générique récursif — structure plate à 2 niveaux uniquement (type de section + libellé optionnel).
- Aucune suppression de section (ni fixe, ni `DETAIL`) — non demandé, hors périmètre.
- La check-list des 22 points du dossier de travail (Lot 5) est **hors périmètre** de ce plan.
- Numérotation de migration : `030`, confirmé libre après `029-add-attachment-chain-of-custody.sql`.
- Rôles d'écriture proposés pour le nouveau contrôleur : `hasAnyRole('CONTROLEUR_ETAT','AGENT_BRPD','ADMIN_DDIC')` — lecture (`GET /sections`) sans `@PreAuthorize` dédié, cohérent avec `AttachmentController.uploadFiles()`.

---

### Task 1: Arborescence du dossier de travail — entités, service, contrôleur, intégration

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/enums/TypeSectionDossierTravail.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/enums/OrganisationDetail.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/SectionDossierTravail.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Dossier.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Attachment.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/SectionDossierTravailRepository.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/repository/AttachmentRepository.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/SectionDossierTravailService.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/AttachmentStorageService.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/SectionDossierTravailController.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/controller/AttachmentController.java`
- Create: `src/main/resources/db/changelog/migrations/030-create-section-dossier-travail.sql`
- Create: `src/test/java/gov/bf/ascelc/univers_audits/service/SectionDossierTravailServiceTest.java`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/AttachmentStorageServiceTest.java`

**Interfaces:**
- Produces: `SectionDossierTravail.dossier: Dossier`, `.type: TypeSectionDossierTravail`, `.libelle: String` (nullable). `Dossier.organisationDetail: OrganisationDetail` (nullable). `Attachment.section: SectionDossierTravail` (nullable). `SectionDossierTravailService.creerSectionsFixes(Dossier): void`, `.definirOrganisationDetail(UUID, OrganisationDetail): void`, `.creerSectionDetail(UUID, String): SectionDossierTravail`, `.listerSections(UUID): List<SectionDossierTravailResponse>`. `AttachmentStorageService.reclasser(UUID, String): void`. Single task, no other task in this plan.

- [ ] **Step 1: Créer les 2 nouveaux enums**

Créer `src/main/java/gov/bf/ascelc/univers_audits/enums/TypeSectionDossierTravail.java` :

```java
package gov.bf.ascelc.univers_audits.enums;

public enum TypeSectionDossierTravail {
    ADMINISTRATION_MISSION,
    PRISE_CONNAISSANCE_ENTITE,
    PRISE_CONNAISSANCE_ENVIRONNEMENT,
    DETAIL
}
```

Créer `src/main/java/gov/bf/ascelc/univers_audits/enums/OrganisationDetail.java` :

```java
package gov.bf.ascelc.univers_audits.enums;

public enum OrganisationDetail {
    PAR_ETAPE,
    PAR_ENTITE,
    PAR_SITE,
    PAR_CYCLE_COMPTABLE
}
```

- [ ] **Step 2: Ajouter `organisationDetail` à `Dossier`**

Dans `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Dossier.java`, ajouter l'import :

```java
import gov.bf.ascelc.univers_audits.enums.OrganisationDetail;
```

Après le champ `autoReferralSource` (bloc `@Enumerated(EnumType.STRING) @Column(name = "auto_referral_source", length = 30) private AutoReferralSource autoReferralSource;`), ajouter :

```java
    @Enumerated(EnumType.STRING)
    @Column(name = "organisation_detail", length = 25)
    private OrganisationDetail organisationDetail;
```

- [ ] **Step 3: Créer l'entité `SectionDossierTravail`**

```java
// src/main/java/gov/bf/ascelc/univers_audits/model/entity/SectionDossierTravail.java
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

- [ ] **Step 4: Ajouter `section` à `Attachment`**

Dans `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Attachment.java`, ajouter l'import :

```java
import gov.bf.ascelc.univers_audits.model.entity.SectionDossierTravail;
```

(Si `SectionDossierTravail` est déjà dans le même package `model.entity`, cet import n'est pas nécessaire — vérifier le package réel avant d'ajouter l'import.)

Après le bloc du champ `code` (fin des champs de chaîne de possession) :

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private SectionDossierTravail section;
```

- [ ] **Step 5: Créer `SectionDossierTravailRepository`**

```java
// src/main/java/gov/bf/ascelc/univers_audits/repository/SectionDossierTravailRepository.java
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

- [ ] **Step 6: Ajouter `countBySectionId` à `AttachmentRepository`**

Dans `src/main/java/gov/bf/ascelc/univers_audits/repository/AttachmentRepository.java`, ajouter (l'import `java.util.UUID` est déjà présent) :

```java
    long countBySectionId(UUID sectionId);
```

- [ ] **Step 7: Écrire les tests unitaires de `SectionDossierTravailService` (échouent à la compilation, le service n'existe pas encore)**

```java
// src/test/java/gov/bf/ascelc/univers_audits/service/SectionDossierTravailServiceTest.java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.OrganisationDetail;
import gov.bf.ascelc.univers_audits.enums.TypeSectionDossierTravail;
import gov.bf.ascelc.univers_audits.model.entity.Attachment;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.SectionDossierTravail;
import gov.bf.ascelc.univers_audits.repository.AttachmentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.SectionDossierTravailRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SectionDossierTravailServiceTest {

    @Mock
    private SectionDossierTravailRepository sectionRepository;
    @Mock
    private DossierRepository dossierRepository;
    @Mock
    private AttachmentRepository attachmentRepository;

    @InjectMocks
    private SectionDossierTravailService service;

    private Dossier dossier;
    private UUID dossierId;

    @BeforeEach
    void setUp() {
        dossierId = UUID.randomUUID();
        dossier = Dossier.builder().build();
        dossier.setId(dossierId);
    }

    @Test
    void creerSectionsFixes_creeLesTroisSectionsFixes() {
        when(sectionRepository.existsByDossierIdAndType(eq(dossierId), any())).thenReturn(false);

        service.creerSectionsFixes(dossier);

        verify(sectionRepository, times(3)).save(any(SectionDossierTravail.class));
        verify(sectionRepository).existsByDossierIdAndType(
                dossierId, TypeSectionDossierTravail.ADMINISTRATION_MISSION);
        verify(sectionRepository).existsByDossierIdAndType(
                dossierId, TypeSectionDossierTravail.PRISE_CONNAISSANCE_ENTITE);
        verify(sectionRepository).existsByDossierIdAndType(
                dossierId, TypeSectionDossierTravail.PRISE_CONNAISSANCE_ENVIRONNEMENT);
    }

    @Test
    void creerSectionsFixes_appelDeuxiemeFoisNeCreeAucunDoublon() {
        when(sectionRepository.existsByDossierIdAndType(eq(dossierId), any())).thenReturn(true);

        service.creerSectionsFixes(dossier);

        verify(sectionRepository, never()).save(any());
    }

    @Test
    void definirOrganisationDetail_reussitQuandNonEncoreDefini() {
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));

        service.definirOrganisationDetail(dossierId, OrganisationDetail.PAR_SITE);

        assertThat(dossier.getOrganisationDetail()).isEqualTo(OrganisationDetail.PAR_SITE);
        verify(dossierRepository).save(dossier);
    }

    @Test
    void definirOrganisationDetail_rejetteSiDejaDefini() {
        dossier.setOrganisationDetail(OrganisationDetail.PAR_ETAPE);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));

        assertThatThrownBy(() ->
                service.definirOrganisationDetail(dossierId, OrganisationDetail.PAR_SITE))
                .isInstanceOf(BusinessException.class);
        verify(dossierRepository, never()).save(any());
    }

    @Test
    void creerSectionDetail_rejetteSiOrganisationNonDefinie() {
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));

        assertThatThrownBy(() -> service.creerSectionDetail(dossierId, "Site A"))
                .isInstanceOf(BusinessException.class);
        verify(sectionRepository, never()).save(any());
    }

    @Test
    void creerSectionDetail_reussitUneFoisOrganisationDefinie() {
        dossier.setOrganisationDetail(OrganisationDetail.PAR_SITE);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(sectionRepository.existsByDossierIdAndTypeAndLibelle(
                dossierId, TypeSectionDossierTravail.DETAIL, "Site A")).thenReturn(false);
        when(sectionRepository.save(any(SectionDossierTravail.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SectionDossierTravail result = service.creerSectionDetail(dossierId, "Site A");

        assertThat(result.getType()).isEqualTo(TypeSectionDossierTravail.DETAIL);
        assertThat(result.getLibelle()).isEqualTo("Site A");
    }

    @Test
    void creerSectionDetail_rejetteLibelleDejaUtilise() {
        dossier.setOrganisationDetail(OrganisationDetail.PAR_SITE);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(sectionRepository.existsByDossierIdAndTypeAndLibelle(
                dossierId, TypeSectionDossierTravail.DETAIL, "Site A")).thenReturn(true);

        assertThatThrownBy(() -> service.creerSectionDetail(dossierId, "Site A"))
                .isInstanceOf(BusinessException.class);
        verify(sectionRepository, never()).save(any());
    }

    @Test
    void listerSections_retourneLesSectionsFixesMemeVides() {
        SectionDossierTravail section = SectionDossierTravail.builder()
                .dossier(dossier)
                .type(TypeSectionDossierTravail.ADMINISTRATION_MISSION)
                .build();
        section.setId(UUID.randomUUID());
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(sectionRepository.findByDossierId(dossierId)).thenReturn(List.of(section));
        when(attachmentRepository.countBySectionId(section.getId())).thenReturn(0L);

        List<SectionDossierTravailService.SectionDossierTravailResponse> result =
                service.listerSections(dossierId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nombrePieces()).isZero();
    }

    @Test
    void listerSections_retourneLeCompteurDePiecesCorrect() {
        SectionDossierTravail section = SectionDossierTravail.builder()
                .dossier(dossier)
                .type(TypeSectionDossierTravail.ADMINISTRATION_MISSION)
                .build();
        section.setId(UUID.randomUUID());
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(sectionRepository.findByDossierId(dossierId)).thenReturn(List.of(section));
        when(attachmentRepository.countBySectionId(section.getId())).thenReturn(3L);

        List<SectionDossierTravailService.SectionDossierTravailResponse> result =
                service.listerSections(dossierId);

        assertThat(result.get(0).nombrePieces()).isEqualTo(3L);
    }

    @Test
    void listerSections_rejetteSiDossierIntrouvable() {
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listerSections(dossierId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 8: Lancer les tests et vérifier qu'ils échouent à la compilation**

Run: `./mvnw -q -Dtest=SectionDossierTravailServiceTest test`
Expected: COMPILE FAILURE — `SectionDossierTravailService` n'existe pas encore

- [ ] **Step 9: Implémenter `SectionDossierTravailService`**

```java
// src/main/java/gov/bf/ascelc/univers_audits/service/SectionDossierTravailService.java
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

- [ ] **Step 10: Lancer les tests et vérifier qu'ils passent**

Run: `./mvnw -q -Dtest=SectionDossierTravailServiceTest test`
Expected: PASS (10/10 tests)

- [ ] **Step 11: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/enums/TypeSectionDossierTravail.java \
        src/main/java/gov/bf/ascelc/univers_audits/enums/OrganisationDetail.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/SectionDossierTravail.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/Dossier.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/Attachment.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/SectionDossierTravailRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/AttachmentRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/SectionDossierTravailService.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/SectionDossierTravailServiceTest.java
git commit -m "feat: add SectionDossierTravail entity, repository and service"
```

- [ ] **Step 12: Étendre `InvestigationServiceImplTest` pour la non-régression + le nouvel appel (échoue avant l'intégration)**

Dans `src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java`, la classe s'appelle `InvestigationServiceImplTest` (ligne 65), le champ testé est `service` (`@InjectMocks private InvestigationServiceImpl service;`), et le test `start_succeedsWithFullCompositionAndMandat` (ligne 350) est le montage happy-path de référence à reproduire à l'identique. Ajouter le mock manquant au bloc des champs `@Mock` existants (juste après `@Mock private MesureConservatoireRepository mesureConservatoireRepository;`, ligne 87) :

```java
    @Mock private SectionDossierTravailService sectionDossierTravailService;
```

Puis ajouter le nouveau test, à la suite de `start_succeedsWithFullCompositionAndMandat` :

```java
    @Test
    void start_declencheLaCreationDesSectionsFixesDuDossierDeTravail() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        investigation.setPlannedDurationDays(30);
        Agent cge = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CHEF_MISSION)).thenReturn(1L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.INVESTIGATEUR)).thenReturn(2L);
        when(memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigation.getId(), TeamRole.CONSEIL_JURIDIQUE)).thenReturn(1L);
        when(mandatRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(Mandat.builder().id(UUID.randomUUID())
                        .investigation(investigation).agentCGE(cge)
                        .dateDelivrance(java.time.Instant.now()).build()));
        when(planInvestigationRepository.findByInvestigationId(investigation.getId()))
                .thenReturn(Optional.of(PlanInvestigation.builder().id(UUID.randomUUID())
                        .validatedAt(java.time.Instant.now()).build()));
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(investigationMapper.toResponse(investigation))
                .thenReturn(InvestigationResponse.builder().build());
        when(agentContextResolver.getCurrentAgent()).thenReturn(cge);

        service.start(investigation.getId(), "127.0.0.1");

        verify(sectionDossierTravailService).creerSectionsFixes(dossier);
    }
```

Import à vérifier en tête de fichier (l'ajouter si absent) :
`gov.bf.ascelc.univers_audits.service.SectionDossierTravailService`.

- [ ] **Step 13: Lancer le test et vérifier qu'il échoue**

Run: `./mvnw -q -Dtest=InvestigationServiceImplTest#start_declencheLaCreationDesSectionsFixesDuDossierDeTravail test`
Expected: FAIL — soit `sectionDossierTravailService` non injecté (champ absent dans `InvestigationServiceImpl`), soit `verify` échoue faute d'appel

- [ ] **Step 14: Câbler l'appel dans `InvestigationServiceImpl.start()`**

Dans `src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java`, ajouter le champ (la classe utilise déjà l'injection par constructeur Lombok — ajouter simplement le champ `private final SectionDossierTravailService sectionDossierTravailService;` au bloc des autres champs `private final` en tête de classe).

Dans la méthode `start(UUID investigationId, String ipAddress)`, juste après la ligne `Investigation saved = investigationRepository.save(inv);` et avant l'appel à `auditRecorder.addObservation(...)` :

```java
        sectionDossierTravailService.creerSectionsFixes(inv.getDossier());
```

- [ ] **Step 15: Lancer le test et vérifier qu'il passe**

Run: `./mvnw -q -Dtest=InvestigationServiceImplTest test`
Expected: PASS (tous les tests de la classe, y compris le nouveau et tous les tests `start_*` déjà existants — non-régression)

- [ ] **Step 16: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/InvestigationServiceImplTest.java
git commit -m "feat: create fixed dossier de travail sections when investigation starts"
```

- [ ] **Step 17: Créer `SectionDossierTravailController`**

```java
// src/main/java/gov/bf/ascelc/univers_audits/controller/SectionDossierTravailController.java
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

- [ ] **Step 18: Compiler et vérifier qu'il n'y a pas d'erreur**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS (aucun test dédié au contrôleur dans ce plan — pas de couche `@WebMvcTest` existante pour les autres contrôleurs de ce dépôt, cohérent avec l'absence de test de `AttachmentController` lui-même ; la logique métier est déjà couverte par `SectionDossierTravailServiceTest`)

- [ ] **Step 19: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/SectionDossierTravailController.java
git commit -m "feat: add SectionDossierTravailController REST endpoints"
```

- [ ] **Step 20: Étendre `AttachmentStorageServiceTest` pour `sectionId` au dépôt et `reclasser()` (échoue à la compilation)**

Dans `src/test/java/gov/bf/ascelc/univers_audits/service/AttachmentStorageServiceTest.java`, la classe expose `@Mock private AttachmentRepository attachmentRepository;`, `@Mock private DossierRepository dossierRepository;`, `@InjectMocks private AttachmentStorageService service;`, un `@TempDir uploadDir` déjà injecté par `@BeforeEach setUp()`, et un helper `buildDossier(UUID id)` (retourne un `Dossier` en statut `EN_INVESTIGATION`) — réutiliser ce helper, ne pas le recréer. L'appel `upload(...)` existant prend actuellement 6 arguments (`dossierId, files, accessCode, source, modeObtention, personneRemettante`) — **tous les appels déjà présents dans ce fichier doivent recevoir un 7ᵉ argument `null`** pour rester compilables (non-régression) : chercher chaque `service.upload(` existant et ajouter `, null` avant la parenthèse fermante.

Ajouter le mock manquant au bloc des champs `@Mock` existants (après `@Mock private AccessCodeGenerator accessCodeGenerator;`) :

```java
    @Mock private SectionDossierTravailRepository sectionDossierTravailRepository;
```

Ajouter les 4 nouveaux tests, à la suite des tests `upload_*` existants :

```java
    @Test
    void upload_sansSectionIdLaisseSectionNulle() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);
        MockMultipartFile file = new MockMultipartFile(
                "files", "preuve.pdf", "application/pdf", "contenu".getBytes());

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.empty());
        when(attachmentRepository.count()).thenReturn(0L);
        when(accessCodeGenerator.generateAttachmentCode(any(), anyLong()))
                .thenReturn("ACC-S-00001");
        when(attachmentRepository.existsByCode("ACC-S-00001")).thenReturn(false);
        when(attachmentRepository.save(any(Attachment.class)))
                .thenAnswer(inv -> {
                    Attachment a = inv.getArgument(0);
                    a.setId(UUID.randomUUID());
                    return a;
                });

        service.upload(dossierId.toString(), List.of(file), null, null, null, null, null);

        verify(attachmentRepository).save(argThat(a -> a.getSection() == null));
    }

    @Test
    void upload_avecSectionIdRattacheLaPieceALaBonneSection() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);
        UUID sectionId = UUID.randomUUID();
        SectionDossierTravail section = SectionDossierTravail.builder().build();
        section.setId(sectionId);
        MockMultipartFile file = new MockMultipartFile(
                "files", "preuve.pdf", "application/pdf", "contenu".getBytes());

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.empty());
        when(attachmentRepository.count()).thenReturn(0L);
        when(accessCodeGenerator.generateAttachmentCode(any(), anyLong()))
                .thenReturn("ACC-S-00001");
        when(attachmentRepository.existsByCode("ACC-S-00001")).thenReturn(false);
        when(sectionDossierTravailRepository.getReferenceById(sectionId)).thenReturn(section);
        when(attachmentRepository.save(any(Attachment.class)))
                .thenAnswer(inv -> {
                    Attachment a = inv.getArgument(0);
                    a.setId(UUID.randomUUID());
                    return a;
                });

        service.upload(dossierId.toString(), List.of(file), null, null, null, null,
                sectionId.toString());

        verify(attachmentRepository).save(argThat(a -> a.getSection() == section));
    }

    @Test
    void reclasser_changeLaSectionDUnePieceDejaDeposee() {
        UUID attachmentId = UUID.randomUUID();
        UUID nouvelleSectionId = UUID.randomUUID();
        Attachment attachment = Attachment.builder().build();
        attachment.setId(attachmentId);
        SectionDossierTravail nouvelleSection = SectionDossierTravail.builder().build();
        nouvelleSection.setId(nouvelleSectionId);
        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));
        when(sectionDossierTravailRepository.getReferenceById(nouvelleSectionId))
                .thenReturn(nouvelleSection);

        service.reclasser(attachmentId, nouvelleSectionId.toString());

        assertThat(attachment.getSection()).isEqualTo(nouvelleSection);
        verify(attachmentRepository).save(attachment);
    }

    @Test
    void reclasser_avecSectionIdNulDeclasseLaPiece() {
        UUID attachmentId = UUID.randomUUID();
        Attachment attachment = Attachment.builder().build();
        attachment.setId(attachmentId);
        SectionDossierTravail ancienneSection = SectionDossierTravail.builder().build();
        ancienneSection.setId(UUID.randomUUID());
        attachment.setSection(ancienneSection);
        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));

        service.reclasser(attachmentId, null);

        assertThat(attachment.getSection()).isNull();
        verify(attachmentRepository).save(attachment);
    }
```

Vérifier en tête de fichier que les imports suivants sont présents, les ajouter sinon :
`gov.bf.ascelc.univers_audits.model.entity.SectionDossierTravail`,
`gov.bf.ascelc.univers_audits.repository.SectionDossierTravailRepository`.

- [ ] **Step 21: Lancer les tests et vérifier qu'ils échouent à la compilation**

Run: `./mvnw -q -Dtest=AttachmentStorageServiceTest test`
Expected: COMPILE FAILURE — `reclasser()` n'existe pas encore, signature de `upload()` incompatible avec les nouveaux appels à 7 arguments

- [ ] **Step 22: Étendre `AttachmentStorageService` — `sectionId` au dépôt + `reclasser()`**

Dans `src/main/java/gov/bf/ascelc/univers_audits/service/AttachmentStorageService.java`, ajouter l'import :

```java
import gov.bf.ascelc.univers_audits.model.entity.SectionDossierTravail;
import gov.bf.ascelc.univers_audits.repository.SectionDossierTravailRepository;
```

Ajouter le champ (la classe est déjà `@RequiredArgsConstructor`, l'ajout de ce champ `final` suffit à le faire injecter) :

```java
    private final SectionDossierTravailRepository sectionDossierTravailRepository;
```

Modifier la signature de `upload(...)` pour ajouter un paramètre `sectionId` :

```java
    @Transactional
    public List<UploadedFile> upload(
            String dossierId, List<MultipartFile> files, String accessCode,
            AttachmentSource source, ModeObtention modeObtention, String personneRemettante,
            String sectionId) {
```

À l'intérieur de la boucle, ajouter au builder d'`Attachment` (après `.code(...)`) :

```java
                .section(sectionId != null
                        ? sectionDossierTravailRepository.getReferenceById(UUID.fromString(sectionId))
                        : null)
```

Ajouter la nouvelle méthode `reclasser` :

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

- [ ] **Step 23: Lancer les tests et vérifier qu'ils passent**

Run: `./mvnw -q -Dtest=AttachmentStorageServiceTest test`
Expected: PASS (tous les tests du fichier, y compris les 4 nouveaux et tous les tests déjà existants du sous-chantier 5/6 — non-régression)

- [ ] **Step 24: Mettre à jour `AttachmentController` — paramètre `sectionId` + endpoint de reclassement**

Dans `src/main/java/gov/bf/ascelc/univers_audits/controller/AttachmentController.java`, dans `uploadFiles(...)`, ajouter le paramètre :

```java
            @RequestParam(value = "sectionId", required = false) String sectionId,
```

Mettre à jour l'appel au service en conséquence :

```java
                        dossierId, files, accessCode, source, modeObtention,
                        personneRemettante, sectionId);
```

Ajouter le nouvel endpoint de reclassement :

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

(Vérifier que `java.util.Map` est déjà importé — utilisé ailleurs dans ce fichier pour d'autres réponses.)

- [ ] **Step 25: Compiler et vérifier qu'il n'y a pas d'erreur**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 26: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/service/AttachmentStorageService.java \
        src/main/java/gov/bf/ascelc/univers_audits/controller/AttachmentController.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/AttachmentStorageServiceTest.java
git commit -m "feat: allow attaching and reclassifying pieces into dossier de travail sections"
```

- [ ] **Step 27: Créer la migration `030`**

```sql
-- src/main/resources/db/changelog/migrations/030-create-section-dossier-travail.sql
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

- [ ] **Step 28: Lancer la suite complète et vérifier qu'elle passe**

Run: `./mvnw -q test`
Expected: `Tests run: XXX, Failures: 0, Errors: 1` — la seule erreur attendue est `UniversAuditsApplicationTests.contextLoads` (« Failed to determine suitable jdbc url »), pré-existante dans cet environnement sans datasource live, sans rapport avec ce changement (déjà confirmé lors du sous-chantier 5/6). Aucune autre erreur ni échec ne doit apparaître.

- [ ] **Step 29: Commit**

```bash
git add src/main/resources/db/changelog/migrations/030-create-section-dossier-travail.sql
git commit -m "feat: add migration for section_dossier_travail table and related columns"
```

## Self-Review Notes (for the plan author, not a task)

- **Spec coverage :** arborescence à 2 niveaux (Step 1-6), création auto des 3
  sections fixes au démarrage de l'investigation (Step 12-16), choix
  d'`organisationDetail` immuable + création de sections `DETAIL` (Step 7-11, service
  + Step 17-19, contrôleur), rattachement optionnel des pièces + reclassement après
  coup (Step 20-26), migration (Step 27). Hors périmètre du spec (checklist 22
  points, arbre récursif, suppression de section) correctement absents de ce plan.
- **Single task, no decomposition needed** : comme pour le sous-chantier 5/6, chaque
  changement de fichier découle de la même chaîne de dépendances (entité → repository
  → service → intégration dans `InvestigationServiceImpl` → contrôleur → extension
  d'`AttachmentStorageService`/`AttachmentController`) — rien n'est indépendamment
  revuable ou mergeable seul.
- **Compile-safety vérifiée** : `upload()` gagne un 7ᵉ paramètre — son seul appelant
  de production (`AttachmentController.uploadFiles()`) est mis à jour au même Step
  (24) que la signature ; son seul appelant de test
  (`AttachmentStorageServiceTest`) est explicitement signalé au Step 20 pour mettre à
  jour tous les appels existants.
- **Type consistency vérifiée** : `SectionDossierTravailService.
  SectionDossierTravailResponse` (record imbriqué) utilisé de façon identique dans le
  service (Step 9) et le contrôleur (Step 17). `sectionId: String` (jamais `UUID`)
  cohérent sur toute la chaîne HTTP → service, à l'image du patron déjà établi pour
  `dossierId`/`attachmentId` dans ce dépôt (conversion `UUID.fromString` au niveau
  service, jamais au niveau contrôleur/DTO).
