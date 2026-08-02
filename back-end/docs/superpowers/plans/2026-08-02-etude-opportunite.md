# Étude d'opportunité (Lot 2, sous-chantier 1/4) — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remplacer le texte libre actuel du conseiller juridique (`ADMISSIBILITY_ANALYSIS`)
par une entité `EtudeOpportunite` structurée capturant la grille de 9 questions du manuel
(§11 du plan de travail), exposée en lecture/écriture sur un endpoint dédié.

**Architecture:** Nouvelle entité `EtudeOpportunite` (1:1 avec `Dossier`, table séparée),
même style que `Witness`/`TargetedParty` : repository/service/controller dédiés, intégrée en
lecture au détail du dossier (`DossierResponse.etudeOpportunite`), avec un garde-fou léger sur
`submitToCtadp` (existence de l'étude requise, pas complétude des 9 champs).

**Tech Stack:** Spring Boot 3 / Java 17, MapStruct, Liquibase, JUnit 5 + Mockito + AssertJ.

## Global Constraints

- Migration Liquibase `016-add-etude-opportunite.sql`, table `etude_opportunite`, FK
  `dossier_id` avec contrainte `UNIQUE`.
- Tous les champs de `EtudeOpportunite` sont nullables sauf `dossier` — remplissage progressif.
- `PUT /api/v1/dossiers/{dossierId}/etude-opportunite` : mise à jour partielle (un champ
  absent de la requête reste inchangé), réservé `CONSEILLER_JURIDIQUE, ADMIN_DDIC`, appelle
  `DossierAccessGuard.checkReadAccess(dossier)`, rejette si `dossier.getStatus() !=
  DossierStatus.EN_ETUDE_OPPORTUNITE`.
- `submitToCtadp()` rejette si aucune `EtudeOpportunite` n'existe pour le dossier — pas de
  vérification de complétude des champs.
- Ne pas créer de référentiel `Secteur` (Lot 0, hors périmètre) — `secteurSensible` reste un
  booléen + texte libre.
- Ne pas toucher au contrôle d'accès en écriture de `WitnessService`/`TargetedPartyService` —
  hors périmètre, dette pré-existante distincte.

Spec de référence : `docs/superpowers/specs/2026-08-02-etude-opportunite-design.md`
Document source : `docs/reference/plan-de-travail-asce-lc.md` (§5, §11)

---

### Task 1: Enums, entité, migration

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/enums/NatureQualification.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/enums/QualificationNonPenale.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/EtudeOpportunite.java`
- Create: `src/main/resources/db/changelog/migrations/016-add-etude-opportunite.sql`

- [ ] **Step 1: Créer les deux enums**

`NatureQualification.java` :

```java
package gov.bf.ascelc.univers_audits.enums;

public enum NatureQualification {
    PENALE,
    ADMINISTRATIVE
}
```

`QualificationNonPenale.java` :

```java
package gov.bf.ascelc.univers_audits.enums;

public enum QualificationNonPenale {
    IRREGULARITE,
    FRAUDE,
    ACTE_COLLUSION,
    ACTES_ILLICITES
}
```

- [ ] **Step 2: Créer l'entité `EtudeOpportunite`**

```java
package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.NatureQualification;
import gov.bf.ascelc.univers_audits.enums.QualificationNonPenale;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "etude_opportunite", indexes = {
        @Index(name = "idx_etude_opportunite_dossier",
                columnList = "dossier_id", unique = true)
})
public class EtudeOpportunite extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false, unique = true)
    private Dossier dossier;

    @Column(name = "preoccupation_reelle")
    private Boolean preoccupationReelle;

    @Column(name = "preoccupation_reelle_commentaire", length = 2000)
    private String preoccupationReelleCommentaire;

    @Column(name = "competence_asce_lc")
    private Boolean competenceAsceLc;

    @Column(name = "competence_asce_lc_commentaire", length = 2000)
    private String competenceAsceLcCommentaire;

    @Enumerated(EnumType.STRING)
    @Column(name = "nature_qualification", length = 20)
    private NatureQualification natureQualification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_infraction_id")
    private TypeInfraction typeInfraction;

    @Enumerated(EnumType.STRING)
    @Column(name = "qualification_non_penale", length = 30)
    private QualificationNonPenale qualificationNonPenale;

    @Column(name = "preuves_suffisantes")
    private Boolean preuvesSuffisantes;

    @Column(name = "preuves_suffisantes_commentaire", length = 2000)
    private String preuvesSuffisantesCommentaire;

    @Column(name = "enquete_complementaire_necessaire")
    private Boolean enqueteComplementaireNecessaire;

    @Column(name = "enquete_complementaire_necessaire_commentaire", length = 2000)
    private String enqueteComplementaireNecessaireCommentaire;

    @Column(name = "urgence_securisation_preuves")
    private Boolean urgenceSecurisationPreuves;

    @Column(name = "urgence_securisation_preuves_commentaire", length = 2000)
    private String urgenceSecurisationPreuvesCommentaire;

    @Column(name = "opportunite_saisir_procureur")
    private Boolean opportuniteSaisirProcureur;

    @Column(name = "opportunite_saisir_procureur_commentaire", length = 2000)
    private String opportuniteSaisirProcureurCommentaire;

    @Column(name = "secteur_sensible")
    private Boolean secteurSensible;

    @Column(name = "secteur_precision", length = 300)
    private String secteurPrecision;

    @Column(name = "solidite_allegation")
    private Boolean soliditeAllegation;

    @Column(name = "solidite_allegation_commentaire", length = 2000)
    private String soliditeAllegationCommentaire;

    @Column(name = "avis_general", length = 5000)
    private String avisGeneral;
}
```

- [ ] **Step 3: Écrire la migration**

```sql
--liquibase formatted sql
--changeset dev:016-add-etude-opportunite

