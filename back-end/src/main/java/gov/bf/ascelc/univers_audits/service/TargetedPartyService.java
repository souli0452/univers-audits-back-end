package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.TargetedPartyDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TargetedPartyService {
    TargetedPartyDto create(TargetedPartyDto targetedPartyDto);

    List<TargetedPartyDto> findAll();

    Optional<TargetedPartyDto> findById(UUID id);

    TargetedPartyDto update(UUID id, TargetedPartyDto targetedPartyDto);

    void delete(UUID id);
}
