package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.mapper.DossierMapper;
import gov.bf.ascelc.univers_audits.model.dto.DossierDto;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.service.DossierService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DossierServiceImpl implements DossierService {

    private final DossierRepository dossierRepository;
    private final DossierMapper dossierMapper;

    @Override
    @Transactional
    public DossierDto create(DossierDto dossierDto) {
        Dossier dossier = dossierMapper.toEntity(dossierDto);
        dossier.setId(null);

        Dossier savedDossier = dossierRepository.save(dossier);
        return dossierMapper.toDto(savedDossier);
    }
    @Override
    public List<DossierDto> findAll() {
        return dossierMapper.toDtos(dossierRepository.findAll());
    }

    @Override
    public Optional<DossierDto> findById(UUID id) {

        return dossierRepository.findById(id).map(dossierMapper::toDto);
    }

    @Override
    @Transactional
    public DossierDto update(UUID id, DossierDto dossierDto) {
        Dossier dossier = dossierRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Dossier not found"));
        dossierMapper.updateEntityFromDto(dossierDto, dossier);
        dossierRepository.save(dossier);
        return dossierMapper.toDto(dossier);
    }

    @Override
    public void delete(UUID id) {
        dossierRepository.deleteById(id);
    }

    }