CREATE TABLE IF NOT EXISTS etude_opportunite (
    id                                              UUID PRIMARY KEY,
    version                                         BIGINT NOT NULL DEFAULT 0,
    created_at                                       TIMESTAMP,
    updated_at                                       TIMESTAMP,
    created_by_id                                    VARCHAR(100),
    updated_by_id                                    VARCHAR(100),
    dossier_id                                       UUID NOT NULL UNIQUE REFERENCES dossier(id),
    preoccupation_reelle                             BOOLEAN,
    preoccupation_reelle_commentaire                 TEXT,
    competence_asce_lc                               BOOLEAN,
    competence_asce_lc_commentaire                   TEXT,
    nature_qualification                             VARCHAR(20),
    type_infraction_id                               UUID REFERENCES type_infraction(id),
    qualification_non_penale                         VARCHAR(30),
    preuves_suffisantes                              BOOLEAN,
    preuves_suffisantes_commentaire                  TEXT,
    enquete_complementaire_necessaire                BOOLEAN,
    enquete_complementaire_necessaire_commentaire    TEXT,
    urgence_securisation_preuves                     BOOLEAN,
    urgence_securisation_preuves_commentaire         TEXT,
    opportunite_saisir_procureur                     BOOLEAN,
    opportunite_saisir_procureur_commentaire         TEXT,
    secteur_sensible                                 BOOLEAN,
    secteur_precision                                VARCHAR(300),
    solidite_allegation                              BOOLEAN,
    solidite_allegation_commentaire                  TEXT,
    avis_general                                     VARCHAR(5000)
);

COMMENT ON TABLE etude_opportunite IS 'Grille structuree de 9 questions du conseiller juridique (Lot 2, plan de travail S11) - une par dossier';
COMMENT ON COLUMN etude_opportunite.qualification_non_penale IS 'Renseigne si nature_qualification=ADMINISTRATIVE - prepare la branche ORIENTEE_ADMINISTRATIF (sous-chantier DecisionCGE, pas encore implemente)';
```

Les colonnes héritées ci-dessus (`id`, `version`, `created_at`, `updated_at`,
`created_by_id`/`updated_by_id` en `VARCHAR(100)`, pas `UUID`) sont déjà vérifiées contre
`AuditEntity.java` — les reproduire telles quelles, ne pas les redériver.

- [ ] **Step 4: Vérifier la compilation**

```
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/enums/NatureQualification.java \
        src/main/java/gov/bf/ascelc/univers_audits/enums/QualificationNonPenale.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/EtudeOpportunite.java \
        src/main/resources/db/changelog/migrations/016-add-etude-opportunite.sql
