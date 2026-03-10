package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.mapper.DossierMapper;
import gov.bf.ascelc.univers_audits.model.dto.DossierDto;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.service.DossierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

}
