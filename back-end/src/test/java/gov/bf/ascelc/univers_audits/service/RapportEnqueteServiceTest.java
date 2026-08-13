package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.InvestigationStatus;
import gov.bf.ascelc.univers_audits.model.dto.request.NoteRecommandationsRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RapportEnqueteRequest;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.NoteRecommandations;
import gov.bf.ascelc.univers_audits.model.entity.RapportEnquete;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.NoteRecommandationsRepository;
import gov.bf.ascelc.univers_audits.repository.RapportEnqueteRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
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
class RapportEnqueteServiceTest {

    @Mock
    private RapportEnqueteRepository rapportEnqueteRepository;
    @Mock
    private NoteRecommandationsRepository noteRecommandationsRepository;
    @Mock
    private InvestigationRepository investigationRepository;
    @Mock
    private DossierAccessGuard accessGuard;

    @InjectMocks
    private RapportEnqueteService service;

    private Investigation investigation;
    private UUID investigationId;

    @BeforeEach
    void setUp() {
        investigationId = UUID.randomUUID();
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        investigation = Investigation.builder()
                .id(investigationId)
                .status(InvestigationStatus.IN_PROGRESS)
                .dossier(dossier)
                .build();
    }

    private RapportEnqueteRequest buildRequest() {
        return RapportEnqueteRequest.builder()
                .titre("Titre")
                .introduction("Introduction")
                .methodologie("Méthodologie")
                .informationsCollectees("Infos")
                .exposeFactuelAnomalies("Anomalies")
                .quantificationPrejudice("Préjudice")
                .conclusions("Conclusions")
                .build();
    }

    @Test
    void enregistrerRapport_creeUnNouveauRapportSiAucunNExisteEncore() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(rapportEnqueteRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());
        when(rapportEnqueteRepository.save(any(RapportEnquete.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RapportEnquete result = service.enregistrerRapport(investigationId, buildRequest());

        assertThat(result.getInvestigation()).isEqualTo(investigation);
        assertThat(result.getTitre()).isEqualTo("Titre");
        verify(rapportEnqueteRepository).save(any(RapportEnquete.class));
    }

    @Test
    void enregistrerRapport_metAJourLeRapportExistantAuDeuxiemeAppel() {
        RapportEnquete existant = RapportEnquete.builder()
                .investigation(investigation)
                .titre("Ancien titre")
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(rapportEnqueteRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(existant));
        when(rapportEnqueteRepository.save(any(RapportEnquete.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RapportEnquete result = service.enregistrerRapport(investigationId, buildRequest());

        assertThat(result).isSameAs(existant);
        assertThat(result.getTitre()).isEqualTo("Titre");
        verify(rapportEnqueteRepository).save(existant);
    }

    @Test
    void enregistrerRapport_rejetteSiInvestigationNEstPasEnCours() {
        investigation.setStatus(InvestigationStatus.COMPLETED);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.enregistrerRapport(investigationId, buildRequest()))
                .isInstanceOf(BusinessException.class);
        verify(rapportEnqueteRepository, never()).save(any());
    }

    @Test
    void getRapportOrThrow_leveResourceNotFoundExceptionSiAucunRapport() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(rapportEnqueteRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRapportOrThrow(investigationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getRapportOrThrow_propageBusinessExceptionSiAccesRefuse() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        doThrow(new BusinessException("Accès refusé — ce dossier ne vous est pas assigné"))
                .when(accessGuard).checkReadAccess(investigation.getDossier());

        assertThatThrownBy(() -> service.getRapportOrThrow(investigationId))
                .isInstanceOf(BusinessException.class);
        verify(rapportEnqueteRepository, never()).findByInvestigationId(any());
    }

    @Test
    void getRapportOrThrow_leveResourceNotFoundExceptionSiDossierConfidentielEtAgentNonPrivilegie() {
        investigation.getDossier().setIsConfidential(true);
        RapportEnquete rapport = RapportEnquete.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(rapportEnqueteRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(rapport));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        assertThatThrownBy(() -> service.getRapportOrThrow(investigationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void enregistrerNote_rejetteSiAucunRapportNExisteEncore() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(rapportEnqueteRepository.findByInvestigationId(investigationId)).thenReturn(Optional.empty());

        NoteRecommandationsRequest request = NoteRecommandationsRequest.builder()
                .contenu("Recommandation").build();

        assertThatThrownBy(() -> service.enregistrerNote(investigationId, request))
                .isInstanceOf(BusinessException.class);
        verify(noteRecommandationsRepository, never()).save(any());
    }

    @Test
    void enregistrerNote_creeUneNouvelleNoteSiLeRapportExiste() {
        RapportEnquete rapport = RapportEnquete.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(rapportEnqueteRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(rapport));
        when(noteRecommandationsRepository.findByRapportEnqueteId(rapport.getId())).thenReturn(Optional.empty());
        when(noteRecommandationsRepository.save(any(NoteRecommandations.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        NoteRecommandationsRequest request = NoteRecommandationsRequest.builder()
                .contenu("Recommandation n°1").build();

        NoteRecommandations result = service.enregistrerNote(investigationId, request);

        assertThat(result.getRapportEnquete()).isEqualTo(rapport);
        assertThat(result.getContenu()).isEqualTo("Recommandation n°1");
    }

    @Test
    void enregistrerNote_rejetteSiInvestigationNEstPasEnCours() {
        investigation.setStatus(InvestigationStatus.SUSPENDED);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));

        NoteRecommandationsRequest request = NoteRecommandationsRequest.builder()
                .contenu("Recommandation").build();

        assertThatThrownBy(() -> service.enregistrerNote(investigationId, request))
                .isInstanceOf(BusinessException.class);
        verify(noteRecommandationsRepository, never()).save(any());
    }

    @Test
    void getNoteOrThrow_leveResourceNotFoundExceptionSiAucuneNote() {
        RapportEnquete rapport = RapportEnquete.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(rapportEnqueteRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(rapport));
        when(noteRecommandationsRepository.findByRapportEnqueteId(rapport.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getNoteOrThrow(investigationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
