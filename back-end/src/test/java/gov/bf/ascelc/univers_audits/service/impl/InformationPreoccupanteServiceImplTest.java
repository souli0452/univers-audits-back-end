package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.AutoReferralSource;
import gov.bf.ascelc.univers_audits.enums.StatutInformationPreoccupante;
import gov.bf.ascelc.univers_audits.enums.SubmissionMode;
import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import gov.bf.ascelc.univers_audits.model.dto.request.DossierCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.InformationPreoccupanteCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RattacherDossierRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.InformationPreoccupanteResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.InformationPreoccupante;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.InformationPreoccupanteDossierRepository;
import gov.bf.ascelc.univers_audits.repository.InformationPreoccupanteRepository;
import gov.bf.ascelc.univers_audits.service.DossierService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InformationPreoccupanteServiceImplTest {

    @Mock private InformationPreoccupanteRepository informationPreoccupanteRepository;
    @Mock private InformationPreoccupanteDossierRepository informationPreoccupanteDossierRepository;
    @Mock private DossierRepository dossierRepository;
    @Mock private DossierService dossierService;
    @Mock private DossierAccessGuard dossierAccessGuard;

    @InjectMocks
    private InformationPreoccupanteServiceImpl service;

    private InformationPreoccupante buildInfo(StatutInformationPreoccupante statut) {
        return InformationPreoccupante.builder()
                .id(UUID.randomUUID())
                .objet("Signalement presse")
                .description("Article évoquant des irrégularités")
                .source(AutoReferralSource.WRITTEN_PRESS)
                .dateReception(Instant.now())
                .statut(statut)
                .build();
    }

    @Test
    void create_creeUneInformationPreoccupanteAvecStatutNouvelle() {
        InformationPreoccupanteCreateRequest request = InformationPreoccupanteCreateRequest.builder()
                .objet("Signalement presse")
                .description("Article évoquant des irrégularités")
                .source(AutoReferralSource.WRITTEN_PRESS)
                .dateReception(Instant.now())
                .build();

        when(informationPreoccupanteRepository.save(any(InformationPreoccupante.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InformationPreoccupanteResponse result = service.create(request);

        assertThat(result.getStatut()).isEqualTo(StatutInformationPreoccupante.NOUVELLE);
        assertThat(result.getObjet()).isEqualTo("Signalement presse");
    }

    @Test
    void rattacherDossier_passeLeStatutARattacheeSiNouvelle() {
        InformationPreoccupante info = buildInfo(StatutInformationPreoccupante.NOUVELLE);
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).number("ASCE-2026-000001").build();

        when(informationPreoccupanteRepository.findById(info.getId())).thenReturn(Optional.of(info));
        when(dossierAccessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);
        when(informationPreoccupanteDossierRepository
                .existsByInformationPreoccupanteIdAndDossierId(info.getId(), dossierId))
                .thenReturn(false);
        when(informationPreoccupanteDossierRepository.findByInformationPreoccupanteId(info.getId()))
                .thenReturn(List.of());
        when(informationPreoccupanteRepository.save(any(InformationPreoccupante.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InformationPreoccupanteResponse result = service.rattacherDossier(
                info.getId(), dossierId, RattacherDossierRequest.builder().commentaire("lien").build());

        assertThat(result.getStatut()).isEqualTo(StatutInformationPreoccupante.RATTACHEE);
    }

    @Test
    void rattacherDossier_neRetrogradePasUnStatutAutoSaisineDeclenchee() {
        InformationPreoccupante info = buildInfo(StatutInformationPreoccupante.AUTO_SAISINE_DECLENCHEE);
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).number("ASCE-2026-000002").build();

        when(informationPreoccupanteRepository.findById(info.getId())).thenReturn(Optional.of(info));
        when(dossierAccessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);
        when(informationPreoccupanteDossierRepository
                .existsByInformationPreoccupanteIdAndDossierId(info.getId(), dossierId))
                .thenReturn(false);
        when(informationPreoccupanteDossierRepository.findByInformationPreoccupanteId(info.getId()))
                .thenReturn(List.of());

        InformationPreoccupanteResponse result = service.rattacherDossier(info.getId(), dossierId, null);

        assertThat(result.getStatut()).isEqualTo(StatutInformationPreoccupante.AUTO_SAISINE_DECLENCHEE);
        verify(informationPreoccupanteRepository, never()).save(any(InformationPreoccupante.class));
    }

    @Test
    void rattacherDossier_refuseSiClasseeSansSuite() {
        InformationPreoccupante info = buildInfo(StatutInformationPreoccupante.CLASSEE_SANS_SUITE);
        UUID dossierId = UUID.randomUUID();

        when(informationPreoccupanteRepository.findById(info.getId())).thenReturn(Optional.of(info));

        assertThatThrownBy(() -> service.rattacherDossier(info.getId(), dossierId, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rattacherDossier_refuseSiDejaRattacheAuMemeDossier() {
        InformationPreoccupante info = buildInfo(StatutInformationPreoccupante.NOUVELLE);
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).number("ASCE-2026-000003").build();

        when(informationPreoccupanteRepository.findById(info.getId())).thenReturn(Optional.of(info));
        when(dossierAccessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);
        when(informationPreoccupanteDossierRepository
                .existsByInformationPreoccupanteIdAndDossierId(info.getId(), dossierId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.rattacherDossier(info.getId(), dossierId, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void declencherAutoSaisine_appelleDossierServiceSubmitAvecLesBonsChamps() {
        InformationPreoccupante info = buildInfo(StatutInformationPreoccupante.NOUVELLE);
        UUID dossierId = UUID.randomUUID();
        DossierResponse created = DossierResponse.builder().id(dossierId).number("ASCE-2026-000004").build();
        Dossier dossierRef = Dossier.builder().id(dossierId).number("ASCE-2026-000004").build();

        when(informationPreoccupanteRepository.findById(info.getId())).thenReturn(Optional.of(info));
        when(dossierService.submit(any(DossierCreateRequest.class), anyString())).thenReturn(created);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossierRef));
        when(informationPreoccupanteRepository.save(any(InformationPreoccupante.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.declencherAutoSaisine(info.getId(), "127.0.0.1");

        ArgumentCaptor<DossierCreateRequest> captor = ArgumentCaptor.forClass(DossierCreateRequest.class);
        verify(dossierService).submit(captor.capture(), eq("127.0.0.1"));
        DossierCreateRequest sent = captor.getValue();
        assertThat(sent.getSubmissionMode()).isEqualTo(SubmissionMode.AUDIT_REPORT);
        assertThat(sent.getAutoReferralSource()).isEqualTo(AutoReferralSource.WRITTEN_PRESS);
        assertThat(sent.getObject()).isEqualTo("Signalement presse");
        assertThat(sent.getDeclarantData().getTypeDeclarant()).isEqualTo(TypeDeclarant.ASCE_SELF_REFERRAL);
    }

    @Test
    void declencherAutoSaisine_passeLeStatutAAutoSaisineDeclenchee() {
        InformationPreoccupante info = buildInfo(StatutInformationPreoccupante.NOUVELLE);
        UUID dossierId = UUID.randomUUID();
        DossierResponse created = DossierResponse.builder().id(dossierId).number("ASCE-2026-000005").build();
        Dossier dossierRef = Dossier.builder().id(dossierId).number("ASCE-2026-000005").build();

        when(informationPreoccupanteRepository.findById(info.getId())).thenReturn(Optional.of(info));
        when(dossierService.submit(any(DossierCreateRequest.class), anyString())).thenReturn(created);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossierRef));
        when(informationPreoccupanteRepository.save(any(InformationPreoccupante.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.declencherAutoSaisine(info.getId(), "127.0.0.1");

        assertThat(info.getStatut()).isEqualTo(StatutInformationPreoccupante.AUTO_SAISINE_DECLENCHEE);
        verify(informationPreoccupanteDossierRepository, times(1)).save(any());
    }

    @Test
    void declencherAutoSaisine_refuseSiDejaDeclenchee() {
        InformationPreoccupante info = buildInfo(StatutInformationPreoccupante.AUTO_SAISINE_DECLENCHEE);

        when(informationPreoccupanteRepository.findById(info.getId())).thenReturn(Optional.of(info));

        assertThatThrownBy(() -> service.declencherAutoSaisine(info.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(dossierService, never()).submit(any(), anyString());
    }

    @Test
    void declencherAutoSaisine_refuseSiClasseeSansSuite() {
        InformationPreoccupante info = buildInfo(StatutInformationPreoccupante.CLASSEE_SANS_SUITE);

        when(informationPreoccupanteRepository.findById(info.getId())).thenReturn(Optional.of(info));

        assertThatThrownBy(() -> service.declencherAutoSaisine(info.getId(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(dossierService, never()).submit(any(), anyString());
    }

    @Test
    void classerSansSuite_refuseSiAutoSaisineDejaDeclenchee() {
        InformationPreoccupante info = buildInfo(StatutInformationPreoccupante.AUTO_SAISINE_DECLENCHEE);

        when(informationPreoccupanteRepository.findById(info.getId())).thenReturn(Optional.of(info));

        assertThatThrownBy(() -> service.classerSansSuite(info.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void declencherAutoSaisine_renseigneLesChampsDeRecevabiliteParDefaut() {
        InformationPreoccupante info = buildInfo(StatutInformationPreoccupante.NOUVELLE);
        UUID dossierId = UUID.randomUUID();
        DossierResponse created = DossierResponse.builder().id(dossierId).number("ASCE-2026-000006").build();
        Dossier dossierRef = Dossier.builder().id(dossierId).number("ASCE-2026-000006").build();

        when(informationPreoccupanteRepository.findById(info.getId())).thenReturn(Optional.of(info));
        when(dossierService.submit(any(DossierCreateRequest.class), anyString())).thenReturn(created);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossierRef));
        when(informationPreoccupanteRepository.save(any(InformationPreoccupante.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.declencherAutoSaisine(info.getId(), "127.0.0.1");

        ArgumentCaptor<DossierCreateRequest> captor = ArgumentCaptor.forClass(DossierCreateRequest.class);
        verify(dossierService).submit(captor.capture(), eq("127.0.0.1"));
        DossierCreateRequest sent = captor.getValue();
        assertThat(sent.getDecisionJusticeExistante()).isFalse();
        assertThat(sent.getAutreInstitutionSaisie()).isFalse();
    }

    @Test
    void rattacherDossier_verifieLeControleDaccesViaDossierAccessGuard() {
        InformationPreoccupante info = buildInfo(StatutInformationPreoccupante.NOUVELLE);
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(dossierId).number("ASCE-2026-000007").build();

        when(informationPreoccupanteRepository.findById(info.getId())).thenReturn(Optional.of(info));
        when(dossierAccessGuard.getDossierOrThrow(dossierId)).thenReturn(dossier);
        when(informationPreoccupanteDossierRepository
                .existsByInformationPreoccupanteIdAndDossierId(info.getId(), dossierId))
                .thenReturn(false);
        when(informationPreoccupanteDossierRepository.findByInformationPreoccupanteId(info.getId()))
                .thenReturn(List.of());
        when(informationPreoccupanteRepository.save(any(InformationPreoccupante.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.rattacherDossier(info.getId(), dossierId, null);

        verify(dossierAccessGuard).getDossierOrThrow(dossierId);
        verify(dossierAccessGuard).checkReadAccess(dossier);
    }
}
