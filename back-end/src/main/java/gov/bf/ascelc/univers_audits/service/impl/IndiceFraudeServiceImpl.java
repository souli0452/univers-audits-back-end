package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.model.dto.request.IndiceFraudeRequest;
import gov.bf.ascelc.univers_audits.model.entity.IndiceFraude;
import gov.bf.ascelc.univers_audits.repository.IndiceFraudeRepository;
import gov.bf.ascelc.univers_audits.service.IndiceFraudeService;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IndiceFraudeServiceImpl implements IndiceFraudeService {

    private final IndiceFraudeRepository repository;

    @Override
    public List<IndiceFraude> findAllActifs(String categorie) {
        if (StringUtils.hasText(categorie)) {
            return repository.findByActifTrueAndCategorieOrderByOrdreAsc(categorie);
        }
        return repository.findByActifTrueOrderByOrdreAsc();
    }

    @Override
    public List<IndiceFraude> findAll() {
        return repository.findAll();
    }

    @Override
    @Transactional
    public IndiceFraude create(IndiceFraudeRequest request) {
        if (repository.existsByCode(request.getCode())) {
            throw new ConflictException(
                    "Un indice de fraude avec ce code existe déjà : " + request.getCode());
        }

        IndiceFraude indice = IndiceFraude.builder()
                .code(request.getCode())
                .libelle(request.getLibelle())
                .categorie(request.getCategorie())
                .description(request.getDescription())
                .actif(request.getActif())
                .ordre(request.getOrdre() != null ? request.getOrdre() : 0)
                .build();

        IndiceFraude saved = repository.save(indice);
        log.info("[IndiceFraude] '{}' créé", saved.getCode());
        return saved;
    }

    @Override
    @Transactional
    public IndiceFraude update(String code, IndiceFraudeRequest request) {
        IndiceFraude indice = repository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Indice de fraude introuvable : " + code));

        indice.setLibelle(request.getLibelle());
        indice.setCategorie(request.getCategorie());
        indice.setDescription(request.getDescription());
        indice.setActif(request.getActif());
        indice.setOrdre(request.getOrdre() != null ? request.getOrdre() : 0);

        IndiceFraude saved = repository.save(indice);
        log.info("[IndiceFraude] '{}' mis à jour", code);
        return saved;
    }
}