git commit -m "feat: add EtudeOpportunite entity and migration (Lot 2)"
```

---

### Task 2: DTOs et mapper

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/EtudeOpportuniteRequest.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/EtudeOpportuniteResponse.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierDetailsMapper.java`

**Interfaces:**
- Consumes: `EtudeOpportunite` (Task 1).
- Produces: `DossierDetailsMapper.toResponse(EtudeOpportunite)`,
  `DossierDetailsMapper.updateEntity(EtudeOpportuniteRequest, EtudeOpportunite)` — utilisés par
  la Task 3.

- [ ] **Step 1: Créer `EtudeOpportuniteRequest`**

```java
package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.NatureQualification;
import gov.bf.ascelc.univers_audits.enums.QualificationNonPenale;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EtudeOpportuniteRequest {

    private Boolean preoccupationReelle;

    @Size(max = 2000)
    private String preoccupationReelleCommentaire;

    private Boolean competenceAsceLc;

    @Size(max = 2000)
    private String competenceAsceLcCommentaire;

    private NatureQualification natureQualification;

    private UUID typeInfractionId;

    private QualificationNonPenale qualificationNonPenale;

    private Boolean preuvesSuffisantes;

    @Size(max = 2000)
    private String preuvesSuffisantesCommentaire;

    private Boolean enqueteComplementaireNecessaire;

    @Size(max = 2000)
    private String enqueteComplementaireNecessaireCommentaire;

    private Boolean urgenceSecurisationPreuves;

    @Size(max = 2000)
    private String urgenceSecurisationPreuvesCommentaire;

    private Boolean opportuniteSaisirProcureur;

    @Size(max = 2000)
    private String opportuniteSaisirProcureurCommentaire;

    private Boolean secteurSensible;

    @Size(max = 300)
    private String secteurPrecision;

    private Boolean soliditeAllegation;

    @Size(max = 2000)
    private String soliditeAllegationCommentaire;

    @Size(max = 5000)
    private String avisGeneral;
}
```

- [ ] **Step 2: Créer `EtudeOpportuniteResponse`**

```java
package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.NatureQualification;
import gov.bf.ascelc.univers_audits.enums.QualificationNonPenale;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EtudeOpportuniteResponse {

    private UUID id;
    private Boolean preoccupationReelle;
    private String preoccupationReelleCommentaire;
    private Boolean competenceAsceLc;
    private String competenceAsceLcCommentaire;
    private NatureQualification natureQualification;
    private UUID typeInfractionId;
    private String typeInfractionLibelle;
    private QualificationNonPenale qualificationNonPenale;
    private Boolean preuvesSuffisantes;
    private String preuvesSuffisantesCommentaire;
    private Boolean enqueteComplementaireNecessaire;
    private String enqueteComplementaireNecessaireCommentaire;
    private Boolean urgenceSecurisationPreuves;
    private String urgenceSecurisationPreuvesCommentaire;
    private Boolean opportuniteSaisirProcureur;
    private String opportuniteSaisirProcureurCommentaire;
    private Boolean secteurSensible;
    private String secteurPrecision;
    private Boolean soliditeAllegation;
    private String soliditeAllegationCommentaire;
    private String avisGeneral;
}
```

- [ ] **Step 3: Ajouter le mapping dans `DossierDetailsMapper`**

Lire d'abord tout `DossierDetailsMapper.java` pour repérer où sont déclarées les méthodes
`toResponse`/`fillXxx` de `Witness`/`TargetedParty` (déjà lu lors du design — motif
`default toResponse(...)` enveloppant `mapToResponse(...)`, requis à cause du bug MapStruct/
Lombok déjà documenté en tête de ce fichier). Ajouter, dans le même style :

