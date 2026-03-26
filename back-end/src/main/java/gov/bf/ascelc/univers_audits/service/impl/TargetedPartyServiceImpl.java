package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.mapper.TargetedPartyMapper;
import gov.bf.ascelc.univers_audits.model.dto.TargetedPartyDto;
import gov.bf.ascelc.univers_audits.model.entity.TargetedParty;
import gov.bf.ascelc.univers_audits.repository.TargetPartyRepository;
import gov.bf.ascelc.univers_audits.service.TargetedPartyService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TargetedPartyServiceImpl implements TargetedPartyService {

    private final TargetPartyRepository targetedPartyRepository;
    private final TargetedPartyMapper targetedPartyMapper;

    @Override
    @Transactional
    public TargetedPartyDto create(TargetedPartyDto targetedPartyDto) {
        TargetedParty targetedParty = targetedPartyMapper.toEntity(targetedPartyDto);
        targetedParty.setId(null);

        TargetedParty savedTargetedParty = targetedPartyRepository.save(targetedParty);
        return targetedPartyMapper.toDto(savedTargetedParty);
    }

    @Override
    public List<TargetedPartyDto> findAll() {
        return targetedPartyMapper.toDtos(targetedPartyRepository.findAll());
    }

    @Override
    public Optional<TargetedPartyDto> findById(UUID id) {
        return targetedPartyRepository.findById(id).map(targetedPartyMapper::toDto);
    }

    @Override
    @Transactional
    public TargetedPartyDto update(UUID id, TargetedPartyDto targetedPartyDto) {
        TargetedParty targetedParty = targetedPartyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("TargetedParty not found"));
        targetedPartyMapper.updateEntityFromDto(targetedPartyDto, targetedParty);
        targetedPartyRepository.save(targetedParty);
        return targetedPartyMapper.toDto(targetedParty);
    }

    @Override
    public void delete(UUID id) {
        targetedPartyRepository.deleteById(id);
    }
}
