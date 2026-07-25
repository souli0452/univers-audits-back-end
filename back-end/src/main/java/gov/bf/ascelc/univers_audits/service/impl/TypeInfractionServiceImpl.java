package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.model.dto.request.TypeInfractionRequest;
import gov.bf.ascelc.univers_audits.model.entity.TypeInfraction;
import gov.bf.ascelc.univers_audits.repository.TypeInfractionRepository;
import gov.bf.ascelc.univers_audits.service.TypeInfractionService;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
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
public class TypeInfractionServiceImpl implements TypeInfractionService {

    private final TypeInfractionRepository repository;

    @Override
    public List<TypeInfraction> findAllActifs() {
        return repository.findByActifTrueOrderByOrdreAsc();
    }

    @Override
    public List<TypeInfraction> findAll() {
        return repository.findAll();
    }

    @Override
    @Transactional
    public TypeInfraction create(TypeInfractionRequest request) {
        if (repository.existsByCode(request.getCode())) {
            throw new ConflictException(
                    "Un type d'infraction avec ce code existe déjà : " + request.getCode());
        }

        TypeInfraction infraction = TypeInfraction.builder()
                .code(request.getCode())
                .libelle(request.getLibelle())
                .articleCodePenal(request.getArticleCodePenal())
                .articleLoi004(request.getArticleLoi004())
                .impliqueDdip(request.getImpliqueDdip())
                .actif(request.getActif())
                .ordre(request.getOrdre() != null ? request.getOrdre() : 0)
                .build();

        TypeInfraction saved = repository.save(infraction);
        log.info("[TypeInfraction] '{}' créé", saved.getCode());
        return saved;
    }

    @Override
    @Transactional
    public TypeInfraction update(String code, TypeInfractionRequest request) {
        TypeInfraction infraction = repository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Type d'infraction introuvable : " + code));

        infraction.setLibelle(request.getLibelle());
        infraction.setArticleCodePenal(request.getArticleCodePenal());
        infraction.setArticleLoi004(request.getArticleLoi004());
        infraction.setImpliqueDdip(request.getImpliqueDdip());
        infraction.setActif(request.getActif());
        infraction.setOrdre(request.getOrdre() != null ? request.getOrdre() : 0);

        TypeInfraction saved = repository.save(infraction);
        log.info("[TypeInfraction] '{}' mis à jour", code);
        return saved;
    }
}
