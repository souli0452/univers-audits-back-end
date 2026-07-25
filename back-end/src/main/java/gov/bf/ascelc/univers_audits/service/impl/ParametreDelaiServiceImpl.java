package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.model.dto.request.ParametreDelaiRequest;
import gov.bf.ascelc.univers_audits.model.entity.ParametreDelai;
import gov.bf.ascelc.univers_audits.repository.ParametreDelaiRepository;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParametreDelaiServiceImpl implements ParametreDelaiService {

    private final ParametreDelaiRepository repository;

    @Override
    public int resolveDelaiJours(String code) {
        ParametreDelai delai = repository.findByCode(code)
                .filter(ParametreDelai::getActif)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Paramètre de délai introuvable ou inactif : " + code));

        if (delai.getValeurJours() == null) {
            throw new ResourceNotFoundException(
                    "Paramètre de délai sans valeur configurée : " + code);
        }
        return delai.getValeurJours();
    }

    @Override
    public List<ParametreDelai> findAllActifs() {
        return repository.findByActifTrueOrderByCodeAsc();
    }

    @Override
    public List<ParametreDelai> findAll() {
        return repository.findAll();
    }

    @Override
    @Transactional
    public ParametreDelai update(String code, ParametreDelaiRequest request) {
        ParametreDelai delai = repository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Paramètre de délai introuvable : " + code));

        delai.setLibelle(request.getLibelle());
        delai.setValeurJours(request.getValeurJours());
        delai.setJoursOuvrables(request.getJoursOuvrables());
        delai.setActif(request.getActif());

        ParametreDelai saved = repository.save(delai);
        log.info("[ParametreDelai] '{}' mis à jour", code);
        return saved;
    }
}
