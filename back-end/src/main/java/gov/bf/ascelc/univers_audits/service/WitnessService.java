package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.WitnessDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WitnessService {
    WitnessDto create(WitnessDto witnessDto);

    List<WitnessDto> findAll();

    Optional<WitnessDto> findById(UUID id);

    WitnessDto update(UUID id, WitnessDto witnessDto);

    void delete(UUID id);
}
