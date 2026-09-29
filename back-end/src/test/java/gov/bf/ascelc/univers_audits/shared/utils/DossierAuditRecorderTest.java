package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.ObservationType;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Observation;
import gov.bf.ascelc.univers_audits.repository.ObservationRepository;
import gov.bf.ascelc.univers_audits.repository.StatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DossierAuditRecorderTest {

    @Mock private ObservationRepository observationRepository;
    @Mock private StatusHistoryRepository statusHistoryRepository;
    @InjectMocks private DossierAuditRecorder recorder;

    @Test
    void addDeclarantObservation_garde_l_auteur_technique_et_affiche_le_nom_du_declarant() {
        Agent demandeur = Agent.builder().firstName("Issouf").lastName("Souli").build();
        Dossier dossier = Dossier.builder().status(DossierStatus.EN_ETUDE_OPPORTUNITE).build();

        recorder.addDeclarantObservation(dossier, ObservationType.COMPLEMENT_RESPONSE,
                "Voici les justificatifs", demandeur, "Déclarant (via le portail)");

        ArgumentCaptor<Observation> captor = ArgumentCaptor.forClass(Observation.class);
        verify(observationRepository).save(captor.capture());
        Observation obs = captor.getValue();
        assertThat(obs.getType()).isEqualTo(ObservationType.COMPLEMENT_RESPONSE);
        assertThat(obs.getContent()).isEqualTo("Voici les justificatifs");
        assertThat(obs.getAuthor()).isSameAs(demandeur);
        assertThat(obs.getAuthorFullName()).isEqualTo("Déclarant (via le portail)");
        assertThat(obs.getStatusSnapshot()).isEqualTo(DossierStatus.EN_ETUDE_OPPORTUNITE);
        assertThat(obs).extracting("confidential").isEqualTo(false);
    }
}
