package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.QualiteDeclarant;
import gov.bf.ascelc.univers_audits.enums.SubmissionMode;
import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import gov.bf.ascelc.univers_audits.enums.TypeSaisine;
import gov.bf.ascelc.univers_audits.mapper.DeclarantMapper;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.mapper.DossierMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.DeclarantCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.DossierCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Declarant;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.repository.DeclarantRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.repository.ObservationRepository;
import gov.bf.ascelc.univers_audits.service.DossierHabilitationService;
import gov.bf.ascelc.univers_audits.service.NotificationDispatcherService;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.utils.AccessCodeGenerator;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAuditRecorder;
import gov.bf.ascelc.univers_audits.shared.utils.NatureSaisineResolver;
import gov.bf.ascelc.univers_audits.shared.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    @Mock private DossierAccessGuard accessGuard;
    @Mock private DossierHabilitationService habilitationService;

    @InjectMocks
    private DossierServiceImpl service;

    private DossierCreateRequest buildRequest(QualiteDeclarant quality) {
        DeclarantCreateRequest declarantData = DeclarantCreateRequest.builder()
                .typeDeclarant(TypeDeclarant.CITIZEN)
                .anonymous(false)
                .firstName("Awa")
                .lastName("Ouedraogo")
                .build();
        return DossierCreateRequest.builder()
                .submissionMode(SubmissionMode.WEB_FORM)
                .object("Marché public suspect")
                .quality(quality)
                .declarantData(declarantData)
                .build();
    }

    @Test
    void submit_appliesResolvedNatureSaisineToNewDossier() {
        DossierCreateRequest request = buildRequest(QualiteDeclarant.TEMOIN);
        Declarant declarant = Declarant.builder()
                .typeDeclarant(TypeDeclarant.CITIZEN)
                .anonymous(false)
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
                .anonymous(false)
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
                .anonymous(false)
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
        DossierCreateRequest request = buildRequest(QualiteDeclarant.VICTIME);
        Declarant declarant = Declarant.builder()
                .typeDeclarant(TypeDeclarant.ANONYMOUS)
                .anonymous(true)
                .build();

        when(declarantMapper.toEntity(request.getDeclarantData())).thenReturn(declarant);
        when(declarantRepository.save(declarant)).thenReturn(declarant);
        when(natureSaisineResolver.resolve(TypeDeclarant.ANONYMOUS, QualiteDeclarant.VICTIME, true))
                .thenThrow(new BusinessException(
                        "Un déclarant anonyme ne peut être enregistré qu'en tant que témoin."));

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
    void findAll_usesFindAllForPrivilegedAgent() {
        Pageable pageable = PageRequest.of(0, 20);

        when(accessGuard.canSeeConfidential()).thenReturn(true);
        when(dossierRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

        service.findAll(pageable);

        verify(dossierRepository).findAll(pageable);
        verify(dossierRepository, never()).findAccessibleByAgentId(any(), any());
    }
}