```java
    default EtudeOpportuniteResponse toResponse(EtudeOpportunite etude) {
        EtudeOpportuniteResponse response = mapToResponse(etude);
        if (response != null) {
            fillEtudeOpportunite(etude, response);
        }
        return response;
    }

    @Mapping(target = "typeInfractionId", ignore = true)
    @Mapping(target = "typeInfractionLibelle", ignore = true)
    EtudeOpportuniteResponse mapToResponse(EtudeOpportunite etude);

    default void fillEtudeOpportunite(
            EtudeOpportunite etude,
            EtudeOpportuniteResponse response) {
        if (etude.getTypeInfraction() != null) {
            response.setTypeInfractionId(etude.getTypeInfraction().getId());
            response.setTypeInfractionLibelle(etude.getTypeInfraction().getLibelle());
        }
    }

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdById", ignore = true)
    @Mapping(target = "updatedById", ignore = true)
    @Mapping(target = "dossier", ignore = true)
    @Mapping(target = "typeInfraction", ignore = true)
    void updateEntity(EtudeOpportuniteRequest request, @MappingTarget EtudeOpportunite etude);
```

`updateEntity` ignore `typeInfraction` (relation, pas un champ scalaire) — la Task 3 résout
`typeInfractionId` vers l'entité `TypeInfraction` elle-même dans le service, avant d'appeler ce
mapper.

- [ ] **Step 4: Vérifier la compilation**

```
mvn compile -q
```

