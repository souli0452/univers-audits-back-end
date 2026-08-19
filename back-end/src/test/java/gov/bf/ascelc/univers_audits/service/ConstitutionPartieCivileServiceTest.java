package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.ConstitutionPartieCivileRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.ConstitutionPartieCivileResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.ConstitutionPartieCivileRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConstitutionPartieCivileServiceTest {

    @Mock private ConstitutionPartieCivileRepository constitutionPartieCivileRepository;
    @Mock private InvestigationRepository investigationRepository;
    @Mock private DossierAccessGuard accessGuard;
    @Mock private AgentContextResolver agentContextResolver;

    @InjectMocks
    private ConstitutionPartieCivileService service;

    private Investigation investigation;
    private UUID investigationId;
    private Dossier dossier;

    @BeforeEach
    void setUp() {
        investigationId = UUID.randomUUID();
        dossier = Dossier.builder().id(UUID.randomUUID()).build();
        investigation = Investigation.builder()
                .id(investigationId)
                .dossier(dossier)
                .cgeApprovedAt(Instant.now())
                .build();
    }

    private ConstitutionPartieCivileRequest.ConstitutionPartieCivileRequestBuilder validRequest() {
        return ConstitutionPartieCivileRequest.builder()
                .justification("Prejudice financier direct subi par l'Etat, preuve suffisante au dossier")
                .montantReclame(new BigDecimal("15000000.00"));
    }

    @Test
    void creer_rejetteSiDecisionFinaleNonRendue() {
        investigation.setCgeApprovedAt(null);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));

        assertThatThrownBy(() -> service.creer(investigationId, validRequest().build()))
                .isInstanceOf(BusinessException.class);
        verify(constitutionPartieCivileRepository, never()).save(any());
    }

    @Test
    void creer_rejetteSiConstitutionDejaExistante() {
        ConstitutionPartieCivile existante = ConstitutionPartieCivile.builder()
                .investigation(investigation)
                .constitueAt(Instant.now())
                .justification("Deja constitue")
                .submittedAt(Instant.now())
                .build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(constitutionPartieCivileRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.of(existante));

        assertThatThrownBy(() -> service.creer(investigationId, validRequest().build()))
                .isInstanceOf(BusinessException.class);
        verify(constitutionPartieCivileRepository, never()).save(any());
    }

    @Test
    void creer_rejetteSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        assertThatThrownBy(() -> service.creer(investigationId, validRequest().build()))
                .isInstanceOf(BusinessException.class);
        verify(constitutionPartieCivileRepository, never()).save(any());
    }

    @Test
    void creer_succeedsEtRenseigneConstitueAtEtConstitueePar() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Jean").lastName("Ouedraogo").build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(constitutionPartieCivileRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(constitutionPartieCivileRepository.save(any(ConstitutionPartieCivile.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ConstitutionPartieCivileResponse result = service.creer(investigationId, validRequest().build());

        assertThat(result.getConstitueeParNom()).isEqualTo("Jean Ouedraogo");
        assertThat(result.getSubmittedAt()).isNotNull();
        verify(constitutionPartieCivileRepository).save(argThat(c ->
                c.getInvestigation() == investigation
                        && c.getConstitueePar() == agent
                        && c.getConstitueAt() != null
                        && c.getJustification().equals(
                                "Prejudice financier direct subi par l'Etat, preuve suffisante au dossier")));
    }

    @Test
    void creer_succeedsSansMontantReclame() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).firstName("Awa").lastName("Sawadogo").build();
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(constitutionPartieCivileRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());
        when(agentContextResolver.getCurrentAgent()).thenReturn(agent);
        when(constitutionPartieCivileRepository.save(any(ConstitutionPartieCivile.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ConstitutionPartieCivileRequest request = ConstitutionPartieCivileRequest.builder()
                .justification("Montant du prejudice non encore chiffre par les experts")
                .build();

        ConstitutionPartieCivileResponse result = service.creer(investigationId, request);

        assertThat(result.getMontantReclame()).isNull();
    }

    @Test
    void getOrThrow_leveResourceNotFoundExceptionSiAucuneConstitution() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(constitutionPartieCivileRepository.findByInvestigationId(investigationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrThrow(investigationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getOrThrow_masqueSiDossierConfidentielEtAgentNonPrivilegie() {
        dossier.setIsConfidential(true);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(accessGuard.canSeeConfidential()).thenReturn(false);

        assertThatThrownBy(() -> service.getOrThrow(investigationId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(constitutionPartieCivileRepository, never()).findByInvestigationId(any());
    }

    @Test
    void getOrThrow_leveBusinessExceptionSiAccesRefuse() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        doThrow(new BusinessException("Accès refusé — ce dossier ne vous est pas assigné"))
                .when(accessGuard).checkReadAccess(dossier);

        assertThatThrownBy(() -> service.getOrThrow(investigationId))
                .isInstanceOf(BusinessException.class);
        verify(constitutionPartieCivileRepository, never()).findByInvestigationId(any());
    }
}
