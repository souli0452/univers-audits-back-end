package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.PointChecklistDossierTravailRequest;
import gov.bf.ascelc.univers_audits.model.entity.PointChecklistDossierTravail;
import gov.bf.ascelc.univers_audits.repository.PointChecklistDossierTravailRepository;
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
public class PointChecklistDossierTravailService {

    private final PointChecklistDossierTravailRepository repository;

    public List<PointChecklistDossierTravail> findAllActifs() {
        return repository.findByActifTrueOrderByOrdreAsc();
    }

    public List<PointChecklistDossierTravail> findAll() {
        return repository.findAllByOrderByOrdreAsc();
    }

    @Transactional
    public PointChecklistDossierTravail create(PointChecklistDossierTravailRequest request) {
        PointChecklistDossierTravail point = PointChecklistDossierTravail.builder()
                .code(nextCode())
                .libelle(request.getLibelle())
                .categorie(request.getCategorie())
                .ordre(request.getOrdre())
                .actif(request.getActif() == null || request.getActif())
                .build();
        PointChecklistDossierTravail saved = repository.save(point);
        log.info("[PointChecklistDossierTravail] '{}' créé", saved.getCode());
        return saved;
    }

    @Transactional
    public PointChecklistDossierTravail update(String code, PointChecklistDossierTravailRequest request) {
        PointChecklistDossierTravail point = repository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Point de check-list introuvable : " + code));
        point.setLibelle(request.getLibelle());
        point.setCategorie(request.getCategorie());
        point.setOrdre(request.getOrdre());
        if (request.getActif() != null) {
            point.setActif(request.getActif());
        }
        PointChecklistDossierTravail saved = repository.save(point);
        log.info("[PointChecklistDossierTravail] '{}' mis à jour", code);
        return saved;
    }

    private String nextCode() {
        int n = 1;
        String candidate;
        do {
            candidate = String.format("PT-%02d", n++);
        } while (repository.existsByCode(candidate));
        return candidate;
    }
}