Expected: BUILD SUCCESS (l'annotation processor MapStruct doit générer
`DossierDetailsMapperImpl` sans erreur — vérifier qu'aucune propriété n'est signalée comme non
mappée, `unmappedTargetPolicy = ReportingPolicy.WARN` sur ce mapper : un avertissement au
build n'échoue pas la compilation mais doit être lu et traité si présent).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/request/EtudeOpportuniteRequest.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/EtudeOpportuniteResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/mapper/DossierDetailsMapper.java
git commit -m "feat: add EtudeOpportunite DTOs and mapper"
```

---

### Task 3: Repository, service

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/repository/EtudeOpportuniteRepository.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/EtudeOpportuniteService.java`
- Create: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/EtudeOpportuniteServiceImpl.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/EtudeOpportuniteServiceImplTest.java`

**Interfaces:**
- Consumes: `DossierDetailsMapper.toResponse(EtudeOpportunite)`/`updateEntity(...)` (Task 2),
  `DossierAccessGuard.getDossierOrThrow(UUID)`/`checkReadAccess(Dossier)` (existant),
  `TypeInfractionRepository` (existant).
- Produces: `EtudeOpportuniteService.findByDossierId(UUID)`, `.upsert(UUID dossierId,
  EtudeOpportuniteRequest request)` — utilisés par la Task 4 (contrôleur) et indirectement par
  la Task 5 (`submitToCtadp`, via `EtudeOpportuniteRepository.existsByDossierId`).

- [ ] **Step 1: Créer le repository**

```java
package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.EtudeOpportunite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EtudeOpportuniteRepository
        extends JpaRepository<EtudeOpportunite, UUID> {

    Optional<EtudeOpportunite> findByDossierId(UUID dossierId);

    boolean existsByDossierId(UUID dossierId);
}
```

- [ ] **Step 2: Créer l'interface de service**

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.EtudeOpportuniteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.EtudeOpportuniteResponse;

import java.util.UUID;

public interface EtudeOpportuniteService {

    EtudeOpportuniteResponse findByDossierId(UUID dossierId);

    EtudeOpportuniteResponse upsert(UUID dossierId, EtudeOpportuniteRequest request);
}
```

- [ ] **Step 3: Implémenter le service**

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.EtudeOpportuniteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.EtudeOpportuniteResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.EtudeOpportunite;
import gov.bf.ascelc.univers_audits.model.entity.TypeInfraction;
import gov.bf.ascelc.univers_audits.repository.EtudeOpportuniteRepository;
import gov.bf.ascelc.univers_audits.repository.TypeInfractionRepository;
import gov.bf.ascelc.univers_audits.service.EtudeOpportuniteService;
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
@Transactional(readOnly = true)
public class EtudeOpportuniteServiceImpl implements EtudeOpportuniteService {

    private final EtudeOpportuniteRepository etudeOpportuniteRepository;
    private final TypeInfractionRepository   typeInfractionRepository;
    private final DossierDetailsMapper       detailsMapper;
    private final DossierAccessGuard         accessGuard;

    @Override
    public EtudeOpportuniteResponse findByDossierId(UUID dossierId) {
        Dossier dossier = accessGuard.getDossierOrThrow(dossierId);
        accessGuard.checkReadAccess(dossier);

        return etudeOpportuniteRepository.findByDossierId(dossierId)
                .map(detailsMapper::toResponse)
                .orElse(null);
    }

    @Override
    @Transactional
    public EtudeOpportuniteResponse upsert(UUID dossierId, EtudeOpportuniteRequest request) {
        Dossier dossier = accessGuard.getDossierOrThrow(dossierId);
        accessGuard.checkReadAccess(dossier);

        if (dossier.getStatus() != DossierStatus.EN_ETUDE_OPPORTUNITE) {
            throw new BusinessException(
                    "L'étude d'opportunité ne peut être modifiée que pendant "
                            + "l'étape d'étude d'opportunité (statut actuel : "
                            + dossier.getStatus() + ")");
        }

        EtudeOpportunite etude = etudeOpportuniteRepository.findByDossierId(dossierId)
                .orElseGet(() -> EtudeOpportunite.builder().dossier(dossier).build());

        detailsMapper.updateEntity(request, etude);

        if (request.getTypeInfractionId() != null) {
            TypeInfraction typeInfraction = typeInfractionRepository
                    .findById(request.getTypeInfractionId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Qualification pénale introuvable : "
                                    + request.getTypeInfractionId()));
            etude.setTypeInfraction(typeInfraction);
        }

        EtudeOpportunite saved = etudeOpportuniteRepository.save(etude);
        log.info("Étude d'opportunité mise à jour — dossier: {}", dossierId);
        return detailsMapper.toResponse(saved);
    }
}
```

Note : `request.getTypeInfractionId() != null` ne permet pas de *retirer* une qualification
déjà choisie via ce endpoint (cohérent avec la sémantique de mise à jour partielle globale de
ce chantier — un champ absent de la requête reste inchangé, jamais remis à `null`). Si le
conseiller doit un jour changer d'avis de PENALE vers ADMINISTRATIVE, il envoie le nouveau
`natureQualification` et le nouveau `qualificationNonPenale` ; `typeInfraction` reste
techniquement renseigné en base tant qu'aucune nouvelle valeur n'est fournie — comportement
accepté pour ce chantier, pas un bug à corriger ici.

- [ ] **Step 4: Écrire les tests**

Pas de `WitnessServiceImplTest` existant dans ce dépôt pour s'en inspirer (vérifié) — suivre le
pattern `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks` déjà utilisé par
`DossierServiceImplTest.java`.

```java
package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.EtudeOpportuniteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.EtudeOpportuniteResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.EtudeOpportunite;
import gov.bf.ascelc.univers_audits.repository.EtudeOpportuniteRepository;
import gov.bf.ascelc.univers_audits.repository.TypeInfractionRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EtudeOpportuniteServiceImplTest {

    @Mock private EtudeOpportuniteRepository etudeOpportuniteRepository;
    @Mock private TypeInfractionRepository   typeInfractionRepository;
    @Mock private DossierDetailsMapper       detailsMapper;
    @Mock private DossierAccessGuard         accessGuard;

    @InjectMocks
    private EtudeOpportuniteServiceImpl service;

    @Test
    void upsert_createsWhenAbsent() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.EN_ETUDE_OPPORTUNITE).build();
        EtudeOpportuniteRequest request = EtudeOpportuniteRequest.builder()
                .preoccupationReelle(true)
                .build();

        when(accessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);
        when(etudeOpportuniteRepository.findByDossierId(dossierId))
                .thenReturn(Optional.empty());
        when(etudeOpportuniteRepository.save(any(EtudeOpportunite.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(detailsMapper.toResponse(any(EtudeOpportunite.class)))
                .thenReturn(EtudeOpportuniteResponse.builder().build());

        service.upsert(dossierId, request);

        verify(accessGuard).checkReadAccess(dossier);
        verify(etudeOpportuniteRepository).save(argThat(e -> e.getDossier() == dossier));
    }

    @Test
    void upsert_rejectsWhenDossierNotInOpportunityStudyStatus() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.EN_REVUE_CTADP).build();
        EtudeOpportuniteRequest request = EtudeOpportuniteRequest.builder().build();

        when(accessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);

        assertThatThrownBy(() -> service.upsert(dossierId, request))
                .isInstanceOf(BusinessException.class);

        verify(etudeOpportuniteRepository, never()).save(any());
    }

    @Test
    void upsert_propagatesGuardRejectionWithoutSaving() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.EN_ETUDE_OPPORTUNITE).build();
        EtudeOpportuniteRequest request = EtudeOpportuniteRequest.builder().build();

        when(accessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);
        doThrow(new BusinessException("Accès refusé"))
                .when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.upsert(dossierId, request))
                .isInstanceOf(BusinessException.class);

        verify(etudeOpportuniteRepository, never()).save(any());
    }
}
```

- [ ] **Step 5: Lancer les tests**

```
mvn test -q -Dtest=EtudeOpportuniteServiceImplTest
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/repository/EtudeOpportuniteRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/EtudeOpportuniteService.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/impl/EtudeOpportuniteServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/EtudeOpportuniteServiceImplTest.java
git commit -m "feat: add EtudeOpportuniteService with status/habilitation guards"
```

---

### Task 4: Contrôleur REST

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/controller/EtudeOpportuniteController.java`

