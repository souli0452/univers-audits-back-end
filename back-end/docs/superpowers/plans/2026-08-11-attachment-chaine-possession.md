# Attachment Chain-of-Custody Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the hardcoded `Attachment.source`, add the 4 missing chain-of-custody fields required by plan de travail §8.2 (uploader, personne remettante, mode d'obtention, auto-generated code), and enrich the pieces index (`AttachmentSummary`).

**Architecture:** `Attachment` gains 4 fields and a new `ModeObtention` enum. The auto-generated code (`ACC-<letter>-NNNNN`) mirrors the existing dossier-number generation pattern exactly (`AccessCodeGenerator` formatter + count-and-retry loop in the owning service). `source`/`modeObtention`/`personneRemettante` become optional request params on the existing upload endpoint — no new endpoints, no inferred/guessed values.

**Tech Stack:** Spring Boot 3 / Java 17, Spring Data JPA, Liquibase (formatted SQL), Lombok `@SuperBuilder`, JUnit 5 + Mockito + AssertJ.

## Global Constraints

- `source` is currently hardcoded to `AttachmentSource.INITIAL_SUBMISSION` for every upload regardless of context — this is a bug being fixed, not new behavior. Existing callers that omit the new `source` param must keep getting `INITIAL_SUBMISSION` (non-regression).
- `modeObtention` defaults to `ModeObtention.VOLONTAIRE` when omitted.
- `uploadedBy` is nullable — anonymous citizen uploads via `accessCode` have no `Agent`. Never throw when unauthenticated; resolve to `null` instead of calling `AgentContextResolver.getCurrentAgent()` (which throws `BusinessException` if not authenticated).
- `personneRemettante` is free text, nullable — may name a citizen or third party, not necessarily an `Agent`.
- Code format: `ACC-<lettre>-NNNNN` — lettre from a fixed `AttachmentSource → char` map (`S`/`T`/`R`/`P`/`E`/`X`), `NNNNN` a **global** (not per-year, not per-letter) 5-digit sequential counter, generated via the same compte-et-réessaie pattern as `DossierServiceImpl.generateUniqueNumber()` — never a DB sequence.
- `code` is nullable at the column level (existing attachments are never retroactively coded) but always set by the service for every new attachment.
- No new endpoints — the existing `GET /api/v1/attachments/dossier/{dossierId}` list response (`AttachmentSummary`) is enriched in place; it is already the pieces index conceptually.
- Migration numbering: `029`, the next after `028-add-pv-audition-correction-relecture.sql`. No tracked migration in this repo creates the `attachment` table (baseline predates Liquibase tracking here) — this migration is a normal `ALTER TABLE` against the existing table, same as any other.
- Out of scope (do not implement): read/write audit logging on `Attachment`, original-vs-working-copy distinction, soft-delete/versioning, retroactive coding of existing attachments, wiring up `Attachment.validate()`/`.reject()`.

---

### Task 1: Chain-of-custody fields, auto-generated code, enriched index

**Files:**
- Create: `src/main/java/gov/bf/ascelc/univers_audits/enums/ModeObtention.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Attachment.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/shared/utils/AccessCodeGenerator.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/repository/AttachmentRepository.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/service/AttachmentStorageService.java`
- Modify: `src/main/java/gov/bf/ascelc/univers_audits/controller/AttachmentController.java`
- Create: `src/main/resources/db/changelog/migrations/029-add-attachment-chain-of-custody.sql`
- Modify: `src/test/java/gov/bf/ascelc/univers_audits/service/AttachmentStorageServiceTest.java`

**Interfaces:**
- Produces: `Attachment.uploadedBy: Agent` (nullable), `Attachment.personneRemettante: String` (nullable), `Attachment.modeObtention: ModeObtention` (default `VOLONTAIRE`), `Attachment.code: String` (nullable at column level, always set on create), `AccessCodeGenerator.generateAttachmentCode(AttachmentSource, long): String`, `AttachmentRepository.existsByCode(String): boolean`. Single task, no other task in this plan.

- [ ] **Step 1: Create `ModeObtention`**

Create `src/main/java/gov/bf/ascelc/univers_audits/enums/ModeObtention.java`:

```java
package gov.bf.ascelc.univers_audits.enums;

public enum ModeObtention {
    VOLONTAIRE,
    REQUISITION
}
```

- [ ] **Step 2: Add fields to `Attachment`**

