package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.mapper.DeclarantMapper;
import gov.bf.ascelc.univers_audits.model.dto.DeclarantDto;
import gov.bf.ascelc.univers_audits.model.entity.Declarant;
import gov.bf.ascelc.univers_audits.repository.DeclarantRepository;
import gov.bf.ascelc.univers_audits.service.DeclarantService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeclarantServiceImpl implements DeclarantService {

    private final DeclarantRepository declarantRepository;
    private final DeclarantMapper declarantMapper;

    @Override
    @Transactional
    public DeclarantDto create(DeclarantDto declarantDto) {
        Declarant declarant = declarantMapper.toEntity(declarantDto);
        declarant.setId(null);

        Declarant savedDeclarant = declarantRepository.save(declarant);
        return declarantMapper.toDto(savedDeclarant);
    }

    @Override
    public List<DeclarantDto> findAll() {
        return declarantMapper.toDtos(declarantRepository.findAll());
    }

    @Override
    public Optional<DeclarantDto> findById(UUID id) {
        return declarantRepository.findById(id).map(declarantMapper::toDto);
    }

    @Override
    @Transactional
    public DeclarantDto update(UUID id, DeclarantDto declarantDto) {
        Declarant declarant = declarantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Declarant not found"));
        declarantMapper.updateEntityFromDto(declarantDto, declarant);
        declarantRepository.save(declarant);
        return declarantMapper.toDto(declarant);
    }

    @Override
    public void delete(UUID id) {
        declarantRepository.deleteById(id);
    }
}
