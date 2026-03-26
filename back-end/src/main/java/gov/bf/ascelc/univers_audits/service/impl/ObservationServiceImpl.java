package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.mapper.ObservationMapper;
import gov.bf.ascelc.univers_audits.model.dto.ObservationDto;
import gov.bf.ascelc.univers_audits.model.entity.Observation;
import gov.bf.ascelc.univers_audits.repository.ObservationRepository;
import gov.bf.ascelc.univers_audits.service.ObservationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ObservationServiceImpl implements ObservationService {

    private final ObservationRepository observationRepository;
    private final ObservationMapper observationMapper;

    @Override
    @Transactional
    public ObservationDto create(ObservationDto observationDto) {
        Observation observation = observationMapper.toEntity(observationDto);
        observation.setId(null);

        Observation savedObservation = observationRepository.save(observation);
        return observationMapper.toDto(savedObservation);
    }

    @Override
    public List<ObservationDto> findAll() {
        return observationMapper.toDtos(observationRepository.findAll());
    }

    @Override
    public Optional<ObservationDto> findById(UUID id) {
        return observationRepository.findById(id).map(observationMapper::toDto);
    }

    @Override
    @Transactional
    public ObservationDto update(UUID id, ObservationDto observationDto) {
        Observation observation = observationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Observation not found"));
        observationMapper.updateEntityFromDto(observationDto, observation);
        observationRepository.save(observation);
        return observationMapper.toDto(observation);
    }

    @Override
    public void delete(UUID id) {
        observationRepository.deleteById(id);
    }
}