In `src/main/java/gov/bf/ascelc/univers_audits/model/entity/Attachment.java`, add the import:

```java
import gov.bf.ascelc.univers_audits.enums.ModeObtention;
```

Then add, after the existing `validatedBy` field (before the `validate(Agent agent)` method):

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id")
    private Agent uploadedBy;

    @Column(name = "personne_remettante", length = 255)
    private String personneRemettante;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_obtention", nullable = false, length = 20)
    @Builder.Default
    private ModeObtention modeObtention = ModeObtention.VOLONTAIRE;

    @Column(name = "code", unique = true, length = 20)
    private String code;
```

- [ ] **Step 3: Add the code formatter to `AccessCodeGenerator`**

In `src/main/java/gov/bf/ascelc/univers_audits/shared/utils/AccessCodeGenerator.java`, add the import:

```java
import gov.bf.ascelc.univers_audits.enums.AttachmentSource;
import java.util.Map;
```

Then add, after `generateDossierNumber`:

```java

    private static final Map<AttachmentSource, Character> ATTACHMENT_SOURCE_LETTERS = Map.of(
            AttachmentSource.INITIAL_SUBMISSION, 'S',
            AttachmentSource.FIELD_INVESTIGATION, 'T',
            AttachmentSource.SOCIAL_MEDIA, 'R',
            AttachmentSource.PRESS_MEDIA, 'P',
            AttachmentSource.EXTERNAL_AUDIT, 'E',
            AttachmentSource.OTHER, 'X');

    public String generateAttachmentCode(AttachmentSource source, long sequence) {
        char letter = ATTACHMENT_SOURCE_LETTERS.get(source);
        return String.format("ACC-%c-%05d", letter, sequence);
    }
```

- [ ] **Step 4: Add `existsByCode` to `AttachmentRepository`**

In `src/main/java/gov/bf/ascelc/univers_audits/repository/AttachmentRepository.java`, add:

```java

    boolean existsByCode(String code);
