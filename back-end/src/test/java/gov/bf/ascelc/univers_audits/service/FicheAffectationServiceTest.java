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

    @Test
    void affecter_departementEligible_metAJourLaFicheEtNotifieLesAgentsDuDepartement() {
        FicheAffectation fiche = FicheAffectation.builder().dossier(dossier).build();
        UUID departementId = UUID.randomUUID();
        Agent agentDep1 = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-dep-1").actif(true).build();
        Agent agentDep2Inactif = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-dep-2").actif(false).build();
        gov.bf.ascelc.univers_audits.model.entity.Departement dei =
                gov.bf.ascelc.univers_audits.model.entity.Departement.builder()
                        .id(departementId).code("DEI").libelle("Enquête et Investigation")
                        .agents(java.util.List.of(agentDep1, agentDep2Inactif))
                        .build();

        when(ficheAffectationRepository.findByDossierId(dossierId)).thenReturn(Optional.of(fiche));
        when(departementRepository.findById(departementId)).thenReturn(Optional.of(dei));
        stubAgentCourant();
        when(ficheAffectationRepository.save(any(FicheAffectation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(portalConfigService.resolveNotificationText(eq("notif_subject_affectation_dossier"), any()))
                .thenReturn("sujet");
        when(portalConfigService.resolveNotificationText(eq("notif_content_affectation_dossier"), any()))
                .thenReturn("contenu");

        gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest request =
                gov.bf.ascelc.univers_audits.model.dto.request.FicheAffectationAffectationRequest.builder()
                        .typeDesignation(gov.bf.ascelc.univers_audits.enums.TypeDesignation.DEPARTEMENT)
                        .departementDesigneId(departementId)
                        .build();

        FicheAffectation result = service.affecter(dossierId, request);

        assertThat(result.getDepartementDesigne()).isEqualTo(dei);
        assertThat(result.getAgentCgea()).isEqualTo(agentCourant);
        assertThat(result.getDateImputation()).isNotNull();
        // Un seul agent actif dans le département -> une seule notification créée
        verify(notificationRepository, times(1)).save(any());
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
                .isInstanceOf(BusinessException.class);
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
                .isInstanceOf(BusinessException.class);
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
    void suivre_agentDuDepartementDesigne_estAutorise() {
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
                .isInstanceOf(BusinessException.class);
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
        when(securityUtils.hasRole("AGENT_BRPD")).thenReturn(true);
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
                .isInstanceOf(BusinessException.class);
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
                .isInstanceOf(BusinessException.class);
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
        when(securityUtils.hasRole("AGENT_BRPD")).thenReturn(false);

        assertThatThrownBy(() -> service.getOrThrow(dossierId))
                .isInstanceOf(BusinessException.class);
    }
}
