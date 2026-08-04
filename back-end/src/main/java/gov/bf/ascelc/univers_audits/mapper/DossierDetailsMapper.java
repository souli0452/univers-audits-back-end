package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.model.dto.request.*;
import gov.bf.ascelc.univers_audits.model.dto.response.*;
import gov.bf.ascelc.univers_audits.model.entity.*;
import org.mapstruct.*;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.WARN
)
public interface DossierDetailsMapper {

    // NOTE — bug MapStruct/Lombok confirme (voir DeclarantMapper/DossierMapper) :
    // @AfterMapping n'est jamais invoque quand la cible est un @Builder Lombok.
    // Tous les DTO de ce mapper sont des @Builder, donc chaque toResponse(...)
    // ci-dessous enveloppe sa methode generee par MapStruct (renommee mapToResponse
    // en cas de surcharge) dans une methode default qui appelle explicitement le
    // hook. C'est particulierement critique pour fillWitness : sans ce correctif,
    // le masquage d'identite d'un temoin anonyme ne s'appliquait jamais.

    default TargetedPartyResponse toResponse(TargetedParty targetedParty) {
        TargetedPartyResponse response = mapToResponse(targetedParty);
        if (response != null) {
            fillTargetedParty(targetedParty, response);
        }
        return response;
    }

    @Mapping(target = "displayName", ignore = true)
    TargetedPartyResponse mapToResponse(TargetedParty targetedParty);

    default void fillTargetedParty(
            TargetedParty party,
            TargetedPartyResponse response) {
        response.setDisplayName(party.getDisplayName());
    }

    default WitnessResponse toResponse(Witness witness) {
        WitnessResponse response = mapToResponse(witness);
        if (response != null) {
            fillWitness(witness, response);
        }
        return response;
    }

    @Mapping(target = "displayName", ignore = true)
    WitnessResponse mapToResponse(Witness witness);

    default void fillWitness(
            Witness witness,
            WitnessResponse response) {
        response.setDisplayName(witness.getDisplayName());
        if (witness.isAnonymous()) {
            response.setFirstName(null);
            response.setLastName(null);
            response.setPhoneNumber(null);
            response.setEmail(null);
            response.setAddress(null);
            response.setProfession(null);
        }
    }

    ObservationResponse toResponse(Observation observation);

    @Mapping(target = "downloadUrl", ignore = true)
    @Mapping(target = "thumbnailUrl", ignore = true)
    AttachmentResponse toResponse(Attachment attachment);

    default NotificationResponse toResponse(Notification notification) {
        NotificationResponse response = mapToResponse(notification);
        if (response != null) {
            fillNotification(notification, response);
        }
        return response;
    }

    @Mapping(target = "overdue", ignore = true)
    NotificationResponse mapToResponse(Notification notification);

    default void fillNotification(
            Notification notification,
            NotificationResponse response) {
        response.setOverdue(notification.isOverdue());
    }

    StatusHistoryResponse toResponse(StatusHistory statusHistory);

    default AuditionResponse toResponse(Audition audition) {
        AuditionResponse response = mapToResponse(audition);
        if (response != null) {
            fillAudition(audition, response);
        }
        return response;
    }

    @Mapping(target = "investigationId", source = "investigation.id")
    @Mapping(target = "intervieweeDisplayName", ignore = true)
    @Mapping(target = "conductedByName", source = "conductedBy.nomComplet")
    AuditionResponse mapToResponse(Audition audition);

    default void fillAudition(
            Audition audition,
            AuditionResponse response) {
        response.setIntervieweeDisplayName(audition.getIntervieweeDisplayName());
    }

    @Mapping(target = "auditionId", source = "audition.id")
    @Mapping(target = "draftedByName", source = "draftedBy.nomComplet")
    PvAuditionResponse toResponse(PVAudition pvAudition);

    default DemandeDocumentsResponse toResponse(DemandeDocuments demandeDocuments) {
        DemandeDocumentsResponse response = mapToResponse(demandeDocuments);
        if (response != null) {
            fillDemandeDocuments(demandeDocuments, response);
        }
        return response;
    }

    @Mapping(target = "investigationId", source = "investigation.id")
    @Mapping(target = "requestedByName", source = "requestedBy.nomComplet")
    @Mapping(target = "overdue", ignore = true)
    DemandeDocumentsResponse mapToResponse(DemandeDocuments demandeDocuments);

    default void fillDemandeDocuments(
            DemandeDocuments demandeDocuments,
            DemandeDocumentsResponse response) {
        response.setOverdue(demandeDocuments.isOverdue());
    }

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

    default DecisionCGEResponse toResponse(DecisionCGE decision) {
        DecisionCGEResponse response = mapToResponse(decision);
        if (response != null) {
            fillDecisionCGE(decision, response);
        }
        return response;
    }

    @Mapping(target = "agentCGEId",  ignore = true)
    @Mapping(target = "agentCGENom", ignore = true)
    DecisionCGEResponse mapToResponse(DecisionCGE decision);

    default void fillDecisionCGE(
            DecisionCGE decisionCge,
            DecisionCGEResponse response) {
        if (decisionCge.getAgentCGE() != null) {
            response.setAgentCGEId(decisionCge.getAgentCGE().getId());
            response.setAgentCGENom(decisionCge.getAgentCGE().getNomComplet());
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
}