```

- [ ] **Step 5: Rewrite `AttachmentStorageService`**

Replace the full content of `src/main/java/gov/bf/ascelc/univers_audits/service/AttachmentStorageService.java`:

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.AttachmentSource;
import gov.bf.ascelc.univers_audits.enums.AttachmentStatus;
import gov.bf.ascelc.univers_audits.enums.AttachmentType;
import gov.bf.ascelc.univers_audits.enums.ModeObtention;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Attachment;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.AttachmentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AccessCodeGenerator;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import gov.bf.ascelc.univers_audits.shared.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentStorageService {

    /** Types MIME acceptés en pièce jointe — tout le reste est rejeté. */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain", "text/csv",
            "audio/mpeg", "audio/mp3", "audio/wav", "audio/webm", "audio/ogg",
            "video/mp4", "video/webm", "video/quicktime", "video/x-msvideo"
    );

    private final AttachmentRepository attachmentRepository;
    private final DossierRepository    dossierRepository;
    private final AgentRepository      agentRepository;
    private final DossierAccessGuard   accessGuard;
    private final SecurityUtils        securityUtils;
    private final AccessCodeGenerator  accessCodeGenerator;

    @Value("${storage.upload-dir:C:/asce-lc/uploads}")
    private String uploadDir;

    public record UploadedFile(String id, String originalName, String mimeType,
                               long fileSizeBytes, boolean isAudio) {}

    public record AttachmentSummary(String id, String originalName, String mimeType,
                                    Long fileSizeBytes, String uploadedAt,
                                    boolean isAudio, String status,
                                    String description, String source,
                                    String modeObtention, String code) {}

    @Transactional
    public List<UploadedFile> upload(
            String dossierId, List<MultipartFile> files, String accessCode,
            AttachmentSource source, ModeObtention modeObtention, String personneRemettante) {
        Dossier dossier = dossierRepository.findById(UUID.fromString(dossierId))
                .orElseThrow(() -> new BusinessException(
                        "Dossier introuvable: " + dossierId));
        accessGuard.checkAttachmentUploadAccess(dossier, accessCode);

        Path dir = Paths.get(uploadDir, dossierId);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("Erreur création dossier upload: {}", e.getMessage(), e);
            throw new BusinessException("Erreur création dossier upload : " + e.getMessage());
        }

        AttachmentSource effectiveSource = source != null ? source : AttachmentSource.INITIAL_SUBMISSION;
        ModeObtention effectiveModeObtention = modeObtention != null ? modeObtention : ModeObtention.VOLONTAIRE;
        Agent uploader = resolveUploaderOrNull();

        List<UploadedFile> saved = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                String originalName = file.getOriginalFilename() != null
                        ? file.getOriginalFilename() : "fichier";

                String mime = file.getContentType() != null
                        ? file.getContentType() : "application/octet-stream";

                if (!ALLOWED_MIME_TYPES.contains(mime)) {
                    log.warn("Upload rejeté — type non autorisé '{}' pour {}",
                            mime, file.getOriginalFilename());
                    continue;
                }

                String ext = "";
                int dotIdx = originalName.lastIndexOf('.');
                if (dotIdx > 0) ext = originalName.substring(dotIdx);

                String storedName = UUID.randomUUID() + ext;
                Path filePath = dir.resolve(storedName);
                byte[] fileBytes = file.getBytes();
                Files.write(filePath, fileBytes);

                String hash = computeHash(fileBytes);
                AttachmentType attType = detectType(mime, originalName);

                Attachment att = Attachment.builder()
                        .dossier(dossier)
                        .originalName(originalName)
                        .storedName(storedName)
                        .filePath(filePath.toString())
                        .mimeType(mime)
                        .fileSizeBytes(file.getSize())
                        .hashSha256(hash)
                        .type(attType)
                        .source(effectiveSource)
                        .modeObtention(effectiveModeObtention)
                        .personneRemettante(personneRemettante)
                        .uploadedBy(uploader)
                        .code(generateUniqueAttachmentCode(effectiveSource))
                        .status(AttachmentStatus.PENDING_VALIDATION)
                        .uploadedAt(LocalDateTime.now())
                        .build();

                attachmentRepository.save(att);
                log.info("Fichier sauvegardé: {}", originalName);

                saved.add(new UploadedFile(att.getId().toString(), originalName, mime,
                        file.getSize(), mime.contains("audio")));

            } catch (Exception e) {
                log.error("Erreur upload fichier {}: {}",
                        file.getOriginalFilename(), e.getMessage(), e);
            }
        }

        return saved;
    }

    public List<AttachmentSummary> listByDossier(UUID dossierId) {
        return attachmentRepository.findByDossierId(dossierId).stream()
                .map(att -> new AttachmentSummary(
                        att.getId().toString(),
                        att.getOriginalName(),
                        att.getMimeType(),
                        att.getFileSizeBytes(),
                        att.getUploadedAt() != null ? att.getUploadedAt().toString() : "",
                        att.getMimeType() != null && att.getMimeType().contains("audio"),
                        att.getStatus().name(),
                        att.getDescription(),
                        att.getSource() != null ? att.getSource().name() : null,
                        att.getModeObtention() != null ? att.getModeObtention().name() : null,
                        att.getCode()))
                .toList();
    }

    public Attachment getOrThrow(UUID attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pièce jointe introuvable : " + attachmentId));
    }

    @Transactional
    public void delete(Attachment attachment) {
        try {
            Files.deleteIfExists(Paths.get(attachment.getFilePath()));
        } catch (IOException e) {
            throw new BusinessException("Erreur suppression fichier : " + e.getMessage());
        }
        attachmentRepository.delete(attachment);
    }

    private Agent resolveUploaderOrNull() {
        return securityUtils.getCurrentKeycloakId()
                .flatMap(agentRepository::findByKeycloakId)
                .orElse(null);
    }

    private String generateUniqueAttachmentCode(AttachmentSource source) {
        long sequence = attachmentRepository.count() + 1;
        String code;
        do {
            code = accessCodeGenerator.generateAttachmentCode(source, sequence);
            sequence++;
        } while (attachmentRepository.existsByCode(code));
        return code;
    }

    private String computeHash(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    private AttachmentType detectType(String contentType, String fileName) {
        if (contentType == null) contentType = "";
        String fn = fileName.toLowerCase();

        if (contentType.contains("audio")
                || fn.endsWith(".mp3") || fn.endsWith(".webm")
                || fn.endsWith(".wav") || fn.endsWith(".ogg"))
            return AttachmentType.AUDIO_EVIDENCE;

        if (contentType.contains("video")
                || fn.endsWith(".mp4") || fn.endsWith(".avi")
                || fn.endsWith(".mov"))
            return AttachmentType.VIDEO;

        if (contentType.contains("image")
                || fn.endsWith(".jpg") || fn.endsWith(".jpeg")
                || fn.endsWith(".png"))
            return AttachmentType.PHOTO;

        return AttachmentType.DOCUMENT;
    }
}
```

