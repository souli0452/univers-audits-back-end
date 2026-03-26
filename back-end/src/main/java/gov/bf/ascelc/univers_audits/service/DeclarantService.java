package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.DeclarantDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeclarantService {
    DeclarantDto create(DeclarantDto declarantDto);

    List<DeclarantDto> findAll();

    Optional<DeclarantDto> findById(UUID id);

    DeclarantDto update(UUID id, DeclarantDto declarantDto);

    void delete(UUID id);
}
