package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.FicheRetexRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.FicheRetexResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.FicheRetex;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.TypeInfraction;
import gov.bf.ascelc.univers_audits.repository.FicheRetexRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.TypeInfractionRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
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
public class FicheRetexService {

    private final FicheRetexRepository ficheRetexRepository;
    private final InvestigationRepository investigationRepository;
    private final TypeInfractionRepository typeInfractionRepository;
    private final DossierAccessGuard accessGuard;
    private final AgentContextResolver agentContextResolver;

    @Transactional
    public FicheRetexResponse creer(UUID investigationId, FicheRetexRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (investigation.getCgeApprovedAt() == null) {
            throw new BusinessException(
                    "La rédaction d'une fiche RETEX n'est possible qu'après la décision finale du CGE.");
        }

        if (ficheRetexRepository.findByInvestigationId(investigationId).isPresent()) {
            throw new BusinessException(
                    "Une fiche RETEX existe déjà pour cette investigation.");
        }

        TypeInfraction typeInfraction = null;
        if (request.getTypeInfractionId() != null) {
            typeInfraction = typeInfractionRepository.findById(request.getTypeInfractionId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Type d'infraction introuvable : " + request.getTypeInfractionId()));
        }

        Agent agent = agentContextResolver.getCurrentAgent();

        FicheRetex fiche = FicheRetex.builder()
                .investigation(investigation)
                .typeInfraction(typeInfraction)
                .lieu(request.getLieu())
                .difficultesRencontrees(request.getDifficultesRencontrees())
                .origineSoupcons(request.getOrigineSoupcons())
                .impactFinancier(request.getImpactFinancier())
                .originaliteSchemas(request.getOriginaliteSchemas())
                .collaborateursPlanifies(request.getCollaborateursPlanifies())
                .joursCharges(request.getJoursCharges())
                .contexte(request.getContexte())
                .strategieMethodes(request.getStrategieMethodes())
                .syntheseResultats(request.getSyntheseResultats())
                .enseignementsAxesAmelioration(request.getEnseignementsAxesAmelioration())
                .redigePar(agent)
                .build();

        FicheRetex saved = ficheRetexRepository.save(fiche);
        log.info("Fiche RETEX rédigée — investigation: {}", investigationId);
        return toResponse(saved);
    }

    public FicheRetexResponse obtenir(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        FicheRetex fiche = ficheRetexRepository.findByInvestigationId(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune fiche RETEX n'a été rédigée pour cette investigation."));
        return toResponse(fiche);
    }

    private FicheRetexResponse toResponse(FicheRetex fiche) {
        return FicheRetexResponse.builder()
                .id(fiche.getId())
                .investigationId(fiche.getInvestigation().getId())
                .typeInfractionId(fiche.getTypeInfraction() != null ? fiche.getTypeInfraction().getId() : null)
                .typeInfractionLibelle(fiche.getTypeInfraction() != null ? fiche.getTypeInfraction().getLibelle() : null)
                .lieu(fiche.getLieu())
                .difficultesRencontrees(fiche.getDifficultesRencontrees())
                .origineSoupcons(fiche.getOrigineSoupcons())
                .impactFinancier(fiche.getImpactFinancier())
                .originaliteSchemas(fiche.getOriginaliteSchemas())
                .collaborateursPlanifies(fiche.getCollaborateursPlanifies())
                .joursCharges(fiche.getJoursCharges())
                .contexte(fiche.getContexte())
                .strategieMethodes(fiche.getStrategieMethodes())
                .syntheseResultats(fiche.getSyntheseResultats())
                .enseignementsAxesAmelioration(fiche.getEnseignementsAxesAmelioration())
                .redigeParNom(fiche.getRedigePar().getNomComplet())
                .createdAt(fiche.getCreatedAt())
                .build();
    }

    private Investigation getInvestigationOrThrow(UUID investigationId) {
        return investigationRepository.findById(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + investigationId));
    }
}