- [ ] **Step 6: Update `AttachmentController.uploadFiles()`**

In `src/main/java/gov/bf/ascelc/univers_audits/controller/AttachmentController.java`, add the imports:

```java
import gov.bf.ascelc.univers_audits.enums.AttachmentSource;
import gov.bf.ascelc.univers_audits.enums.ModeObtention;
```

Replace:

```java
    @PostMapping("/dossier/{dossierId}")
    public ResponseEntity<?> uploadFiles(
            @PathVariable String dossierId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "accessCode", required = false) String accessCode) {

        log.info("Upload {} fichier(s) pour dossier {}", files.size(), dossierId);

        List<AttachmentStorageService.UploadedFile> saved =
                attachmentStorageService.upload(dossierId, files, accessCode);

        return ResponseEntity.ok(Map.of(
                "uploaded", saved.size(),
                "files",    saved
        ));
    }
```

with:

```java
    @PostMapping("/dossier/{dossierId}")
    public ResponseEntity<?> uploadFiles(
            @PathVariable String dossierId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "accessCode", required = false) String accessCode,
            @RequestParam(value = "source", required = false) AttachmentSource source,
            @RequestParam(value = "modeObtention", required = false) ModeObtention modeObtention,
            @RequestParam(value = "personneRemettante", required = false) String personneRemettante) {

        log.info("Upload {} fichier(s) pour dossier {}", files.size(), dossierId);

        List<AttachmentStorageService.UploadedFile> saved =
                attachmentStorageService.upload(
                        dossierId, files, accessCode, source, modeObtention, personneRemettante);

        return ResponseEntity.ok(Map.of(
                "uploaded", saved.size(),
                "files",    saved
        ));
    }
```

- [ ] **Step 7: Write migration 029**

Create `src/main/resources/db/changelog/migrations/029-add-attachment-chain-of-custody.sql`:

```sql
--liquibase formatted sql
--changeset dev:029-add-attachment-chain-of-custody

ALTER TABLE attachment ADD COLUMN uploaded_by_id UUID REFERENCES agent(id);
ALTER TABLE attachment ADD COLUMN personne_remettante VARCHAR(255);
ALTER TABLE attachment ADD COLUMN mode_obtention VARCHAR(20) NOT NULL DEFAULT 'VOLONTAIRE';
ALTER TABLE attachment ADD COLUMN code VARCHAR(20);

CREATE UNIQUE INDEX idx_attachment_code ON attachment (code) WHERE code IS NOT NULL;

COMMENT ON COLUMN attachment.uploaded_by_id IS 'Auteur du depot (Agent) - null pour un depot citoyen anonyme via accessCode';
COMMENT ON COLUMN attachment.personne_remettante IS 'Personne ayant physiquement remis la piece, distincte de l auteur du depot';
COMMENT ON COLUMN attachment.mode_obtention IS 'Volontaire ou requisition - plan de travail S8.2';
COMMENT ON COLUMN attachment.code IS 'Code auto-genere ACC-<lettre-provenance>-NNNNN, null pour les pieces anterieures a cette migration';
```

This migration is auto-discovered via `includeAll` — no changelog-master.yaml edit needed. No tracked migration creates the `attachment` table in this repo (confirmed by search — the baseline schema predates Liquibase tracking here), so this is a normal additive `ALTER TABLE` against the table as it already exists, same shape as every other migration in this repo.

- [ ] **Step 8: Update the existing test and add new coverage**

Replace the full content of
`src/test/java/gov/bf/ascelc/univers_audits/service/AttachmentStorageServiceTest.java`:

```java
package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.AttachmentSource;
import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.ModeObtention;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Attachment;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.AttachmentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.utils.AccessCodeGenerator;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import gov.bf.ascelc.univers_audits.shared.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentStorageServiceTest {

    @Mock private AttachmentRepository attachmentRepository;
    @Mock private DossierRepository    dossierRepository;
    @Mock private AgentRepository      agentRepository;
    @Mock private DossierAccessGuard   accessGuard;
    @Mock private SecurityUtils        securityUtils;
    @Mock private AccessCodeGenerator  accessCodeGenerator;

    @InjectMocks
    private AttachmentStorageService service;

    private Dossier buildDossier(UUID id) {
        return Dossier.builder().id(id).status(DossierStatus.EN_INVESTIGATION).build();
    }

    @Test
    void upload_checksAttachmentUploadAccessBeforeWritingAnything() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        doThrow(new BusinessException("Accès refusé"))
                .when(accessGuard).checkAttachmentUploadAccess(dossier, null);

        assertThatThrownBy(() -> service.upload(
                dossierId.toString(), List.of(), null, null, null, null))
                .isInstanceOf(BusinessException.class);

        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void upload_defaultsSourceAndModeObtentionWhenNotProvided() {
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

        service.upload(dossierId.toString(), List.of(file), null, null, null, null);

        verify(attachmentRepository).save(argThat(a ->
                a.getSource() == AttachmentSource.INITIAL_SUBMISSION
                        && a.getModeObtention() == ModeObtention.VOLONTAIRE
                        && a.getUploadedBy() == null));
    }

    @Test
    void upload_usesProvidedSourceModeObtentionAndPersonneRemettante() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);
        MockMultipartFile file = new MockMultipartFile(
                "files", "preuve.jpg", "image/jpeg", "contenu".getBytes());

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.empty());
        when(attachmentRepository.count()).thenReturn(4L);
        when(accessCodeGenerator.generateAttachmentCode(any(), anyLong()))
                .thenReturn("ACC-T-00005");
        when(attachmentRepository.existsByCode("ACC-T-00005")).thenReturn(false);
        when(attachmentRepository.save(any(Attachment.class)))
                .thenAnswer(inv -> {
                    Attachment a = inv.getArgument(0);
                    a.setId(UUID.randomUUID());
                    return a;
                });

        service.upload(dossierId.toString(), List.of(file), null,
                AttachmentSource.FIELD_INVESTIGATION, ModeObtention.REQUISITION, "Jean Kaboré");

        verify(attachmentRepository).save(argThat(a ->
                a.getSource() == AttachmentSource.FIELD_INVESTIGATION
                        && a.getModeObtention() == ModeObtention.REQUISITION
                        && a.getPersonneRemettante().equals("Jean Kaboré")
                        && a.getCode().equals("ACC-T-00005")));
    }

    @Test
    void upload_setsUploadedByWhenAgentAuthenticated() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        MockMultipartFile file = new MockMultipartFile(
                "files", "preuve.pdf", "application/pdf", "contenu".getBytes());

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.of("kc-agent-1"));
        when(agentRepository.findByKeycloakId("kc-agent-1")).thenReturn(Optional.of(agent));
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

        service.upload(dossierId.toString(), List.of(file), null, null, null, null);

        verify(attachmentRepository).save(argThat(a -> a.getUploadedBy() == agent));
    }

    @Test
    void generateUniqueAttachmentCode_retriesOnCollision() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = buildDossier(dossierId);
        MockMultipartFile file = new MockMultipartFile(
                "files", "preuve.pdf", "application/pdf", "contenu".getBytes());

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.empty());
        when(attachmentRepository.count()).thenReturn(0L);
        when(accessCodeGenerator.generateAttachmentCode(any(), eq(1L))).thenReturn("ACC-S-00001");
        when(accessCodeGenerator.generateAttachmentCode(any(), eq(2L))).thenReturn("ACC-S-00002");
        when(attachmentRepository.existsByCode("ACC-S-00001")).thenReturn(true);
        when(attachmentRepository.existsByCode("ACC-S-00002")).thenReturn(false);
        when(attachmentRepository.save(any(Attachment.class)))
                .thenAnswer(inv -> {
                    Attachment a = inv.getArgument(0);
                    a.setId(UUID.randomUUID());
                    return a;
                });

        service.upload(dossierId.toString(), List.of(file), null, null, null, null);

        verify(attachmentRepository).save(argThat(a -> a.getCode().equals("ACC-S-00002")));
    }

    @Test
    void listByDossier_populatesEnrichedFields() {
        UUID dossierId = UUID.randomUUID();
        Attachment att = Attachment.builder()
                .id(UUID.randomUUID())
                .originalName("preuve.pdf")
                .mimeType("application/pdf")
                .fileSizeBytes(1024L)
                .status(gov.bf.ascelc.univers_audits.enums.AttachmentStatus.PENDING_VALIDATION)
                .description("Facture suspecte")
                .source(AttachmentSource.FIELD_INVESTIGATION)
                .modeObtention(ModeObtention.REQUISITION)
                .code("ACC-T-00007")
                .build();
        when(attachmentRepository.findByDossierId(dossierId)).thenReturn(List.of(att));

        List<AttachmentStorageService.AttachmentSummary> summaries = service.listByDossier(dossierId);

        assertThat(summaries).hasSize(1);
        AttachmentStorageService.AttachmentSummary summary = summaries.get(0);
        assertThat(summary.description()).isEqualTo("Facture suspecte");
        assertThat(summary.source()).isEqualTo("FIELD_INVESTIGATION");
        assertThat(summary.modeObtention()).isEqualTo("REQUISITION");
        assertThat(summary.code()).isEqualTo("ACC-T-00007");
    }
}
```