**Interfaces:**
- Consumes: `EtudeOpportuniteService` (Task 3).

- [ ] **Step 1: Créer le contrôleur**

```java
package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.EtudeOpportuniteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.EtudeOpportuniteResponse;
import gov.bf.ascelc.univers_audits.service.EtudeOpportuniteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dossiers/{dossierId}/etude-opportunite")
@RequiredArgsConstructor
public class EtudeOpportuniteController {

    private final EtudeOpportuniteService etudeOpportuniteService;

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CONSEILLER_JURIDIQUE'," +
            "'MEMBRE_CTADP','CGEA','CGE','CONTROLEUR_ETAT','ADMIN_DDIC')")
    public ResponseEntity<EtudeOpportuniteResponse> find(
            @PathVariable UUID dossierId) {
        return ResponseEntity.ok(
                etudeOpportuniteService.findByDossierId(dossierId));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('CONSEILLER_JURIDIQUE','ADMIN_DDIC')")
    public ResponseEntity<EtudeOpportuniteResponse> upsert(
            @PathVariable UUID dossierId,
            @Valid @RequestBody EtudeOpportuniteRequest request) {
        return ResponseEntity.ok(
                etudeOpportuniteService.upsert(dossierId, request));
    }
}
```

- [ ] **Step 2: Vérifier la compilation**

```
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/controller/EtudeOpportuniteController.java
git commit -m "feat: add EtudeOpportuniteController"
```

---

### Task 5: Intégration à `DossierServiceImpl` (détail, masquage, garde-fou CTADP)

**Files:**
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/DossierResponse.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImpl.java`
- Test: `src/test/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImplTest.java`

**Interfaces:**
- Consumes: `EtudeOpportuniteRepository` (Task 3), `DossierDetailsMapper.toResponse(EtudeOpportunite)`
  (Task 2).

- [ ] **Step 1: Ajouter le champ à `DossierResponse`**

Dans `DossierResponse.java`, ajouter (ordre libre, suggéré à côté de `witnesses`) :

```java
    private EtudeOpportuniteResponse etudeOpportunite;
```

`EtudeOpportuniteResponse` vit dans le même package (`model.dto.response`) que
`DossierResponse` — aucun import à ajouter.

- [ ] **Step 2: Injecter le repository et peupler le champ dans `enrichAndMaskDetail`**

`DossierServiceImpl` porte `@RequiredArgsConstructor` (confirmé) : ajouter le nouveau champ
`private final EtudeOpportuniteRepository etudeOpportuniteRepository;` **à la toute fin** de
la liste des champs `private final ...` de la classe (pas au milieu) — un test existant,
`DossierServiceImplTest.findById_succeedsForAgentWhoseOnlyHabilitationIsInvestigationTeam`,
construit `DossierServiceImpl` avec un appel de constructeur explicite listant chaque
argument dans l'ordre des champs :

```java
DossierServiceImpl serviceWithRealGuard = new DossierServiceImpl(
        dossierRepository, declarantRepository, notificationRepository, observationRepository,
        dossierMapper, dossierDetailsMapper, declarantMapper, accessCodeGenerator, securityUtils,
        notificationDispatcher, agentContextResolver, auditRecorder, parametreDelaiService,
        natureSaisineResolver, realGuard, habilitationService, portalConfigService);
