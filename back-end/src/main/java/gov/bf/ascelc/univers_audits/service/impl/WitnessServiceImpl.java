package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.mapper.WitnessMapper;
import gov.bf.ascelc.univers_audits.model.dto.WitnessDto;
import gov.bf.ascelc.univers_audits.model.entity.Witness;
import gov.bf.ascelc.univers_audits.repository.WitnessRepository;
import gov.bf.ascelc.univers_audits.service.WitnessService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WitnessServiceImpl implements WitnessService {

    private final WitnessRepository witnessRepository;
    private final WitnessMapper witnessMapper;

    @Override
    @Transactional
    public WitnessDto create(WitnessDto witnessDto) {
        Witness witness = witnessMapper.toEntity(witnessDto);
        witness.setId(null);

        Witness savedWitness = witnessRepository.save(witness);
        return witnessMapper.toDto(savedWitness);
    }

    @Override
    public List<WitnessDto> findAll() {
        return witnessMapper.toDtos(witnessRepository.findAll());
    }

    @Override
    public Optional<WitnessDto> findById(UUID id) {
        return witnessRepository.findById(id).map(witnessMapper::toDto);
    }

    @Override
    @Transactional
    public WitnessDto update(UUID id, WitnessDto witnessDto) {
        Witness witness = witnessRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Witness not found"));
        witnessMapper.updateEntityFromDto(witnessDto, witness);
        witnessRepository.save(witness);
        return witnessMapper.toDto(witness);
    }

    @Override
    public void delete(UUID id) {
        witnessRepository.deleteById(id);
    }
}
