package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.DecisionCgeAffectation;
import gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationCreateRequest;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.FicheAffectation;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.DepartementRepository;
import gov.bf.ascelc.univers_audits.repository.DossierHabilitationRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.FicheAffectationRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock private DossierHabilitationService habilitationService;
    @Mock private DossierHabilitationRepository habilitationRepository;

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
    void creer_rejetteSiDossierNonReceptionne() {
        Dossier dossierNonReceptionne = Dossier.builder().id(dossierId).number(null).build();
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossierNonReceptionne));

        FicheAffectationCreateRequest request = FicheAffectationCreateRequest.builder()
                .decisionCge(DecisionCgeAffectation.AFFECTATION_DIRECTE_CGEA)
                .build();

        assertThatThrownBy(() -> service.creer(dossierId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("doit être réceptionné");
        verify(ficheAffectationRepository, never()).save(any());
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
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Agent non authentifié");
    }

    private gov.bf.ascelc.univers_audits.model.entity.Departement departement(String code) {
        return gov.bf.ascelc.univers_audits.model.entity.Departement.builder()
                .id(UUID.randomUUID()).code(code).libelle("Département " + code).build();
    }

    private gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest imputationA(gov.bf.ascelc.univers_audits.model.entity.Departement departement) {
        return gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest.builder()
                .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.DEPARTEMENT)
                .departementDesigneId(departement.getId())
                .build();
    }

    private void stubImputation(FicheAffectation fiche, gov.bf.ascelc.univers_audits.model.entity.Departement dep) {
        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(departementRepository.findById(dep.getId())).thenReturn(Optional.of(dep));
        stubAgentCourant();
        when(ficheAffectationRepository.save(any(FicheAffectation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(portalConfigService.resolveNotificationText(eq("notif_subject_affectation_dossier"), any()))
                .thenReturn("sujet");
        when(portalConfigService.resolveNotificationText(eq("notif_content_affectation_dossier"), any()))
                .thenReturn("contenu");
    }

    @Test
    void affecter_departementEligible_neNotifieNiNHabiliteQueLeChefDuDepartement() {
        FicheAffectation fiche = FicheAffectation.builder().dossier(dossier).build();
        gov.bf.ascelc.univers_audits.model.entity.Departement dei = departement("DEI");
        gov.bf.ascelc.univers_audits.model.entity.Departement dac = departement("DAC");
        Agent chefDei = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-chef-dei").actif(true)
                .departement(dei).build();
        Agent chefDeiInactif = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-chef-inactif").actif(false)
                .departement(dei).build();
        Agent chefAilleurs = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-chef-ailleurs").actif(true)
                .departement(dac).build();
        stubImputation(fiche, dei);
        when(keycloakAdminService.getUserIdsByRole("CHEF_DEI"))
                .thenReturn(java.util.List.of("kc-chef-dei", "kc-chef-inactif", "kc-chef-ailleurs"));
        when(agentRepository.findByKeycloakId("kc-chef-dei")).thenReturn(Optional.of(chefDei));
        when(agentRepository.findByKeycloakId("kc-chef-inactif")).thenReturn(Optional.of(chefDeiInactif));
        when(agentRepository.findByKeycloakId("kc-chef-ailleurs")).thenReturn(Optional.of(chefAilleurs));

        FicheAffectation result = service.affecter(dossierId, imputationA(dei));

        assertThat(result.getDepartementDesigne()).isEqualTo(dei);
        assertThat(result.getAgentCgea()).isEqualTo(agentCourant);
        assertThat(result.getDateImputation()).isNotNull();
        org.mockito.ArgumentCaptor<gov.bf.ascelc.univers_audits.model.entity.Notification> notifications =
                org.mockito.ArgumentCaptor.forClass(gov.bf.ascelc.univers_audits.model.entity.Notification.class);
        verify(notificationRepository, times(1)).save(notifications.capture());
        assertThat(notifications.getValue().getRecipient()).isEqualTo("kc-chef-dei");
        verify(habilitationService).grant(eq(dossier), eq(chefDei), eq(gov.bf.ascelc.univers_audits.enums.HabilitationSource.MANUAL), eq(agentCourant), contains("DEI"));
        verifyNoMoreInteractions(habilitationService);
    }

    @Test
    void affecter_departementSansChef_estRefuseAvecUnMessageClair() {
        FicheAffectation fiche = FicheAffectation.builder().dossier(dossier).build();
        gov.bf.ascelc.univers_audits.model.entity.Departement dei = departement("DEI");
        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(departementRepository.findById(dei.getId())).thenReturn(Optional.of(dei));
        when(keycloakAdminService.getUserIdsByRole("CHEF_DEI")).thenReturn(java.util.List.of());

        assertThatThrownBy(() -> service.affecter(dossierId, imputationA(dei)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Aucun chef")
                .hasMessageContaining("CHEF_DEI");

        verify(ficheAffectationRepository, never()).save(any());
        verifyNoInteractions(notificationRepository, habilitationService);
    }

    // ───────────────────────────── répartition par le chef ─────────────────────────────

    private FicheAffectation ficheImputeeA(gov.bf.ascelc.univers_audits.model.entity.Departement departement) {
        return FicheAffectation.builder().dossier(dossier)
                .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.DEPARTEMENT)
                .departementDesigne(departement).build();
    }

    private Agent agentDuDepartement(String kc, gov.bf.ascelc.univers_audits.model.entity.Departement departement, boolean actif) {
        return Agent.builder().id(UUID.randomUUID()).keycloakId(kc).actif(actif).departement(departement).build();
    }

    private void stubChefCourant(Agent chef) {
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.of(chef.getKeycloakId()));
        when(agentRepository.findByKeycloakId(chef.getKeycloakId())).thenReturn(Optional.of(chef));
    }

    @Test
    void repartir_habiliteEtPrevientLesAgentsChoisisDeSonDepartement() {
        gov.bf.ascelc.univers_audits.model.entity.Departement dei = departement("DEI");
        Agent chef = agentDuDepartement("kc-chef", dei, true);
        Agent a1 = agentDuDepartement("kc-a1", dei, true);
        Agent a2 = agentDuDepartement("kc-a2", dei, true);
        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(ficheImputeeA(dei)));
        stubChefCourant(chef);
        when(agentRepository.findById(a1.getId())).thenReturn(Optional.of(a1));
        when(agentRepository.findById(a2.getId())).thenReturn(Optional.of(a2));
        when(portalConfigService.resolveNotificationText(eq("notif_subject_affectation_dossier"), any())).thenReturn("sujet");
        when(portalConfigService.resolveNotificationText(eq("notif_content_affectation_dossier"), any())).thenReturn("contenu");

        service.repartir(dossierId, java.util.List.of(a1.getId(), a2.getId(), a1.getId()));

        verify(habilitationService).grant(eq(dossier), eq(a1), eq(gov.bf.ascelc.univers_audits.enums.HabilitationSource.MANUAL), eq(chef), contains("DEI"));
        verify(habilitationService).grant(eq(dossier), eq(a2), eq(gov.bf.ascelc.univers_audits.enums.HabilitationSource.MANUAL), eq(chef), contains("DEI"));
        verify(notificationRepository, times(2)).save(any());
    }

    @Test
    void repartir_refuseUnAgentQuiNEstPasDuDepartement() {
        gov.bf.ascelc.univers_audits.model.entity.Departement dei = departement("DEI");
        gov.bf.ascelc.univers_audits.model.entity.Departement dac = departement("DAC");
        Agent chef = agentDuDepartement("kc-chef", dei, true);
        Agent ailleurs = agentDuDepartement("kc-dac", dac, true);
        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(ficheImputeeA(dei)));
        stubChefCourant(chef);
        when(agentRepository.findById(ailleurs.getId())).thenReturn(Optional.of(ailleurs));

        assertThatThrownBy(() -> service.repartir(dossierId, java.util.List.of(ailleurs.getId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("n'appartient pas au département");

        verifyNoInteractions(habilitationService, notificationRepository);
    }

    @Test
    void repartir_refuseUnAgentInactif() {
        gov.bf.ascelc.univers_audits.model.entity.Departement dei = departement("DEI");
        Agent chef = agentDuDepartement("kc-chef", dei, true);
        Agent parti = agentDuDepartement("kc-parti", dei, false);
        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(ficheImputeeA(dei)));
        stubChefCourant(chef);
        when(agentRepository.findById(parti.getId())).thenReturn(Optional.of(parti));

        assertThatThrownBy(() -> service.repartir(dossierId, java.util.List.of(parti.getId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("actif");

        verifyNoInteractions(habilitationService);
    }

    @Test
    void repartir_refuseQuelquUnQuiNEstPasDuDepartementDesigne() {
        gov.bf.ascelc.univers_audits.model.entity.Departement dei = departement("DEI");
        gov.bf.ascelc.univers_audits.model.entity.Departement dac = departement("DAC");
        Agent chefDac = agentDuDepartement("kc-chef-dac", dac, true);
        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(ficheImputeeA(dei)));
        stubChefCourant(chefDac);
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        assertThatThrownBy(() -> service.repartir(dossierId, java.util.List.of(UUID.randomUUID())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chef du département désigné");

        verifyNoInteractions(habilitationService);
    }

    @Test
    void repartir_refuseUnDossierNonAffecteAUnDepartement() {
        FicheAffectation fiche = FicheAffectation.builder().dossier(dossier)
                .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.BRPD).build();
        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));

        assertThatThrownBy(() -> service.repartir(dossierId, java.util.List.of(UUID.randomUUID())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("département");
    }

    @Test
    void repartir_refuseUneListeVide() {
        gov.bf.ascelc.univers_audits.model.entity.Departement dei = departement("DEI");
        Agent chef = agentDuDepartement("kc-chef", dei, true);
        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(ficheImputeeA(dei)));
        stubChefCourant(chef);

        assertThatThrownBy(() -> service.repartir(dossierId, java.util.List.of()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void affecter_departementNonEligible_estRejete() {
        FicheAffectation fiche = FicheAffectation.builder().dossier(dossier).build();
        UUID departementId = UUID.randomUUID();
        gov.bf.ascelc.univers_audits.model.entity.Departement dsi =
                gov.bf.ascelc.univers_audits.model.entity.Departement.builder()
                        .id(departementId).code("DSI").libelle("Systèmes d'information")
                        .build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(departementRepository.findById(departementId)).thenReturn(Optional.of(dsi));

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest.builder()
                        .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.DEPARTEMENT)
                        .departementDesigneId(departementId)
                        .build();

        assertThatThrownBy(() -> service.affecter(dossierId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("DEI et DAC");
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void affecter_agentCjValideEtActif_estAccepteEtNotifieSeul() {
        FicheAffectation fiche = FicheAffectation.builder().dossier(dossier).build();
        UUID agentCjId = UUID.randomUUID();
        Agent conseiller = Agent.builder().id(agentCjId).keycloakId("kc-cj").actif(true).build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(agentRepository.findById(agentCjId)).thenReturn(Optional.of(conseiller));
        when(keycloakAdminService.getUserRoles("kc-cj")).thenReturn(java.util.List.of("CONSEILLER_JURIDIQUE"));
        stubAgentCourant();
        when(ficheAffectationRepository.save(any(FicheAffectation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(portalConfigService.resolveNotificationText(anyString(), any())).thenReturn("texte");

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest.builder()
                        .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.AGENT_CJ)
                        .agentDesigneId(agentCjId)
                        .build();

        FicheAffectation result = service.affecter(dossierId, request);

        assertThat(result.getAgentDesigne()).isEqualTo(conseiller);
        verify(notificationRepository, times(1)).save(any());
    }

    @Test
    void affecter_agentSansRoleConseillerJuridique_estRejete() {
        FicheAffectation fiche = FicheAffectation.builder().dossier(dossier).build();
        UUID agentId = UUID.randomUUID();
        Agent autreAgent = Agent.builder().id(agentId).keycloakId("kc-autre").actif(true).build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(autreAgent));
        when(keycloakAdminService.getUserRoles("kc-autre")).thenReturn(java.util.List.of("AGENT_BRPD"));

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest.builder()
                        .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.AGENT_CJ)
                        .agentDesigneId(agentId)
                        .build();

        assertThatThrownBy(() -> service.affecter(dossierId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Conseiller Juridique");
    }

    @Test
    void affecter_brpd_resoutLesDestinatairesViaKeycloakEtNeCibleAucuneEntiteLocale() {
        FicheAffectation fiche = FicheAffectation.builder().dossier(dossier).build();
        Agent agentBrpd = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-brpd").actif(true).build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(keycloakAdminService.getUserIdsByRole("AGENT_BRPD")).thenReturn(java.util.List.of("kc-brpd"));
        when(agentRepository.findByKeycloakId("kc-brpd")).thenReturn(Optional.of(agentBrpd));
        stubAgentCourant();
        when(ficheAffectationRepository.save(any(FicheAffectation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(portalConfigService.resolveNotificationText(anyString(), any())).thenReturn("texte");

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest.builder()
                        .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.BRPD)
                        .build();

        FicheAffectation result = service.affecter(dossierId, request);

        assertThat(result.getTypeDesignation()).isEqualTo(gov.bf.ascelc.univers_audits.enums.TypeDesignation.BRPD);
        assertThat(result.getDepartementDesigne()).isNull();
        assertThat(result.getAgentDesigne()).isNull();
        verify(notificationRepository, times(1)).save(any());
    }

    @Test
    void suivre_leChefDuDepartementDesigne_estAutorise() {
        gov.bf.ascelc.univers_audits.model.entity.Departement dei =
                gov.bf.ascelc.univers_audits.model.entity.Departement.builder()
                        .id(UUID.randomUUID()).code("DEI").build();
        Agent agentDuDepartement = Agent.builder()
                .id(UUID.randomUUID()).keycloakId("kc-dep")
                .departement(dei).build();
        FicheAffectation fiche = FicheAffectation.builder()
                .dossier(dossier)
                .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.DEPARTEMENT)
                .departementDesigne(dei)
                .build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(accessGuard.canSeeConfidential()).thenReturn(false);
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.of("kc-dep"));
        when(agentRepository.findByKeycloakId("kc-dep")).thenReturn(Optional.of(agentDuDepartement));
        when(securityUtils.hasPermission(AppPermission.FICHE_AFFECTATION_REPARTIR)).thenReturn(true);
        when(ficheAffectationRepository.save(any(FicheAffectation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest.builder()
                        .etatAvancement(gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation.CLOTURE)
                        .commentairesSuivi("Investigation menée à son terme")
                        .build();

        FicheAffectation result = service.suivre(dossierId, request);

        assertThat(result.getEtatAvancement())
                .isEqualTo(gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation.CLOTURE);
        assertThat(result.getAgentSuivi()).isEqualTo(agentDuDepartement);
        assertThat(result.getDateRetour()).isNotNull();
    }

    @Test
    void suivre_unMembreDuDepartementNiChefNiHabilite_estRefuse() {
        gov.bf.ascelc.univers_audits.model.entity.Departement dei = departement("DEI");
        Agent membre = agentDuDepartement("kc-membre", dei, true);
        FicheAffectation fiche = ficheImputeeA(dei);
        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(accessGuard.canSeeConfidential()).thenReturn(false);
        stubChefCourant(membre);
        when(securityUtils.hasPermission(AppPermission.FICHE_AFFECTATION_REPARTIR)).thenReturn(false);
        when(habilitationRepository.existsByDossierIdAndAgentIdAndRevokedAtIsNull(dossierId, membre.getId()))
                .thenReturn(false);

        var request = gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest.builder()
                .etatAvancement(gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation.EN_COURS).build();

        assertThatThrownBy(() -> service.suivre(dossierId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ne vous a pas été affecté");
    }

    @Test
    void suivre_unMembreDuDepartementHabilitePourLeDossier_estAutorise() {
        gov.bf.ascelc.univers_audits.model.entity.Departement dei = departement("DEI");
        Agent membre = agentDuDepartement("kc-membre", dei, true);
        FicheAffectation fiche = ficheImputeeA(dei);
        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(accessGuard.canSeeConfidential()).thenReturn(false);
        stubChefCourant(membre);
        when(securityUtils.hasPermission(AppPermission.FICHE_AFFECTATION_REPARTIR)).thenReturn(false);
        when(habilitationRepository.existsByDossierIdAndAgentIdAndRevokedAtIsNull(dossierId, membre.getId()))
                .thenReturn(true);
        when(ficheAffectationRepository.save(any(FicheAffectation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var request = gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest.builder()
                .etatAvancement(gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation.EN_COURS).build();

        assertThat(service.suivre(dossierId, request).getAgentSuivi()).isEqualTo(membre);
    }

    @Test
    void suivre_agentDunAutreDepartement_estRejete() {
        gov.bf.ascelc.univers_audits.model.entity.Departement dei =
                gov.bf.ascelc.univers_audits.model.entity.Departement.builder()
                        .id(UUID.randomUUID()).code("DEI").build();
        gov.bf.ascelc.univers_audits.model.entity.Departement dac =
                gov.bf.ascelc.univers_audits.model.entity.Departement.builder()
                        .id(UUID.randomUUID()).code("DAC").build();
        Agent agentDac = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-dac").departement(dac).build();
        FicheAffectation fiche = FicheAffectation.builder()
                .dossier(dossier)
                .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.DEPARTEMENT)
                .departementDesigne(dei)
                .build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(accessGuard.canSeeConfidential()).thenReturn(false);
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.of("kc-dac"));
        when(agentRepository.findByKeycloakId("kc-dac")).thenReturn(Optional.of(agentDac));

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest.builder()
                        .etatAvancement(gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation.EN_COURS)
                        .build();

        assertThatThrownBy(() -> service.suivre(dossierId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ne vous a pas été affecté");
    }

    @Test
    void suivre_agentDesigneNommeHorsDepartement_estAutorise() {
        Agent conseillerDesigne = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-cj").build();
        FicheAffectation fiche = FicheAffectation.builder()
                .dossier(dossier)
                .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.AGENT_CJ)
                .agentDesigne(conseillerDesigne)
                .build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(accessGuard.canSeeConfidential()).thenReturn(false);
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.of("kc-cj"));
        when(agentRepository.findByKeycloakId("kc-cj")).thenReturn(Optional.of(conseillerDesigne));
        when(ficheAffectationRepository.save(any(FicheAffectation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest.builder()
                        .etatAvancement(gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation.EN_COURS)
                        .build();

        FicheAffectation result = service.suivre(dossierId, request);

        assertThat(result.getAgentSuivi()).isEqualTo(conseillerDesigne);
    }

    @Test
    void suivre_roleAgentBrpd_estAutoriseQuandBrpdDesigne() {
        FicheAffectation fiche = FicheAffectation.builder()
                .dossier(dossier)
                .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.BRPD)
                .build();
        Agent agentBrpd = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-brpd").build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(accessGuard.canSeeConfidential()).thenReturn(false);
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.of("kc-brpd"));
        when(agentRepository.findByKeycloakId("kc-brpd")).thenReturn(Optional.of(agentBrpd));
        when(securityUtils.hasPermission(AppPermission.DOSSIER_ENREGISTRER)).thenReturn(true);
        when(ficheAffectationRepository.save(any(FicheAffectation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest.builder()
                        .etatAvancement(gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation.EN_COURS)
                        .build();

        FicheAffectation result = service.suivre(dossierId, request);

        assertThat(result.getAgentSuivi()).isEqualTo(agentBrpd);
    }

    @Test
    void suivre_roleprivilegie_estToujoursAutorise() {
        FicheAffectation fiche = FicheAffectation.builder()
                .dossier(dossier)
                .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.BRPD)
                .build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(accessGuard.canSeeConfidential()).thenReturn(true);
        stubAgentCourant();
        when(ficheAffectationRepository.save(any(FicheAffectation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest.builder()
                        .etatAvancement(gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation.EN_COURS)
                        .build();

        FicheAffectation result = service.suivre(dossierId, request);

        assertThat(result).isNotNull();
    }

    @Test
    void suivre_etatAutreSansPrecision_estRejete() {
        FicheAffectation fiche = FicheAffectation.builder()
                .dossier(dossier)
                .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.BRPD)
                .build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(accessGuard.canSeeConfidential()).thenReturn(true);

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest.builder()
                        .etatAvancement(gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation.AUTRE)
                        .build();

        assertThatThrownBy(() -> service.suivre(dossierId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("précision");
    }

    @Test
    void suivre_sectionCgeaPasEncoreRenseignee_estRejetePourNonPrivilegie() {
        FicheAffectation fiche = FicheAffectation.builder().dossier(dossier).build(); // typeDesignation == null

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationSuiviRequest.builder()
                        .etatAvancement(gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation.EN_COURS)
                        .build();

        assertThatThrownBy(() -> service.suivre(dossierId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pas encore été renseignée");
    }

    @Test
    void getOrThrow_appliqueLaMemeAutorisationDynamiqueQueSuivre() {
        FicheAffectation fiche = FicheAffectation.builder()
                .dossier(dossier)
                .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.BRPD)
                .build();
        Agent agentNonBrpd = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-autre").build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(accessGuard.canSeeConfidential()).thenReturn(false);
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.of("kc-autre"));
        when(agentRepository.findByKeycloakId("kc-autre")).thenReturn(Optional.of(agentNonBrpd));
        when(securityUtils.hasPermission(AppPermission.DOSSIER_ENREGISTRER)).thenReturn(false);

        assertThatThrownBy(() -> service.getOrThrow(dossierId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ne vous a pas été affecté");
    }
}
