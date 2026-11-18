package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.HabilitationSource;
import gov.bf.ascelc.univers_audits.enums.QualiteDeclarant;
import gov.bf.ascelc.univers_audits.enums.RecommandationCtadp;
import gov.bf.ascelc.univers_audits.enums.RegionOrigine;
import gov.bf.ascelc.univers_audits.model.entity.GeoRegion;
import gov.bf.ascelc.univers_audits.service.GeographieService;
import gov.bf.ascelc.univers_audits.service.GeographieService.OrigineGeographique;
import gov.bf.ascelc.univers_audits.enums.TypologieInstitutionnelle;
import gov.bf.ascelc.univers_audits.enums.SubmissionMode;
import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import gov.bf.ascelc.univers_audits.enums.TypeSaisine;
import gov.bf.ascelc.univers_audits.mapper.DeclarantMapper;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.mapper.DossierMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.DeclarantCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.DossierCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.DossierUpdateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.StatusTransitionRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DeclarantResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResume;
import gov.bf.ascelc.univers_audits.model.dto.response.EtudeOpportuniteResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Declarant;
import gov.bf.ascelc.univers_audits.model.entity.DecisionCGE;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.DeclarantRepository;
import gov.bf.ascelc.univers_audits.repository.DecisionCGERepository;
import gov.bf.ascelc.univers_audits.repository.DossierHabilitationRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.EtudeOpportuniteRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.repository.ObservationRepository;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.service.CurrentUserAuditor;
import gov.bf.ascelc.univers_audits.service.DossierHabilitationService;
import gov.bf.ascelc.univers_audits.service.NotificationDispatcherService;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
import gov.bf.ascelc.univers_audits.service.PortalConfigService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.utils.AccessCodeGenerator;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DeadlineCalculator;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAuditRecorder;
import gov.bf.ascelc.univers_audits.shared.utils.NatureSaisineResolver;
import gov.bf.ascelc.univers_audits.shared.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DossierServiceImplTest {

    @Mock private DossierRepository dossierRepository;
    @Mock private DeclarantRepository declarantRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private ObservationRepository observationRepository;
    @Mock private DossierMapper dossierMapper;
    @Mock private DossierDetailsMapper dossierDetailsMapper;
    @Mock private DeclarantMapper declarantMapper;
    @Mock private AccessCodeGenerator accessCodeGenerator;
    @Mock private SecurityUtils securityUtils;
    @Mock private NotificationDispatcherService notificationDispatcher;
    @Mock private AgentContextResolver agentContextResolver;
    @Mock private DossierAuditRecorder auditRecorder;
    @Mock private ParametreDelaiService parametreDelaiService;
    @Mock private NatureSaisineResolver natureSaisineResolver;
    @Mock private GeographieService geographieService;
    @Mock private DossierAccessGuard accessGuard;
    @Mock private CurrentUserAuditor currentUserAuditor;
    @Mock private DossierHabilitationService habilitationService;
    @Mock private PortalConfigService portalConfigService;
    @Mock private EtudeOpportuniteRepository etudeOpportuniteRepository;
    @Mock private DecisionCGERepository decisionCGERepository;
    @Mock(answer = Answers.CALLS_REAL_METHODS) private DeadlineCalculator deadlineCalculator;

    @InjectMocks
    private DossierServiceImpl service;

    @BeforeEach
    void origineParDefaut() {
        lenient().when(geographieService.resoudreOrigine(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(OrigineGeographique.aucune());
    }

    private DossierCreateRequest buildRequest(QualiteDeclarant quality) {
        return buildRequest(quality, false);
    }

    private DossierCreateRequest buildRequest(QualiteDeclarant quality, boolean anonymous) {
        DeclarantCreateRequest declarantData = DeclarantCreateRequest.builder()
                .typeDeclarant(TypeDeclarant.CITIZEN)
                .firstName("Awa")
                .lastName("Ouedraogo")
                .build();
        return DossierCreateRequest.builder()
                .submissionMode(SubmissionMode.WEB_FORM)
                .object("Marché public suspect")
                .quality(quality)
                .anonymous(anonymous)
                .declarantData(declarantData)
                .build();
    }

    // ───────── nature choisie, région d'origine et typologie : ce que le citoyen peut ou non renseigner ─────────

    private void stubSoumission(DossierCreateRequest request, Declarant declarant, TypeSaisine natureCalculee,
                                TypeSaisine natureChoisie) {
        boolean anonyme = Boolean.TRUE.equals(request.getAnonymous());
        when(declarantMapper.toEntity(request.getDeclarantData())).thenReturn(declarant);
        when(declarantRepository.save(declarant)).thenReturn(declarant);
        if (natureChoisie != null) {
            when(natureSaisineResolver.resolve(TypeDeclarant.CITIZEN, QualiteDeclarant.TEMOIN, anonyme, natureChoisie))
                    .thenReturn(natureChoisie);
        } else {
            when(natureSaisineResolver.resolve(TypeDeclarant.CITIZEN, QualiteDeclarant.TEMOIN, anonyme))
                    .thenReturn(natureCalculee);
        }
        when(dossierMapper.toEntity(request)).thenReturn(Dossier.builder().build());
        when(accessCodeGenerator.generate()).thenReturn("ABCD1234");
        when(dossierRepository.existsByAccessCode("ABCD1234")).thenReturn(false);
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private GeoRegion unRegion(String libelle, RegionOrigine zone) {
        return GeoRegion.builder().id(UUID.randomUUID()).code(libelle.toUpperCase()).libelle(libelle)
                .zonePublication(zone).build();
    }

    @Test
    void submitParAgent_appliqueLaNatureChoisieLaTypologieEtLOrigineGeographique() {
        GeoRegion guiriko = unRegion("Guiriko", RegionOrigine.GUIRIKO);
        DossierCreateRequest request = buildRequest(QualiteDeclarant.TEMOIN);
        request.setNatureSaisineChoisie(TypeSaisine.DEMANDE_INTERVENTION);
        request.setTypologieInstitutionnelle(TypologieInstitutionnelle.SOCIETES_D_ETAT);
        request.setOrigineRegionId(guiriko.getId());
        Declarant declarant = Declarant.builder().typeDeclarant(TypeDeclarant.CITIZEN).build();
        stubSoumission(request, declarant, null, TypeSaisine.DEMANDE_INTERVENTION);
        when(geographieService.resoudreOrigine(null, guiriko.getId(), null, null, false))
                .thenReturn(new OrigineGeographique("Burkina Faso", guiriko, null, null, RegionOrigine.GUIRIKO));

        service.submitParAgent(request, "127.0.0.1");

        verify(dossierRepository).save(argThat(d -> d.getType() == TypeSaisine.DEMANDE_INTERVENTION
                && d.getTypologieInstitutionnelle() == TypologieInstitutionnelle.SOCIETES_D_ETAT
                && d.getRegionOrigine() == RegionOrigine.GUIRIKO
                && "Burkina Faso".equals(d.getOriginePays())
                && d.getOrigineRegion() == guiriko));
    }

    @Test
    void submitCitoyen_ignoreLaNatureEtLaTypologieMaisGardeLOrigineGeographique() {
        GeoRegion kadiogo = unRegion("Kadiogo", RegionOrigine.KADIOGO);
        DossierCreateRequest request = buildRequest(QualiteDeclarant.TEMOIN);
        request.setNatureSaisineChoisie(TypeSaisine.DEMANDE_INTERVENTION);
        request.setTypologieInstitutionnelle(TypologieInstitutionnelle.SOCIETES_D_ETAT);
        request.setOrigineRegionId(kadiogo.getId());
        Declarant declarant = Declarant.builder().typeDeclarant(TypeDeclarant.CITIZEN).build();
        stubSoumission(request, declarant, TypeSaisine.DENONCIATION, null);
        when(geographieService.resoudreOrigine(null, kadiogo.getId(), null, null, false))
                .thenReturn(new OrigineGeographique("Burkina Faso", kadiogo, null, null, RegionOrigine.KADIOGO));

        service.submit(request, "127.0.0.1");

        verify(dossierRepository).save(argThat(d -> d.getType() == TypeSaisine.DENONCIATION
                && d.getTypologieInstitutionnelle() == null
                && d.getRegionOrigine() == RegionOrigine.KADIOGO
                && d.getOrigineRegion() == kadiogo));
    }

    @Test
    void submitAnonyme_demandeUneOrigineRestreinteALaRegionPourNePasIdentifierLeLanceurDAlerte() {
        UUID region = UUID.randomUUID();
        UUID province = UUID.randomUUID();
        UUID commune = UUID.randomUUID();
        DossierCreateRequest request = buildRequest(QualiteDeclarant.TEMOIN, true);
        request.setOrigineRegionId(region);
        request.setOrigineProvinceId(province);
        request.setOrigineCommuneId(commune);
        Declarant declarant = Declarant.builder().typeDeclarant(TypeDeclarant.CITIZEN).build();
        stubSoumission(request, declarant, TypeSaisine.DENONCIATION, null);

        service.submit(request, "127.0.0.1");

        verify(geographieService).resoudreOrigine(null, region, province, commune, true);
    }

    @Test
    void submitAvecUnPaysEtranger_transmetLePaysAuServiceDeGeographie() {
        DossierCreateRequest request = buildRequest(QualiteDeclarant.TEMOIN);
        request.setPaysOrigine("Côte d'Ivoire");
        Declarant declarant = Declarant.builder().typeDeclarant(TypeDeclarant.CITIZEN).build();
        stubSoumission(request, declarant, TypeSaisine.DENONCIATION, null);
        when(geographieService.resoudreOrigine("Côte d'Ivoire", null, null, null, false))
                .thenReturn(new OrigineGeographique("Côte d'Ivoire", null, null, null, RegionOrigine.DIASPORA));

        service.submit(request, "127.0.0.1");

        verify(dossierRepository).save(argThat(d -> d.getRegionOrigine() == RegionOrigine.DIASPORA
                && "Côte d'Ivoire".equals(d.getOriginePays()) && d.getOrigineRegion() == null));
    }

    // ───────── adresse IP du citoyen : jamais conservée quand elle pourrait trahir un lanceur d'alerte ─────────

    private static final String IP_CITOYEN = "203.0.113.9";

    private Declarant declarantCitoyen(boolean protection) {
        return Declarant.builder().typeDeclarant(TypeDeclarant.CITIZEN).protectionRequested(protection).build();
    }

    @Test
    void submitAnonyme_neConservePasLAdresseIpDuCitoyen() {
        DossierCreateRequest request = buildRequest(QualiteDeclarant.TEMOIN, true);
        stubSoumission(request, declarantCitoyen(false), TypeSaisine.DENONCIATION, null);

        service.submit(request, IP_CITOYEN);

        verify(auditRecorder).recordStatusChange(any(Dossier.class), isNull(), eq(DossierStatus.SOUMIS),
                anyString(), isNull(), isNull());
    }

    @Test
    void submitSousProtection_neConservePasLAdresseIpDuCitoyen() {
        DossierCreateRequest request = buildRequest(QualiteDeclarant.TEMOIN);
        stubSoumission(request, declarantCitoyen(true), TypeSaisine.DENONCIATION, null);

        service.submit(request, IP_CITOYEN);

        verify(auditRecorder).recordStatusChange(any(Dossier.class), isNull(), eq(DossierStatus.SOUMIS),
                anyString(), isNull(), isNull());
    }

    @Test
    void submitIdentifieSansProtection_conserveLAdresseIpPourLaTracabilite() {
        DossierCreateRequest request = buildRequest(QualiteDeclarant.TEMOIN);
        stubSoumission(request, declarantCitoyen(false), TypeSaisine.DENONCIATION, null);

        service.submit(request, IP_CITOYEN);

        verify(auditRecorder).recordStatusChange(any(Dossier.class), isNull(), eq(DossierStatus.SOUMIS),
                anyString(), isNull(), eq(IP_CITOYEN));
    }

    @Test
    void submitSousProtection_n_ecritJamaisLAdresseIpDansLesJournauxDuServeur() {
        ch.qos.logback.classic.Logger journal =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(DossierServiceImpl.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> collecteur =
                new ch.qos.logback.core.read.ListAppender<>();
        collecteur.start();
        journal.addAppender(collecteur);
        try {
            DossierCreateRequest request = buildRequest(QualiteDeclarant.TEMOIN);
            stubSoumission(request, declarantCitoyen(true), TypeSaisine.DENONCIATION, null);

            service.submit(request, IP_CITOYEN);

            assertThat(collecteur.list).isNotEmpty();
            assertThat(collecteur.list).noneMatch(e -> e.getFormattedMessage().contains(IP_CITOYEN));
        } finally {
            journal.detachAppender(collecteur);
        }
    }

    @Test
    void submit_appliesResolvedNatureSaisineToNewDossier() {
        DossierCreateRequest request = buildRequest(QualiteDeclarant.TEMOIN);
        Declarant declarant = Declarant.builder()
                .typeDeclarant(TypeDeclarant.CITIZEN)
                .build();

        when(declarantMapper.toEntity(request.getDeclarantData())).thenReturn(declarant);
        when(declarantRepository.save(declarant)).thenReturn(declarant);
        when(natureSaisineResolver.resolve(TypeDeclarant.CITIZEN, QualiteDeclarant.TEMOIN, false))
                .thenReturn(TypeSaisine.DENONCIATION);
        when(dossierMapper.toEntity(request)).thenReturn(Dossier.builder().build());
        when(accessCodeGenerator.generate()).thenReturn("ABCD1234");
        when(dossierRepository.existsByAccessCode("ABCD1234")).thenReturn(false);
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> inv.getArgument(0));

        service.submit(request, "127.0.0.1");

        verify(dossierRepository).save(argThat(d ->
                d.getType() == TypeSaisine.DENONCIATION
                        && d.getDeclarant() == declarant));
    }

    @Test
    void submit_nullsQualityForSignalement() {
        DeclarantCreateRequest declarantData = DeclarantCreateRequest.builder()
                .typeDeclarant(TypeDeclarant.PUBLIC_AUTHORITY)
                .firstName("Awa")
                .lastName("Ouedraogo")
                .build();
        DossierCreateRequest request = DossierCreateRequest.builder()
                .submissionMode(SubmissionMode.WEB_FORM)
                .object("Signalement d'une autorité publique")
                .quality(QualiteDeclarant.VICTIME)
                .declarantData(declarantData)
                .build();
        Declarant declarant = Declarant.builder()
                .typeDeclarant(TypeDeclarant.PUBLIC_AUTHORITY)
                .build();

        when(declarantMapper.toEntity(request.getDeclarantData())).thenReturn(declarant);
        when(declarantRepository.save(declarant)).thenReturn(declarant);
        when(natureSaisineResolver.resolve(
                TypeDeclarant.PUBLIC_AUTHORITY, QualiteDeclarant.VICTIME, false))
                .thenReturn(TypeSaisine.SIGNALEMENT);
        when(dossierMapper.toEntity(request)).thenReturn(
                Dossier.builder().quality(QualiteDeclarant.VICTIME).build());
        when(accessCodeGenerator.generate()).thenReturn("ABCD1234");
        when(dossierRepository.existsByAccessCode("ABCD1234")).thenReturn(false);
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> inv.getArgument(0));

        service.submit(request, "127.0.0.1");

        verify(dossierRepository).save(argThat(d ->
                d.getType() == TypeSaisine.SIGNALEMENT
                        && d.getQuality() == null));
    }

    @Test
    void submit_throwsWhenDeclarantMissing() {
        DossierCreateRequest request = DossierCreateRequest.builder()
                .submissionMode(SubmissionMode.WEB_FORM)
                .object("Objet")
                .build();

        assertThatThrownBy(() -> service.submit(request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);

        verify(dossierRepository, never()).save(any());
    }

    @Test
    void submit_propagatesBusinessExceptionFromResolverWithoutSaving() {
        DossierCreateRequest request = buildRequest(QualiteDeclarant.VICTIME, true);
        Declarant declarant = Declarant.builder()
                .typeDeclarant(TypeDeclarant.ANONYMOUS)
                .build();

        when(declarantMapper.toEntity(request.getDeclarantData())).thenReturn(declarant);
        when(declarantRepository.save(declarant)).thenReturn(declarant);
        when(natureSaisineResolver.resolve(TypeDeclarant.ANONYMOUS, QualiteDeclarant.VICTIME, true))
                .thenThrow(new BusinessException(
                        "La qualité du déposant est obligatoire pour ce type de déclarant."));

        assertThatThrownBy(() -> service.submit(request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);

        verify(dossierRepository, never()).save(any());
    }

    @Test
    void findById_delegatesAccessCheckToGuard() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(dossierMapper.toResponse(dossier)).thenReturn(DossierResponse.builder().build());

        service.findById(dossierId);

        verify(accessGuard).checkReadAccess(dossier);
    }

    private Dossier dossierDeLanceurDAlerteProtege(UUID dossierId) {
        Declarant declarant = Declarant.builder().protectionRequested(true).build();
        return Dossier.builder().id(dossierId).number("DOS-2026-001").declarant(declarant).build();
    }

    @Test
    void findById_tracePersistantLaConsultationDeLIdentiteDUnLanceurDAlerteProtegeParUnAgentHabilite() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = dossierDeLanceurDAlerteProtege(dossierId);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(dossierMapper.toResponse(dossier)).thenReturn(DossierResponse.builder().build());
        when(securityUtils.hasPermission(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER)).thenReturn(true);

        service.findById(dossierId);

        verify(currentUserAuditor).record(eq("CONSULTER_IDENTITE_LANCEUR_ALERTE"), eq("DOSSIER"),
                eq(dossierId.toString()), contains("DOS-2026-001"));
    }

    @Test
    void findById_neTracePasLaConsultationQuandLIdentiteEstMasquee() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = dossierDeLanceurDAlerteProtege(dossierId);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(dossierMapper.toResponse(dossier)).thenReturn(DossierResponse.builder().build());
        when(securityUtils.hasPermission(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER)).thenReturn(false);

        service.findById(dossierId);

        verifyNoInteractions(currentUserAuditor);
    }

    @Test
    void findById_neTracePasLaConsultationSiLAccesAuDossierEstRefuse() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = dossierDeLanceurDAlerteProtege(dossierId);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        // L'agent détiendrait la permission : seul le refus du garde doit empêcher la trace.
        lenient().when(securityUtils.hasPermission(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER)).thenReturn(true);
        doThrow(new BusinessException("Accès refusé")).when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.findById(dossierId)).isInstanceOf(BusinessException.class);

        verifyNoInteractions(currentUserAuditor);
    }

    @Test
    void findById_neTracePasUnDossierSansLanceurDAlerteProtege() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(dossierMapper.toResponse(dossier)).thenReturn(DossierResponse.builder().build());

        service.findById(dossierId);

        verifyNoInteractions(currentUserAuditor);
    }

    @Test
    void findResume_controleLAccesEtNeTracePasLIdentite() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = dossierDeLanceurDAlerteProtege(dossierId);
        dossier.setStatus(DossierStatus.EN_ETUDE_OPPORTUNITE);
        dossier.setReceptionDate(java.time.Instant.parse("2026-03-02T10:00:00Z"));
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        lenient().when(securityUtils.hasPermission(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER)).thenReturn(true);

        DossierResume resume = service.findResume(dossierId);

        assertThat(resume.receptionDate()).isEqualTo(java.time.Instant.parse("2026-03-02T10:00:00Z"));
        assertThat(resume.status()).isEqualTo(DossierStatus.EN_ETUDE_OPPORTUNITE);
        verify(accessGuard).checkReadAccess(dossier);
        verifyNoInteractions(currentUserAuditor);
    }

    @Test
    void findResume_propageLeRefusDuGarde() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        doThrow(new BusinessException("Accès refusé")).when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.findResume(dossierId)).isInstanceOf(BusinessException.class);
    }

    @Test
    void findById_propagatesGuardRejection() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        doThrow(new BusinessException("Accès refusé"))
                .when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.findById(dossierId))
                .isInstanceOf(BusinessException.class);
    }

    // ───────── listes de dossiers : l'identité en clair d'un lanceur d'alerte protégé est tracée ─────────

    private DossierResponse reponseAvecLanceurDAlerte(String numero, String prenom, String nom, String organisation) {
        return DossierResponse.builder()
                .number(numero)
                .declarant(DeclarantResponse.builder()
                        .protectionRequested(true).firstName(prenom).lastName(nom)
                        .organizationName(organisation).build())
                .build();
    }

    private void listeDeDeuxDossiersProteges() {
        Dossier d1 = Dossier.builder().id(UUID.randomUUID()).number("DOS-1").build();
        Dossier d2 = Dossier.builder().id(UUID.randomUUID()).number("DOS-2").build();
        when(accessGuard.canSeeConfidential()).thenReturn(true);
        when(dossierRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(d1, d2)));
        when(dossierMapper.toResponse(d1)).thenReturn(reponseAvecLanceurDAlerte("DOS-1", "Awa", "Kaboré", null));
        when(dossierMapper.toResponse(d2)).thenReturn(reponseAvecLanceurDAlerte("DOS-2", "Issa", "Zongo", null));
    }

    @Test
    void findAll_traceUneSeuleFoisLIdentiteDesLanceursDAlerteProtegesDeLaListe() {
        listeDeDeuxDossiersProteges();
        when(securityUtils.hasPermission(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER)).thenReturn(true);

        service.findAll(PageRequest.of(0, 20));

        verify(currentUserAuditor, times(1)).record(eq("CONSULTER_IDENTITE_LANCEUR_ALERTE"), eq("DOSSIER"),
                isNull(), contains("2 lanceur"));
    }

    @Test
    void findAll_neTracePasQuandLIdentiteEstMasquee() {
        listeDeDeuxDossiersProteges();
        when(securityUtils.hasPermission(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER)).thenReturn(false);

        service.findAll(PageRequest.of(0, 20));

        verifyNoInteractions(currentUserAuditor);
    }

    @Test
    void findAll_neTracePasUneListeSansLanceurDAlerteProtege() {
        Dossier d1 = Dossier.builder().id(UUID.randomUUID()).number("DOS-1").build();
        when(accessGuard.canSeeConfidential()).thenReturn(true);
        when(dossierRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(d1)));
        when(dossierMapper.toResponse(d1)).thenReturn(DossierResponse.builder().number("DOS-1")
                .declarant(DeclarantResponse.builder().firstName("Awa").lastName("Kaboré").build()).build());

        service.findAll(PageRequest.of(0, 20));

        verifyNoInteractions(currentUserAuditor);
    }

    @Test
    void lesAutresListesTracentAussiLIdentiteDesLanceursDAlerteProteges() {
        Dossier d1 = Dossier.builder().id(UUID.randomUUID()).number("DOS-1").build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        when(dossierRepository.findByStatus(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(d1)));
        when(dossierRepository.findByAgentInChargeId(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(d1)));
        when(dossierRepository.findByReceptionDateBetween(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(d1)));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(accessGuard.canSeeConfidential()).thenReturn(true);
        when(dossierMapper.toResponse(d1)).thenAnswer(inv -> reponseAvecLanceurDAlerte("DOS-1", "Awa", "Kaboré", null));
        when(securityUtils.hasPermission(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER)).thenReturn(true);

        service.findByStatus(DossierStatus.SOUMIS, PageRequest.of(0, 20));
        service.findMyDossiers(PageRequest.of(0, 20));
        service.findByReceptionDateBetween(java.time.Instant.now().minusSeconds(60), java.time.Instant.now(),
                PageRequest.of(0, 20));

        verify(currentUserAuditor, times(3)).record(eq("CONSULTER_IDENTITE_LANCEUR_ALERTE"), eq("DOSSIER"),
                isNull(), contains("1 lanceur"));
    }

    @Test
    void leNomDUneOrganisationLanceuseDAlerteEstMasqueEgalementPourUnAgentNonHabilite() {
        Dossier d1 = Dossier.builder().id(UUID.randomUUID()).number("DOS-1").build();
        when(accessGuard.canSeeConfidential()).thenReturn(true);
        when(dossierRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(d1)));
        when(dossierMapper.toResponse(d1)).thenReturn(reponseAvecLanceurDAlerte("DOS-1", null, null, "Association Burkin Intègre"));
        when(securityUtils.hasPermission(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER)).thenReturn(false);

        DossierResponse reponse = service.findAll(PageRequest.of(0, 20)).getContent().get(0);

        assertThat(reponse.getDeclarant().getOrganizationName()).isNull();
        assertThat(reponse.getDeclarant().getDisplayName()).isEqualTo("Lanceur d'alerte protégé");
    }

    @Test
    void leNomDUneOrganisationDansUnDossierAnonymeEstMasqueAussi() {
        Dossier d1 = Dossier.builder().id(UUID.randomUUID()).number("DOS-1").build();
        when(accessGuard.canSeeConfidential()).thenReturn(true);
        when(dossierRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(d1)));
        DossierResponse anonyme = reponseAvecLanceurDAlerte("DOS-1", null, null, "Association Burkin Intègre");
        anonyme.setAnonymous(true);
        when(dossierMapper.toResponse(d1)).thenReturn(anonyme);
        lenient().when(securityUtils.hasPermission(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER)).thenReturn(true);

        DossierResponse reponse = service.findAll(PageRequest.of(0, 20)).getContent().get(0);

        assertThat(reponse.getDeclarant().getOrganizationName()).isNull();
    }

    @Test
    void findAll_usesAccessibleDossiersForNonPrivilegedAgent() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        Pageable pageable = PageRequest.of(0, 20);

        when(accessGuard.canSeeConfidential()).thenReturn(false);
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(dossierRepository.findAccessibleByAgentId(agent.getId(), pageable))
                .thenReturn(new PageImpl<>(List.of()));

        service.findAll(pageable);

        verify(dossierRepository).findAccessibleByAgentId(agent.getId(), pageable);
        verify(dossierRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void findAll_brpdAgentAlsoSeesSubmittedDeposits() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        Pageable pageable = PageRequest.of(0, 20);

        when(accessGuard.canSeeConfidential()).thenReturn(false);
        when(securityUtils.hasPermission(AppPermission.DOSSIER_ENREGISTRER)).thenReturn(true);
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(dossierRepository.findAccessibleByAgentIdOrWithStatus(agent.getId(), DossierStatus.SOUMIS, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        service.findAll(pageable);

        verify(dossierRepository).findAccessibleByAgentIdOrWithStatus(agent.getId(), DossierStatus.SOUMIS, pageable);
        verify(dossierRepository, never()).findAccessibleByAgentId(any(), any());
    }

    @Test
    void findAll_usesFindAllForPrivilegedAgent() {
        Pageable pageable = PageRequest.of(0, 20);

        when(accessGuard.canSeeConfidential()).thenReturn(true);
        when(dossierRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

        service.findAll(pageable);

        verify(dossierRepository).findAll(pageable);
        verify(dossierRepository, never()).findAccessibleByAgentId(any(), any());
    }

    @Test
    void findByReceptionDateBetween_usesAccessibleDossiersForNonPrivilegedAgent() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();
        Pageable pageable = PageRequest.of(0, 20);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end   = Instant.parse("2026-01-31T00:00:00Z");

        when(accessGuard.canSeeConfidential()).thenReturn(false);
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(dossierRepository.findAccessibleByAgentIdAndReceptionDateBetween(
                agent.getId(), start, end, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        service.findByReceptionDateBetween(start, end, pageable);

        verify(dossierRepository).findAccessibleByAgentIdAndReceptionDateBetween(
                agent.getId(), start, end, pageable);
        verify(dossierRepository, never())
                .findByReceptionDateBetween(any(), any(), any(Pageable.class));
    }

    @Test
    void findByReceptionDateBetween_usesFindByReceptionDateBetweenForPrivilegedAgent() {
        Pageable pageable = PageRequest.of(0, 20);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end   = Instant.parse("2026-01-31T00:00:00Z");

        when(accessGuard.canSeeConfidential()).thenReturn(true);
        when(dossierRepository.findByReceptionDateBetween(start, end, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        service.findByReceptionDateBetween(start, end, pageable);

        verify(dossierRepository).findByReceptionDateBetween(start, end, pageable);
        verify(dossierRepository, never()).findAccessibleByAgentIdAndReceptionDateBetween(
                any(), any(), any(), any());
    }

    /**
     * Preuve de bout en bout (spec Tests) : un agent dont la SEULE
     * habilitation provient de l'équipe d'investigation (INVESTIGATION_TEAM,
     * pas AGENT_IN_CHARGE) doit pouvoir consulter le dossier via findById.
     * Contrairement aux autres tests de cette classe, le garde d'accès n'est
     * PAS mocké ici : on instancie un DossierAccessGuard réel pour prouver
     * que la chaîne findById → guard → repository fonctionne réellement,
     * et pas seulement que findById délègue à un mock.
     */
    @Test
    void findById_succeedsForAgentWhoseOnlyHabilitationIsInvestigationTeam() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).keycloakId("kc-investigator").build();

        AgentRepository realAgentRepository =
                mock(AgentRepository.class);
        DossierHabilitationRepository realHabilitationRepository =
                mock(DossierHabilitationRepository.class);

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(dossierMapper.toResponse(dossier)).thenReturn(DossierResponse.builder().build());
        when(securityUtils.hasPermission(AppPermission.DOSSIER_VOIR_TOUS)).thenReturn(false);
        when(securityUtils.getCurrentKeycloakId()).thenReturn(Optional.of("kc-investigator"));
        when(realAgentRepository.findByKeycloakId("kc-investigator")).thenReturn(Optional.of(agent));
        // Ne dépend pas de la source (AGENT_IN_CHARGE vs INVESTIGATION_TEAM) —
        // seule compte l'existence d'une ligne active, ce qui est réaliste :
        // simule ici un agent habilité UNIQUEMENT via INVESTIGATION_TEAM.
        when(realHabilitationRepository.existsByDossierIdAndAgentIdAndRevokedAtIsNull(
                dossierId, agent.getId())).thenReturn(true);

        DossierAccessGuard realGuard = new DossierAccessGuard(
                dossierRepository, realAgentRepository, securityUtils, realHabilitationRepository);

        DossierServiceImpl serviceWithRealGuard = new DossierServiceImpl(
                dossierRepository, declarantRepository, notificationRepository, observationRepository,
                dossierMapper, dossierDetailsMapper, declarantMapper, accessCodeGenerator, securityUtils,
                notificationDispatcher, agentContextResolver, auditRecorder, parametreDelaiService,
                natureSaisineResolver, geographieService, realGuard, habilitationService, portalConfigService,
                etudeOpportuniteRepository, decisionCGERepository, deadlineCalculator, currentUserAuditor);

        assertThatCode(() -> serviceWithRealGuard.findById(dossierId))
                .doesNotThrowAnyException();
    }

    @Test
    void registerReception_grantsAgentInChargeHabilitation() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).matricule("M001").build();
        StatusTransitionRequest request = StatusTransitionRequest.builder().build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(parametreDelaiService.resolveDelaiJours("ACCUSE_RECEPTION")).thenReturn(5);
        when(parametreDelaiService.resolveDelaiJours("DEMANDE_COMPLEMENT")).thenReturn(10);
        when(dossierRepository.countByReceptionDateBetween(any(), any())).thenReturn(0L);
        when(accessCodeGenerator.generateDossierNumber(anyInt(), anyInt())).thenReturn("2026-0001");
        when(dossierRepository.existsByNumber(anyString())).thenReturn(false);
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dossierMapper.toResponse(any(Dossier.class))).thenReturn(DossierResponse.builder().build());

        service.registerReception(dossierId, request, "127.0.0.1");

        verify(habilitationService).grant(dossier, agent, HabilitationSource.AGENT_IN_CHARGE,
                agent, "Agent en charge du dossier (enregistrement BRPD)");
    }

    @Test
    void registerReception_usesBusinessDaysWhenParametreDelaiFlagsIt() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();
        Agent agent = Agent.builder().id(UUID.randomUUID()).matricule("M001").build();
        StatusTransitionRequest request = StatusTransitionRequest.builder().build();
        Instant acknowledgment = Instant.parse("2026-01-10T00:00:00Z");
        Instant additionalInfo = Instant.parse("2026-01-20T00:00:00Z");

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(parametreDelaiService.resolveDelaiJours("ACCUSE_RECEPTION")).thenReturn(5);
        when(parametreDelaiService.resolveDelaiJours("DEMANDE_COMPLEMENT")).thenReturn(10);
        when(parametreDelaiService.resolveJoursOuvrables("ACCUSE_RECEPTION")).thenReturn(true);
        when(parametreDelaiService.resolveJoursOuvrables("DEMANDE_COMPLEMENT")).thenReturn(true);
        doReturn(acknowledgment).when(deadlineCalculator).addBusinessDays(any(), eq(5));
        doReturn(additionalInfo).when(deadlineCalculator).addBusinessDays(any(), eq(10));
        when(dossierRepository.countByReceptionDateBetween(any(), any())).thenReturn(0L);
        when(accessCodeGenerator.generateDossierNumber(anyInt(), anyInt())).thenReturn("2026-0001");
        when(dossierRepository.existsByNumber(anyString())).thenReturn(false);
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dossierMapper.toResponse(any(Dossier.class))).thenReturn(DossierResponse.builder().build());

        service.registerReception(dossierId, request, "127.0.0.1");

        assertThat(dossier.getAcknowledgmentDeadline()).isEqualTo(acknowledgment);
        assertThat(dossier.getAdditionalInfoDeadline()).isEqualTo(additionalInfo);
        verify(deadlineCalculator, never()).addCalendarDays(any(), anyInt());
    }

    @Test
    void submit_derivesAnonymityFromCurrentRequestAcrossReusedDeclarant() {
        // Le meme Declarant (meme instance = meme ligne reutilisee par
        // resolveDeclarant) est soumis deux fois avec des demandes d'anonymat
        // opposees. Avant ce correctif, natureSaisineResolver recevait
        // declarant.isAnonymous() — un etat de l'entite qui ne change pas entre
        // les deux appels ici — au lieu de l'anonymat de CHAQUE soumission.
        Declarant reusedDeclarant = Declarant.builder()
                .typeDeclarant(TypeDeclarant.CITIZEN)
                .build();
        UUID declarantId = UUID.randomUUID();

        DossierCreateRequest anonymousRequest = DossierCreateRequest.builder()
                .submissionMode(SubmissionMode.WEB_FORM)
                .object("Premier signalement")
                .quality(QualiteDeclarant.TEMOIN)
                .anonymous(true)
                .declarantId(declarantId)
                .build();
        when(declarantRepository.findById(declarantId)).thenReturn(Optional.of(reusedDeclarant));
        when(natureSaisineResolver.resolve(TypeDeclarant.CITIZEN, QualiteDeclarant.TEMOIN, true))
                .thenReturn(TypeSaisine.DENONCIATION);
        when(dossierMapper.toEntity(anonymousRequest))
                .thenReturn(Dossier.builder().anonymous(true).build());
        when(accessCodeGenerator.generate()).thenReturn("ABCD1234");
        when(dossierRepository.existsByAccessCode("ABCD1234")).thenReturn(false);
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> inv.getArgument(0));

        service.submit(anonymousRequest, "127.0.0.1");

        verify(natureSaisineResolver).resolve(TypeDeclarant.CITIZEN, QualiteDeclarant.TEMOIN, true);

        DossierCreateRequest namedRequest = DossierCreateRequest.builder()
                .submissionMode(SubmissionMode.WEB_FORM)
                .object("Deuxième plainte, identité révélée")
                .quality(QualiteDeclarant.VICTIME)
                .anonymous(false)
                .declarantId(declarantId)
                .build();
        when(declarantRepository.findById(declarantId)).thenReturn(Optional.of(reusedDeclarant));
        when(natureSaisineResolver.resolve(TypeDeclarant.CITIZEN, QualiteDeclarant.VICTIME, false))
                .thenReturn(TypeSaisine.PLAINTE);
        when(dossierMapper.toEntity(namedRequest))
                .thenReturn(Dossier.builder().anonymous(false).build());

        service.submit(namedRequest, "127.0.0.1");

        verify(natureSaisineResolver).resolve(TypeDeclarant.CITIZEN, QualiteDeclarant.VICTIME, false);
    }

    @Test
    void findById_masksIdentityWhenDossierAnonymous() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();
        DeclarantResponse declarantResponse = DeclarantResponse.builder()
                .firstName("Awa")
                .lastName("Ouedraogo")
                .email("awa@example.com")
                .phoneNumber("70000000")
                .build();
        DossierResponse response = DossierResponse.builder()
                .anonymous(true)
                .declarant(declarantResponse)
                .build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(dossierMapper.toResponse(dossier)).thenReturn(response);

        DossierResponse result = service.findById(dossierId);

        assertThat(result.getDeclarant().getFirstName()).isNull();
        assertThat(result.getDeclarant().getLastName()).isNull();
        assertThat(result.getDeclarant().getEmail()).isNull();
        assertThat(result.getDeclarant().getPhoneNumber()).isNull();
        assertThat(result.getDeclarant().getDisplayName()).isEqualTo("Déclarant anonyme");
    }

    @Test
    void update_succeedsWhenAgentIsHabilitated() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();
        DossierUpdateRequest request = DossierUpdateRequest.builder()
                .object("Objet corrigé")
                .build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dossierMapper.toResponse(any(Dossier.class))).thenReturn(DossierResponse.builder().build());
        when(securityUtils.hasPermission(any(AppPermission.class))).thenReturn(false);

        assertThatCode(() -> service.update(dossierId, request)).doesNotThrowAnyException();

        verify(accessGuard).checkReadAccess(dossier);
        verify(dossierRepository).save(dossier);
    }

    @Test
    void update_tracePersistantLIdentiteRenvoyeeEnClairAuLanceurDAlerteProtege() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = dossierDeLanceurDAlerteProtege(dossierId);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dossierMapper.toResponse(any(Dossier.class))).thenReturn(DossierResponse.builder().build());
        when(securityUtils.hasPermission(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER)).thenReturn(true);

        service.update(dossierId, DossierUpdateRequest.builder().object("Objet corrigé").build());

        verify(currentUserAuditor, times(1)).record(eq("CONSULTER_IDENTITE_LANCEUR_ALERTE"), eq("DOSSIER"),
                eq(dossierId.toString()), contains("DOS-2026-001"));
    }

    @Test
    void findById_neTraceQuUneFoisParOuverture() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = dossierDeLanceurDAlerteProtege(dossierId);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(dossierMapper.toResponse(dossier)).thenReturn(DossierResponse.builder().build());
        when(securityUtils.hasPermission(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER)).thenReturn(true);

        service.findById(dossierId);

        verify(currentUserAuditor, times(1)).record(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void update_propagatesGuardRejectionWithoutSaving() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).build();
        DossierUpdateRequest request = DossierUpdateRequest.builder()
                .object("Tentative de modification")
                .build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        doThrow(new BusinessException("Accès refusé — ce dossier ne vous est pas assigné"))
                .when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.update(dossierId, request))
                .isInstanceOf(BusinessException.class);

        verify(dossierRepository, never()).save(any());
    }

    @Test
    void submitToCtadp_rejectsWhenNoEtudeOpportuniteExists() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.EN_ETUDE_OPPORTUNITE).build();
        StatusTransitionRequest request = StatusTransitionRequest.builder().build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(etudeOpportuniteRepository.existsByDossierId(dossierId)).thenReturn(false);

        assertThatThrownBy(() -> service.submitToCtadp(dossierId, request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);

        verify(dossierRepository, never()).save(any());
    }

    @Test
    void submitToCtadp_succeedsWhenEtudeOpportuniteExists() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.EN_ETUDE_OPPORTUNITE).build();
        StatusTransitionRequest request = StatusTransitionRequest.builder().build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(etudeOpportuniteRepository.existsByDossierId(dossierId)).thenReturn(true);
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dossierMapper.toResponse(any(Dossier.class))).thenReturn(DossierResponse.builder().build());

        service.submitToCtadp(dossierId, request, "127.0.0.1");

        verify(dossierRepository).save(argThat(d -> d.getStatus() == DossierStatus.EN_REVUE_CTADP));
    }

    @Test
    void findByAccessCode_neverExposesEtudeOpportunite() {
        String accessCode = "ABCD1234";
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).accessCode(accessCode).build();
        DossierResponse response = DossierResponse.builder()
                .etudeOpportunite(EtudeOpportuniteResponse.builder()
                        .avisGeneral("Analyse interne confidentielle")
                        .build())
                .build();

        when(dossierRepository.findByAccessCode(accessCode)).thenReturn(Optional.of(dossier));
        when(dossierMapper.toResponse(dossier)).thenReturn(response);

        DossierResponse result = service.findByAccessCode(accessCode);

        assertThat(result.getEtudeOpportunite()).isNull();
    }

    @Test
    void orientAdministratif_succeedsWithReason() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.EN_REVUE_CTADP).build();
        StatusTransitionRequest request = StatusTransitionRequest.builder()
                .reason("Irrégularité administrative, hors compétence pénale").build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(decisionCGERepository.findByDossierId(dossierId)).thenReturn(Optional.empty());
        when(decisionCGERepository.save(any(DecisionCGE.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dossierMapper.toResponse(any(Dossier.class))).thenReturn(DossierResponse.builder().build());

        service.orientAdministratif(dossierId, request, "127.0.0.1");

        verify(dossierRepository).save(argThat(d ->
                d.getStatus() == DossierStatus.ORIENTEE_ADMINISTRATIF));
        verify(decisionCGERepository).save(argThat(dc ->
                dc.getDecision() == RecommandationCtadp.ORIENTATION_ADMINISTRATIVE
                        && dc.getDossier() == dossier));
    }

    @Test
    void orientAdministratif_rejectsWhenReasonMissing() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.EN_REVUE_CTADP).build();
        StatusTransitionRequest request = StatusTransitionRequest.builder().build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));

        assertThatThrownBy(() -> service.orientAdministratif(dossierId, request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);

        verify(dossierRepository, never()).save(any());
    }

    @Test
    void orientAdministratif_rejectsWhenDossierNotInCorrectStatus() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.RECU).build();
        StatusTransitionRequest request = StatusTransitionRequest.builder()
                .reason("Motif").build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));

        assertThatThrownBy(() -> service.orientAdministratif(dossierId, request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);

        verify(dossierRepository, never()).save(any());
    }

    @Test
    void declareAdmissible_recordsDecisionCGEWithValidationInvestigation() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.EN_REVUE_CTADP).build();
        StatusTransitionRequest request = StatusTransitionRequest.builder()
                .reason("Preuves suffisantes").build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(decisionCGERepository.findByDossierId(dossierId)).thenReturn(Optional.empty());
        when(decisionCGERepository.save(any(DecisionCGE.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dossierMapper.toResponse(any(Dossier.class))).thenReturn(DossierResponse.builder().build());

        service.declareAdmissible(dossierId, request, "127.0.0.1");

        verify(decisionCGERepository).save(argThat(dc ->
                dc.getDecision() == RecommandationCtadp.VALIDATION_INVESTIGATION
                        && dc.getDossier() == dossier));
    }

    @Test
    void declareInadmissible_recordsDecisionCGEWithClassement() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.EN_REVUE_CTADP).build();
        StatusTransitionRequest request = StatusTransitionRequest.builder()
                .reason("Faits non constitutifs d'infraction").build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(decisionCGERepository.findByDossierId(dossierId)).thenReturn(Optional.empty());
        when(decisionCGERepository.save(any(DecisionCGE.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dossierMapper.toResponse(any(Dossier.class))).thenReturn(DossierResponse.builder().build());

        service.declareInadmissible(dossierId, request, "127.0.0.1");

        verify(decisionCGERepository).save(argThat(dc ->
                dc.getDecision() == RecommandationCtadp.CLASSEMENT
                        && dc.getDossier() == dossier));
    }

    @Test
    void transfer_recordsDecisionCGEWithTransmissionInstitutionPartenaire() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId)
                .status(DossierStatus.EN_REVUE_CTADP).build();
        StatusTransitionRequest request = StatusTransitionRequest.builder()
                .reason("Compétence d'une autre institution")
                .transferInstitution("Autorité de régulation de la commande publique")
                .build();

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(decisionCGERepository.findByDossierId(dossierId)).thenReturn(Optional.empty());
        when(decisionCGERepository.save(any(DecisionCGE.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dossierMapper.toResponse(any(Dossier.class))).thenReturn(DossierResponse.builder().build());

        service.transfer(dossierId, request, "127.0.0.1");

        verify(decisionCGERepository).save(argThat(dc ->
                dc.getDecision() == RecommandationCtadp.TRANSMISSION_INSTITUTION_PARTENAIRE
                        && dc.getDossier() == dossier));
    }
}
