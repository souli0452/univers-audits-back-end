package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.model.dto.response.*;
import gov.bf.ascelc.univers_audits.model.entity.*;
import org.mapstruct.*;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.WARN
)
public interface DossierDetailsMapper {

    @Mapping(target = "displayName", ignore = true)
    TargetedPartyResponse toResponse(TargetedParty targetedParty);

    @AfterMapping
    default void fillTargetedParty(
            TargetedParty party,
            @MappingTarget TargetedPartyResponse response) {
        response.setDisplayName(party.getDisplayName());
    }

    @Mapping(target = "displayName", ignore = true)
    WitnessResponse toResponse(Witness witness);

    @AfterMapping
    default void fillWitness(
            Witness witness,
            @MappingTarget WitnessResponse response) {
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

    @Mapping(target = "overdue", ignore = true)
    NotificationResponse toResponse(Notification notification);

    @AfterMapping
    default void fillNotification(
            Notification notification,
            @MappingTarget NotificationResponse response) {
        response.setOverdue(notification.isOverdue());
    }

    StatusHistoryResponse toResponse(StatusHistory statusHistory);

    @Mapping(target = "investigationId", source = "investigation.id")
    @Mapping(target = "intervieweeDisplayName", ignore = true)
    @Mapping(target = "conductedByName", source = "conductedBy.nomComplet")
    AuditionResponse toResponse(Audition audition);

    @AfterMapping
    default void fillAudition(
            Audition audition,
            @MappingTarget AuditionResponse response) {
        response.setIntervieweeDisplayName(audition.getIntervieweeDisplayName());
    }
}