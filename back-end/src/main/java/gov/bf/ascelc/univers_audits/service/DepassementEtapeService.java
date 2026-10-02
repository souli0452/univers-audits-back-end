package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.model.dto.response.ActeurEtapeDepassementResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.DelaiEtapeResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.EtapeDepassementResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.StatutDelaiEtape;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Étapes du circuit de traitement en retard, regroupées par acteur (CGEA, CGE).
 *
 * <p>Distinct de « Dépassements par acteur », qui regroupe par agent en charge du dossier : ces étapes sont
 * confiées à des rôles, pas à l'agent du dossier, et les lui attribuer serait trompeur.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepassementEtapeService {

    private static final List<DossierStatus> STATUTS_TERMINES = List.of(DossierStatus.CLOS, DossierStatus.CLASSE);

    private final DossierRepository dossierRepository;
    private final DelaiEtapeService delaiEtapeService;

    @Transactional(readOnly = true)
    public List<ActeurEtapeDepassementResponse> parActeur() {
        return parActeur(Instant.now());
    }

    /** Version testable : l'instant courant est fourni. */
    @Transactional(readOnly = true)
    public List<ActeurEtapeDepassementResponse> parActeur(Instant maintenant) {
        Map<String, List<EtapeDepassementResponse>> parActeur = new TreeMap<>();

        for (Dossier dossier : dossierRepository.findByStatusNotIn(STATUTS_TERMINES)) {
            if (dossier.getReceptionDate() == null) continue;
            try {
                for (DelaiEtapeResponse etape
                        : delaiEtapeService.evaluer(dossier.getId(), dossier.getReceptionDate(), maintenant)) {
                    if (etape.getStatut() != StatutDelaiEtape.DEPASSE) continue;
                    parActeur.computeIfAbsent(etape.getActeur(), a -> new ArrayList<>())
                            .add(EtapeDepassementResponse.builder()
                                    .dossierId(dossier.getId())
                                    .numero(dossier.getNumber())
                                    .code(etape.getCode())
                                    .libelle(etape.getLibelle())
                                    .echeance(etape.getEcheance())
                                    .heuresDeRetard(Duration.between(etape.getEcheance(), maintenant).toHours())
                                    .build());
                }
            } catch (RuntimeException e) {
                // Un dossier en échec ne doit pas masquer les retards des autres.
                log.error("[DepassementEtape] Échec pour le dossier {} : {}", dossier.getNumber(), e.getMessage(), e);
            }
        }

        List<ActeurEtapeDepassementResponse> resultat = new ArrayList<>();
        parActeur.forEach((acteur, etapes) -> {
            etapes.sort(Comparator.comparingLong(EtapeDepassementResponse::getHeuresDeRetard).reversed());
            resultat.add(ActeurEtapeDepassementResponse.builder().acteur(acteur).etapes(etapes).build());
        });
        return resultat;
    }
}
