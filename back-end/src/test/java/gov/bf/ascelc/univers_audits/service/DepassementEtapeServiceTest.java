package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.response.ActeurEtapeDepassementResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.DelaiEtapeResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.StatutDelaiEtape;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DepassementEtapeServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-09T10:00:00Z");

    private final DossierRepository dossierRepository = mock(DossierRepository.class);
    private final DelaiEtapeService delaiEtapeService = mock(DelaiEtapeService.class);
    private final DepassementEtapeService service = new DepassementEtapeService(dossierRepository, delaiEtapeService);

    private Dossier dossier(String numero, Instant reception) {
        Dossier d = mock(Dossier.class);
        when(d.getId()).thenReturn(UUID.randomUUID());
        when(d.getNumber()).thenReturn(numero);
        when(d.getReceptionDate()).thenReturn(reception);
        return d;
    }

    private DelaiEtapeResponse etape(String code, String acteur, StatutDelaiEtape statut, Instant echeance) {
        return DelaiEtapeResponse.builder()
                .code(code).libelle("Étape " + code).acteur(acteur)
                .debut(echeance.minusSeconds(3 * 24 * 3600)).echeance(echeance)
                .delaiJours(3).joursOuvrables(true).statut(statut)
                .build();
    }

    private void etapesDe(Dossier d, DelaiEtapeResponse... etapes) {
        when(delaiEtapeService.evaluer(eq(d.getId()), any(), eq(MAINTENANT))).thenReturn(List.of(etapes));
    }

    @Test
    void regroupe_les_etapes_en_retard_par_acteur_du_plus_grand_retard_au_plus_petit() {
        Dossier a = dossier("ASCE-LC-2026-0001", Instant.parse("2026-09-01T10:00:00Z"));
        Dossier b = dossier("ASCE-LC-2026-0002", Instant.parse("2026-09-01T10:00:00Z"));
        when(dossierRepository.findByStatusNotIn(any())).thenReturn(List.of(a, b));
        etapesDe(a,
                etape("ETAPE_ANALYSE_CGEA", "CGEA", StatutDelaiEtape.DEPASSE, Instant.parse("2026-10-08T10:00:00Z")),
                etape("ETAPE_QUITUS_CGE", "CGE", StatutDelaiEtape.DEPASSE, Instant.parse("2026-10-01T10:00:00Z")));
        etapesDe(b,
                etape("ETAPE_AFFECTATION_CGEA", "CGEA", StatutDelaiEtape.DEPASSE, Instant.parse("2026-10-05T10:00:00Z")));

        List<ActeurEtapeDepassementResponse> resultat = service.parActeur(MAINTENANT);

        assertThat(resultat).extracting(ActeurEtapeDepassementResponse::getActeur).containsExactly("CGE", "CGEA");
        ActeurEtapeDepassementResponse cgea = resultat.get(1);
        assertThat(cgea.getEtapes()).extracting(e -> e.getNumero()).containsExactly("ASCE-LC-2026-0002", "ASCE-LC-2026-0001");
        assertThat(cgea.getEtapes().get(0).getHeuresDeRetard()).isEqualTo(96L);
        assertThat(cgea.getEtapes().get(1).getHeuresDeRetard()).isEqualTo(24L);
    }

    @Test
    void ignore_les_etapes_qui_ne_sont_pas_depassees() {
        Dossier a = dossier("ASCE-LC-2026-0001", Instant.parse("2026-09-01T10:00:00Z"));
        when(dossierRepository.findByStatusNotIn(any())).thenReturn(List.of(a));
        Instant echeance = Instant.parse("2026-10-08T10:00:00Z");
        etapesDe(a,
                etape("ETAPE_ANALYSE_CGEA", "CGEA", StatutDelaiEtape.EN_COURS, echeance),
                etape("ETAPE_QUITUS_CGE", "CGE", StatutDelaiEtape.PROCHE, echeance),
                etape("ETAPE_IMPUTATION_CGE", "CGE", StatutDelaiEtape.RESPECTE, echeance),
                etape("ETAPE_AFFECTATION_CGEA", "CGEA", StatutDelaiEtape.TERMINE_EN_RETARD, echeance));

        assertThat(service.parActeur(MAINTENANT)).isEmpty();
    }

    @Test
    void ignore_un_dossier_sans_date_de_reception() {
        Dossier sans = dossier("ASCE-LC-2026-0001", null);
        when(dossierRepository.findByStatusNotIn(any())).thenReturn(List.of(sans));

        assertThat(service.parActeur(MAINTENANT)).isEmpty();
    }

    @Test
    void un_dossier_en_echec_ne_masque_pas_les_retards_des_autres() {
        Dossier casse = dossier("ASCE-LC-2026-0001", Instant.parse("2026-09-01T10:00:00Z"));
        Dossier sain = dossier("ASCE-LC-2026-0002", Instant.parse("2026-09-01T10:00:00Z"));
        when(dossierRepository.findByStatusNotIn(any())).thenReturn(List.of(casse, sain));
        when(delaiEtapeService.evaluer(eq(casse.getId()), any(), eq(MAINTENANT)))
                .thenThrow(new IllegalStateException("données incohérentes"));
        etapesDe(sain, etape("ETAPE_ANALYSE_CGEA", "CGEA", StatutDelaiEtape.DEPASSE,
                Instant.parse("2026-10-08T10:00:00Z")));

        assertThat(service.parActeur(MAINTENANT)).hasSize(1);
    }
}
