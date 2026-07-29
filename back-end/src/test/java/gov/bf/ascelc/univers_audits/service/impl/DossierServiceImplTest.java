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
import gov.bf.ascelc.univers_audits.model.entity.Declarant;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.repository.DeclarantRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.repository.ObservationRepository;
import gov.bf.ascelc.univers_audits.service.NotificationDispatcherService;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.utils.AccessCodeGenerator;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAuditRecorder;
import gov.bf.ascelc.univers_audits.shared.utils.NatureSaisineResolver;
import gov.bf.ascelc.univers_audits.shared.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
