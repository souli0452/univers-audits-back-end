package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.EscalationLevel;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.DemandeDocumentsCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DemandeDocumentsResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.DemandeDocumentsRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DemandeDocumentsServiceImplTest {

    @Mock private DemandeDocumentsRepository demandeDocumentsRepository;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private ParametreDelaiService parametreDelaiService;
    @Mock private DossierDetailsMapper mapper;
    @Mock private AgentContextResolver agentContextResolver;
    @Mock private DossierAccessGuard accessGuard;

    @InjectMocks
    private DemandeDocumentsServiceImpl service;

    private Investigation buildInvestigation(Dossier dossier) {
        return Investigation.builder().id(UUID.randomUUID()).dossier(dossier).build();
    }

    @Test
    void create_resolvesInitialDeadlineAndSaves() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        Agent agent = Agent.builder().id(UUID.randomUUID()).build();

        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(parametreDelaiService.resolveDelaiJours("DEMANDE_DOCUMENTS_INITIAL"))
                .thenReturn(15);
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(demandeDocumentsRepository.save(any(DemandeDocuments.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(DemandeDocuments.class)))
                .thenReturn(DemandeDocumentsResponse.builder().build());

        DemandeDocumentsCreateRequest request = DemandeDocumentsCreateRequest.builder()
                .recipientLabel("Banque XYZ")
                .documentsRequested("Relevés de compte 2024-2025")
                .build();

        service.create(investigation.getId(), request);

        verify(demandeDocumentsRepository).save(argThat(d ->
                d.getRecipientLabel().equals("Banque XYZ")
                        && d.getEscalationLevel() == EscalationLevel.INITIAL
                        && d.getRequestedBy() == agent
                        && d.getDeadline() != null));
    }

    @Test
    void create_throwsWhenAgentLacksReadAccess() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        doThrow(new BusinessException("Accès refusé"))
                .when(accessGuard).checkReadAccess(dossier);

        DemandeDocumentsCreateRequest request = DemandeDocumentsCreateRequest.builder()
                .recipientLabel("Banque XYZ")
                .documentsRequested("Relevés")
                .build();

        assertThatThrownBy(() -> service.create(investigation.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void escalate_movesToRelanceWhenOverdue() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        DemandeDocuments demande = DemandeDocuments.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .escalationLevel(EscalationLevel.INITIAL)
                .received(false)
                .deadline(Instant.now().minusSeconds(3600))
                .build();

        when(demandeDocumentsRepository.findById(demande.getId()))
                .thenReturn(Optional.of(demande));
        when(parametreDelaiService.resolveDelaiJours("DEMANDE_DOCUMENTS_RELANCE"))
                .thenReturn(7);
        when(demandeDocumentsRepository.save(any(DemandeDocuments.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(DemandeDocuments.class)))
                .thenReturn(DemandeDocumentsResponse.builder().build());

        service.escalate(demande.getId());

        assertThat(demande.getEscalationLevel()).isEqualTo(EscalationLevel.RELANCE);
    }

    @Test
    void escalate_throwsWhenNotYetOverdue() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        DemandeDocuments demande = DemandeDocuments.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .escalationLevel(EscalationLevel.INITIAL)
                .received(false)
                .deadline(Instant.now().plusSeconds(3600))
                .build();

        when(demandeDocumentsRepository.findById(demande.getId()))
                .thenReturn(Optional.of(demande));

        assertThatThrownBy(() -> service.escalate(demande.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void escalate_throwsWhenAlreadyAtTerminalLevel() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).build();
        Investigation investigation = buildInvestigation(dossier);
        DemandeDocuments demande = DemandeDocuments.builder()
                .id(UUID.randomUUID())
                .investigation(investigation)
                .escalationLevel(EscalationLevel.SAISINE_JUDICIAIRE)
                .received(false)
                .deadline(Instant.now().minusSeconds(3600))
                .build();

        when(demandeDocumentsRepository.findById(demande.getId()))
                .thenReturn(Optional.of(demande));

        assertThatThrownBy(() -> service.escalate(demande.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void findByInvestigationId_returnsEmptyWhenConfidentialAndNotAuthorized() {
        Dossier dossier = Dossier.builder().id(UUID.randomUUID()).isConfidential(true).build();
        Investigation investigation = buildInvestigation(dossier);
        when(investigationRepository.findById(investigation.getId()))
                .thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        List<DemandeDocumentsResponse> result = service.findByInvestigationId(investigation.getId());

        assertThat(result).isEmpty();
        verify(demandeDocumentsRepository, never()).findByInvestigationIdOrderBySentAtDesc(any());
    }

    @Test
    void findByInvestigationId_throwsWhenInvestigationUnknown() {
        UUID id = UUID.randomUUID();
        when(investigationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByInvestigationId(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
