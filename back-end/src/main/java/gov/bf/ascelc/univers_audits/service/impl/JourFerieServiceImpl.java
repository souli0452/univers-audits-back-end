package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.model.dto.request.JourFerieRequest;
import gov.bf.ascelc.univers_audits.model.entity.JourFerie;
import gov.bf.ascelc.univers_audits.repository.JourFerieRepository;
import gov.bf.ascelc.univers_audits.service.JourFerieService;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JourFerieServiceImpl implements JourFerieService {

    private final JourFerieRepository repository;

    @Override
    public List<JourFerie> findAllActifs() {
        return repository.findByActifTrueOrderByDateAsc();
    }

    @Override
    public List<JourFerie> findAll() {
        return repository.findAll();
    }

    @Override
    @Transactional
    public JourFerie create(JourFerieRequest request) {
        if (repository.existsByDate(request.getDate())) {
            throw new ConflictException(
                    "Un jour férié existe déjà pour cette date : " + request.getDate());
        }

        JourFerie jourFerie = JourFerie.builder()
                .date(request.getDate())
                .libelle(request.getLibelle())
                .actif(request.getActif())
                .build();

        JourFerie saved = repository.save(jourFerie);
        log.info("[JourFerie] '{}' créé", saved.getDate());
        return saved;
    }

    @Override
    @Transactional
    public JourFerie update(UUID id, JourFerieRequest request) {
        JourFerie jourFerie = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Jour férié introuvable : " + id));

        jourFerie.setDate(request.getDate());
        jourFerie.setLibelle(request.getLibelle());
        jourFerie.setActif(request.getActif());

        JourFerie saved = repository.save(jourFerie);
        log.info("[JourFerie] '{}' mis à jour", id);
        return saved;
    }
}
