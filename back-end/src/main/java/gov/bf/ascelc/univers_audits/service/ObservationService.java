package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.ObservationDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ObservationService {
    ObservationDto create(ObservationDto observationDto);

    List<ObservationDto> findAll();

    Optional<ObservationDto> findById(UUID id);

    ObservationDto update(UUID id, ObservationDto observationDto);

    void delete(UUID id);
}
