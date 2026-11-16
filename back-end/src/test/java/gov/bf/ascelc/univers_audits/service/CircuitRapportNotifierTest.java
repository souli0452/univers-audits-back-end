package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.EtapeCircuitRapport;
import gov.bf.ascelc.univers_audits.enums.NotificationChannel;
import gov.bf.ascelc.univers_audits.enums.NotificationType;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.Notification;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CircuitRapportNotifierTest {

    @Mock private AgentService agentService;
    @Mock private NotificationRepository notifications;

    @InjectMocks
    private CircuitRapportNotifier notifier;

    private static Investigation investigation(String numero) {
        return Investigation.builder().id(UUID.randomUUID()).dossier(Dossier.builder().number(numero).build()).build();
    }

    private static Agent agent(String keycloakId) {
        return Agent.builder().id(UUID.randomUUID()).keycloakId(keycloakId).build();
    }

    @Test
    void lesEtapesDuCircuitCorrespondentAuxRolesDuWorkflow() {
        assertThat(EtapeCircuitRapport.JURIDIQUE.roleKey()).isEqualTo("CONSEILLER_JURIDIQUE");
        assertThat(EtapeCircuitRapport.DEI.roleKey()).isEqualTo("CHEF_DEI");
        assertThat(EtapeCircuitRapport.CGEA.roleKey()).isEqualTo("CGEA");
        assertThat(EtapeCircuitRapport.CGE.roleKey()).isEqualTo("CGE");
    }

    @Test
    void previentChaquePersonneActiveDeLEtapeQuiAttendUneDecision() {
        when(agentService.findActiveByKeycloakRole("CHEF_DEI")).thenReturn(List.of(agent("kc-1"), agent("kc-2")));

        notifier.prevenirEtape(investigation("DOS-1"), EtapeCircuitRapport.DEI);

        ArgumentCaptor<Notification> envoyees = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(2)).save(envoyees.capture());
        assertThat(envoyees.getAllValues()).extracting(Notification::getRecipient).containsExactly("kc-1", "kc-2");
        Notification n = envoyees.getAllValues().get(0);
        assertThat(n.getType()).isEqualTo(NotificationType.INTERNAL_ALERT);
        assertThat(n.getChannel()).isEqualTo(NotificationChannel.PORTAL);
        assertThat(n.getSubject()).contains("DOS-1").contains("chef du DEI");
    }

    @Test
    void unRejetPrevientLEtapeQuiDoitCorrigerEtDonneLeMotif() {
        when(agentService.findActiveByKeycloakRole("CONSEILLER_JURIDIQUE")).thenReturn(List.of(agent("kc-cj")));

        notifier.prevenirRejet(investigation("DOS-1"), EtapeCircuitRapport.JURIDIQUE, "Preuves insuffisantes");

        ArgumentCaptor<Notification> envoyee = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(envoyee.capture());
        assertThat(envoyee.getValue().getRecipient()).isEqualTo("kc-cj");
        assertThat(envoyee.getValue().getSubject()).contains("renvoyé").contains("DOS-1");
        assertThat(envoyee.getValue().getContent()).contains("Preuves insuffisantes");
    }

    @Test
    void sansPersonneActiveNiNotificationNiErreur() {
        when(agentService.findActiveByKeycloakRole("CHEF_DEI")).thenReturn(List.of());

        assertThatCode(() -> notifier.prevenirEtape(investigation("DOS-1"), EtapeCircuitRapport.DEI))
                .doesNotThrowAnyException();

        verify(notifications, never()).save(any());
    }

    @Test
    void uneAgentSansCompteKeycloakEstIgnore() {
        when(agentService.findActiveByKeycloakRole("CGEA")).thenReturn(List.of(agent(null), agent("kc-ok")));

        notifier.prevenirEtape(investigation("DOS-1"), EtapeCircuitRapport.CGEA);

        verify(notifications, times(1)).save(any());
    }

    @Test
    void unePanneNeFaitJamaisEchouerLActionMetier() {
        when(agentService.findActiveByKeycloakRole("CGE")).thenThrow(new RuntimeException("Keycloak injoignable"));

        assertThatCode(() -> notifier.prevenirEtape(investigation("DOS-1"), EtapeCircuitRapport.CGE))
                .doesNotThrowAnyException();
    }

    @Test
    void leNumeroDeDossierAbsentNeCasseRien() {
        when(agentService.findActiveByKeycloakRole("CGE")).thenReturn(List.of(agent("kc-1")));

        notifier.prevenirEtape(investigation(null), EtapeCircuitRapport.CGE);

        ArgumentCaptor<Notification> envoyee = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(envoyee.capture());
        assertThat(envoyee.getValue().getSubject()).contains("en attente de numéro");
    }
}
