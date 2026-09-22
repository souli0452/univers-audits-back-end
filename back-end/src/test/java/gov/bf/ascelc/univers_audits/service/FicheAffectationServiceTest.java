package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.DecisionCgeAffectation;
import gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationCreateRequest;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.FicheAffectation;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.DepartementRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.FicheAffectationRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import gov.bf.ascelc.univers_audits.shared.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FicheAffectationServiceTest {

    @Mock private FicheAffectationRepository ficheAffectationRepository;
    @Mock private DossierRepository dossierRepository;
    @Mock private DepartementRepository departementRepository;
    @Mock private AgentRepository agentRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private PortalConfigService portalConfigService;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private SecurityUtils securityUtils;
    @Mock private DossierAccessGuard accessGuard;

    @InjectMocks
    private FicheAffectationService service;

    private UUID dossierId;
    private Dossier dossier;
    private Agent agentCourant;

    @BeforeEach
    void setUp() {
        dossierId = UUID.randomUUID();
        dossier = Dossier.builder().id(dossierId).number("ASCE-2026-001").build();
        agentCourant = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-cge").actif(true).build();
    }

    private void stubAgentCourant() {
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.of("kc-cge"));
        when(agentRepository.findByKeycloakId("kc-cge")).thenReturn(Optional.of(agentCourant));
    }

    @Test
    void creer_construitLaFicheAvecLaSection2() {
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(ficheAffectationRepository.existsByDossierId(dossierId)).thenReturn(false);
        stubAgentCourant();
        when(ficheAffectationRepository.save(any(FicheAffectation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FicheAffectationCreateRequest request = FicheAffectationCreateRequest.builder()
                .decisionCge(DecisionCgeAffectation.ECHANGE_PREALABLE)
                .observationsCge("À rediscuter avec le CGEA")
                .build();

        FicheAffectation result = service.creer(dossierId, request);

        assertThat(result.getDossier()).isEqualTo(dossier);
        assertThat(result.getDecisionCge()).isEqualTo(DecisionCgeAffectation.ECHANGE_PREALABLE);
        assertThat(result.getObservationsCge()).isEqualTo("À rediscuter avec le CGEA");
        assertThat(result.getAgentCge()).isEqualTo(agentCourant);
        assertThat(result.getDateDecisionCge()).isNotNull();
        verify(accessGuard).checkReadAccess(dossier);
    }

    @Test
    void creer_rejetteSiUneFicheExisteDejaPourCeDossier() {
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(ficheAffectationRepository.existsByDossierId(dossierId)).thenReturn(true);

        FicheAffectationCreateRequest request = FicheAffectationCreateRequest.builder()
                .decisionCge(DecisionCgeAffectation.AFFECTATION_DIRECTE_CGEA)
                .build();

        assertThatThrownBy(() -> service.creer(dossierId, request))
                .isInstanceOf(ConflictException.class);
        verify(ficheAffectationRepository, never()).save(any());
    }

    @Test
    void creer_rejetteSiDossierIntrouvable() {
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.empty());

        FicheAffectationCreateRequest request = FicheAffectationCreateRequest.builder()
                .decisionCge(DecisionCgeAffectation.AFFECTATION_DIRECTE_CGEA)
                .build();

        assertThatThrownBy(() -> service.creer(dossierId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void creer_rejetteSiAgentCourantIntrouvable() {
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(ficheAffectationRepository.existsByDossierId(dossierId)).thenReturn(false);
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.empty());

        FicheAffectationCreateRequest request = FicheAffectationCreateRequest.builder()
                .decisionCge(DecisionCgeAffectation.AFFECTATION_DIRECTE_CGEA)
                .build();

        assertThatThrownBy(() -> service.creer(dossierId, request))
                .isInstanceOf(BusinessException.class);
    }
}
