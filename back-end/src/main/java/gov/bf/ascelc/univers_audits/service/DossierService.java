package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.DossierDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DossierService {
    DossierDto create(DossierDto dossierDto);

    List<DossierDto> findAll();

    Optional<DossierDto> findById(UUID id);

    DossierDto update(UUID id, DossierDto dossierDto);

    void delete(UUID id);
}