```

Ajouter le champ en fin de liste permet de simplement **ajouter
`etudeOpportuniteRepository` en dernier argument** de cet appel, sans réordonner les 17
arguments existants. Ne pas oublier cette modification — sans elle, la compilation des tests
échoue (nombre d'arguments du constructeur incorrect).

Dans `enrichAndMaskDetail(Dossier dossier)`, juste après le bloc `response.setWitnesses(...)` :

```java
        response.setEtudeOpportunite(
                etudeOpportuniteRepository.findByDossierId(dossier.getId())
                        .map(dossierDetailsMapper::toResponse)
                        .orElse(null));
```

- [ ] **Step 3: Masquer dans `maskSensitiveData`**

Dans le bloc de masquage confidentiel de `maskSensitiveData()` (celui qui nullifie
`response.setWitnesses(null)` etc. quand `isConfidential && !canSeeConfidential`), ajouter :

```java
            response.setEtudeOpportunite(null);
```

- [ ] **Step 4: Garde-fou sur `submitToCtadp`**

Dans `submitToCtadp()`, avant `dossier.setStatus(DossierStatus.EN_REVUE_CTADP);`, ajouter :

```java
        if (!etudeOpportuniteRepository.existsByDossierId(dossierId)) {
            throw new BusinessException(
                    "Impossible de soumettre au CTADP sans étude d'opportunité "
                            + "préalable");
        }
```

- [ ] **Step 5: Tests de régression**

Ajouter dans `DossierServiceImplTest.java` :

```java
    @Test
    void submitToCtadp_rejectsWhenNoEtudeOpportuniteExists() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.EN_ETUDE_OPPORTUNITE).build();
        StatusTransitionRequest request = StatusTransitionRequest.builder().build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(etudeOpportuniteRepository.existsByDossierId(dossierId)).thenReturn(false);

        assertThatThrownBy(() -> service.submitToCtadp(dossierId, request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);

        verify(dossierRepository, never()).save(any());
    }

    @Test
    void submitToCtadp_succeedsWhenEtudeOpportuniteExists() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.EN_ETUDE_OPPORTUNITE).build();
        StatusTransitionRequest request = StatusTransitionRequest.builder().build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(etudeOpportuniteRepository.existsByDossierId(dossierId)).thenReturn(true);
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dossierMapper.toResponse(any(Dossier.class))).thenReturn(DossierResponse.builder().build());
        when(securityUtils.hasRole(anyString())).thenReturn(false);

        service.submitToCtadp(dossierId, request, "127.0.0.1");

        verify(dossierRepository).save(argThat(d -> d.getStatus() == DossierStatus.EN_REVUE_CTADP));
    }
```

Ajouter `@Mock private EtudeOpportuniteRepository etudeOpportuniteRepository;` à la liste des
mocks en tête de classe (`validateTransition`, appelée par `submitToCtadp()`, ne vérifie que
`dossier.isClosed()` et le statut courant — pas de précondition sur `request.getVersion()` à
gérer dans ces deux tests, déjà vérifié).

- [ ] **Step 6: Lancer les tests**

```
mvn test -q -Dtest=DossierServiceImplTest
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/model/dto/response/DossierResponse.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImpl.java \
        src/test/java/gov/bf/ascelc/univers_audits/service/impl/DossierServiceImplTest.java
git commit -m "feat: expose EtudeOpportunite in dossier detail, guard submitToCtadp"
```

---

### Task 6: Suite complète + mise à jour du backlog

**Files:** mémoire projet `project_asce_backlog_2026_07_30.md`.

- [ ] **Step 1: Lancer la suite complète**

```
mvn test -q
```

Expected: 0 échec (l'erreur `UniversAuditsApplicationTests.contextLoads` — absence de base de
données dans cet environnement — est un baseline connu, sans rapport avec ce chantier).

- [ ] **Step 2: Mettre à jour le backlog mémoire**

Marquer le sous-chantier "EtudeOpportunite" comme livré dans la section C (Lot 1/Lot 2) et
noter les 3 sous-chantiers restants du Lot 2 (SeanceCTADP, DecisionCGE + branche
ORIENTEE_ADMINISTRATIF, génération accusé de réception/réponse motivée) comme prochaine étape.
