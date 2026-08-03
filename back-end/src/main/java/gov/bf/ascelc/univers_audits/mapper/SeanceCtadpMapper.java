package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.model.dto.response.SeanceCtadpDossierResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.SeanceCtadpResponse;
import gov.bf.ascelc.univers_audits.model.entity.SeanceCTADP;
import gov.bf.ascelc.univers_audits.model.entity.SeanceCtadpDossier;
import org.mapstruct.*;

import java.util.Collections;
import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.WARN
)
public interface SeanceCtadpMapper {

    // NOTE — bug MapStruct/Lombok confirme (voir DeclarantMapper/DossierMapper/
    // DossierDetailsMapper/InvestigationMapper) : @AfterMapping jamais invoque
    // quand la cible est un @Builder Lombok. SeanceCtadpResponse en est un —
    // on enveloppe donc la methode generee dans une methode default qui
    // remplit la liste des dossiers nous-memes.
    default SeanceCtadpResponse toResponse(SeanceCTADP seance) {
        SeanceCtadpResponse response = mapToResponse(seance);
        if (response != null) {
            fillDossiers(seance, response);
        }
        return response;
    }

    @Mapping(target = "dossiers", ignore = true)
    SeanceCtadpResponse mapToResponse(SeanceCTADP seance);

    default void fillDossiers(SeanceCTADP seance, SeanceCtadpResponse response) {
        List<SeanceCtadpDossier> dossiers = seance.getDossiers();
        response.setDossiers(
                dossiers != null
                        ? dossiers.stream().map(this::toDossierResponse).toList()
                        : Collections.emptyList());
    }

    @Mapping(target = "dossierId",     source = "dossier.id")
    @Mapping(target = "dossierNumber", source = "dossier.number")
    @Mapping(target = "dossierObject", source = "dossier.object")
    SeanceCtadpDossierResponse toDossierResponse(SeanceCtadpDossier dossier);
}