Note: add `import static org.mockito.ArgumentMatchers.eq;` is already covered by the wildcard
`import static org.mockito.Mockito.*;` — no separate import needed for `eq`.

- [ ] **Step 9: Run the tests**

Run: `mvn -q test -Dtest=AttachmentStorageServiceTest`
Expected: BUILD SUCCESS, 6/6 tests passing.

- [ ] **Step 10: Compile the full project**

Run: `mvn -q compile`
Expected: BUILD SUCCESS (confirms `AttachmentController` and all other call sites compile against
the changed `upload()` signature and entity).

- [ ] **Step 11: Run the full suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS, same pre-existing `UniversAuditsApplicationTests.contextLoads`
environment-only failure (no live datasource) as every prior sub-chantier this session, no other
failures.

- [ ] **Step 12: Commit**

```bash
git add src/main/java/gov/bf/ascelc/univers_audits/enums/ModeObtention.java \
        src/main/java/gov/bf/ascelc/univers_audits/model/entity/Attachment.java \
        src/main/java/gov/bf/ascelc/univers_audits/shared/utils/AccessCodeGenerator.java \
        src/main/java/gov/bf/ascelc/univers_audits/repository/AttachmentRepository.java \
        src/main/java/gov/bf/ascelc/univers_audits/service/AttachmentStorageService.java \
        src/main/java/gov/bf/ascelc/univers_audits/controller/AttachmentController.java \
        src/main/resources/db/changelog/migrations/029-add-attachment-chain-of-custody.sql \
        src/test/java/gov/bf/ascelc/univers_audits/service/AttachmentStorageServiceTest.java
git commit -m "feat: add attachment chain-of-custody fields, auto-generated code, and enriched pieces index"
```

---

## Self-Review Notes (for the plan author, not a task)

- **Spec coverage:** §8.2 point "chaîne de possession" (empreinte déjà existante, horodatage déjà
  existant, auteur du dépôt, personne remettante, origine — bug corrigé, mode d'obtention, codage
  automatique) → Steps 1-2, 5-6. Point "index des pièces" (description, provenance, date de
  remise déjà existante, caractère volontaire, numéro de code) → Step 5 (`AttachmentSummary`).
  Point "codage automatique du type ACC-A-00001" → Steps 3-5. Points hors périmètre (audit
  lecture/écriture, original/copie de travail, versionnage/suppression logique, `ChainePossession`
  comme registre de transferts, rétro-codage, `validate()`/`.reject()`) correctly absent.
- **Single task, no decomposition needed**: every file change ripples from the same
  `upload()` signature change (new params → new entity fields → new repository method → new
  generator method → new controller params → new test coverage) — nothing here is independently
  reviewable or mergeable on its own, so splitting into multiple tasks would only add coordination
  overhead without a real quality gate between the pieces.
- **Compile-safety verified**: `upload()`'s only caller is `AttachmentController.uploadFiles()`
  (confirmed by the earlier exploration — single call site), updated in the same step-set.
  `AttachmentStorageServiceTest` is the only test file calling `upload()` directly, updated in
  Step 8. No other file in the codebase references `AttachmentSummary`'s record components
  positionally in a way the 4 new trailing fields would break (Java records are constructed by
  name at each call site here, not deconstructed via pattern matching anywhere in this codebase).
- **Type consistency verified**: `AttachmentSource`/`ModeObtention` parameter names and types are
  identical across `AttachmentStorageService.upload()`'s signature, `AttachmentController`'s
  `@RequestParam`s, and the test file's call sites. `AccessCodeGenerator.generateAttachmentCode`
  signature (`AttachmentSource, long`) matches its only call site in
  `generateUniqueAttachmentCode`.
