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
@Transactional(readOnly = true)
public class RequeteParquetService {

    private final RequeteParquetRepository requeteParquetRepository;
    private final InvestigationRepository investigationRepository;
    private final DossierAccessGuard accessGuard;

    @Transactional
    public RequeteParquet enregistrer(UUID investigationId, RequeteParquetRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new BusinessException(
                    "Accès refusé — ce dossier est confidentiel.");
        }

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
