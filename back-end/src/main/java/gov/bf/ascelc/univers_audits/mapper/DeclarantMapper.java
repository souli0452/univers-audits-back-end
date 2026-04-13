package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.model.dto.request.DeclarantCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DeclarantResponse;
import gov.bf.ascelc.univers_audits.model.entity.Declarant;
import org.mapstruct.*;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.WARN
)
public interface DeclarantMapper {

    @Mapping(target = "displayName", ignore = true)
    DeclarantResponse toResponse(Declarant declarant);

    @AfterMapping
    default void fillAndMask(
            Declarant declarant,
            @MappingTarget DeclarantResponse response) {
        response.setDisplayName(declarant.getDisplayName());
        if (declarant.isAnonymous()) {
            response.setFirstName(null);
            response.setLastName(null);
            response.setEmail(null);
            response.setPhoneNumber(null);
            response.setAddress(null);
            response.setCommune(null);
            response.setProvince(null);
        }
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdById", ignore = true)
    @Mapping(target = "updatedById", ignore = true)
    @Mapping(target = "cases", ignore = true)
    // Valeurs par défaut si le frontend n'envoie pas ces champs
    @Mapping(target = "anonymous",
            source = "anonymous",
            defaultValue = "false")
    @Mapping(target = "protectionRequested",
            source = "protectionRequested",
            defaultValue = "false")
    @Mapping(target = "dataProcessingConsent",
            source = "dataProcessingConsent",
            defaultValue = "false")
    @Mapping(target = "notificationsAccepted",
            source = "notificationsAccepted",
            defaultValue = "true")
    Declarant toEntity(DeclarantCreateRequest request);
}