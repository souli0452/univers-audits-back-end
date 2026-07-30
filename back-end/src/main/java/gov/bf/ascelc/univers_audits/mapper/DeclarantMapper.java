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

    // DeclarantResponse est construit via un builder Lombok (@Builder) : dans
    // cette configuration MapStruct/Lombok, le hook @AfterMapping n'est
    // jamais invoqué par le code généré (il retourne directement
    // builder.build() sans appeler la méthode de callback) — c'est un
    // problème connu de MapStruct avec les cibles de type builder. On
    // enveloppe donc la méthode générée par MapStruct dans une méthode
    // default qui appelle fillAndMask nous-mêmes, pour garantir que le
    // masquage de confidentialité s'applique réellement.
    default DeclarantResponse toResponse(Declarant declarant) {
        DeclarantResponse response = mapToResponse(declarant);
        if (response != null) {
            fillAndMask(declarant, response);
        }
        return response;
    }

    @Mapping(target = "displayName", ignore = true)
    DeclarantResponse mapToResponse(Declarant declarant);

    default void fillAndMask(
            Declarant declarant,
            DeclarantResponse response) {
        response.setDisplayName(declarant.getDisplayName());
        if (declarant.isAnonymous()) {
            response.setFirstName(null);
            response.setLastName(null);
            response.setEmail(null);
            response.setPhoneNumber(null);
            response.setAddress(null);
            response.setCommune(null);
            response.setProvince(null);
            response.setCellulaire(null);
            response.setLocalite(null);
        }
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdById", ignore = true)
    @Mapping(target = "updatedById", ignore = true)
    @Mapping(target = "cases", ignore = true)
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