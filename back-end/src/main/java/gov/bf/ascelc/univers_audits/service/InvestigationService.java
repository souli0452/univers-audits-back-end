package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.InvestigationCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.InvestigationUpdateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.ExtendDeadlineRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.AddMemberRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.EngagementConfidentialiteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.InvestigationResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.MandatResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.EngagementConfidentialiteResponse;
import gov.bf.ascelc.univers_audits.model.dto.request.PlanInvestigationSubmitRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PlanInvestigationRevisionRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PlanInvestigationResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.RevisionPlanResponse;
import gov.bf.ascelc.univers_audits.model.dto.request.IncidentObjectiviteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.IncidentObjectiviteResponse;
import gov.bf.ascelc.univers_audits.model.dto.request.ProcedureUrgenceRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.ProcedureUrgenceDecisionRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.MesureConservatoireRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.ProcedureUrgenceResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.MesureConservatoireResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface InvestigationService {

    InvestigationResponse findById(UUID id);

    InvestigationResponse findByDossierId(UUID dossierId);

    Page<InvestigationResponse> findAll(Pageable pageable);

    Page<InvestigationResponse> findOverdue(Pageable pageable);

    /** Filtre par période de démarrage — utilisé par le rapport investigations */
    Page<InvestigationResponse> findByPeriod(
            Instant start, Instant end, Pageable pageable);

    InvestigationResponse open(UUID dossierId,
                               InvestigationCreateRequest request,
                               String ipAddress);

    InvestigationResponse start(UUID investigationId,
                                String ipAddress);

    InvestigationResponse suspend(UUID investigationId,
                                  String reason,
                                  String ipAddress);

    InvestigationResponse resume(UUID investigationId,
                                 String reason,
                                 String ipAddress);

    InvestigationResponse extendDeadline(UUID investigationId,
                                         ExtendDeadlineRequest request,
                                         String ipAddress);

    InvestigationResponse submitReport(UUID investigationId,
                                       InvestigationUpdateRequest request,
                                       String ipAddress);

    InvestigationResponse approveDei(UUID investigationId,
                                     String ipAddress);

    InvestigationResponse approveLegalAdvisor(UUID investigationId,
                                              String ipAddress);

    InvestigationResponse approveCge(UUID investigationId,
                                     String reason,
                                     String ipAddress);

    InvestigationResponse approveCgea(UUID investigationId, String ipAddress);

    InvestigationResponse rejectDei(UUID investigationId, String motif, String ipAddress);

    InvestigationResponse rejectCgea(UUID investigationId, String motif, String ipAddress);

    InvestigationResponse rejectCge(UUID investigationId, String motif, String ipAddress);

    InvestigationResponse addMember(UUID investigationId,
                                    AddMemberRequest request,
                                    String ipAddress);

    InvestigationResponse removeMember(UUID investigationId,
                                       UUID agentId,
                                       String ipAddress);

    MandatResponse deliverMandat(UUID investigationId, String ipAddress);

    MandatResponse getMandat(UUID investigationId);

    EngagementConfidentialiteResponse declareEngagementPrealable(
            UUID investigationId,
            EngagementConfidentialiteRequest request,
            String ipAddress);

    EngagementConfidentialiteResponse getEngagementPrealable(
            UUID investigationId, UUID agentId);

    PlanInvestigationResponse submitPlan(
            UUID investigationId,
            PlanInvestigationSubmitRequest request,
            String ipAddress);

    PlanInvestigationResponse revisePlan(
            UUID investigationId,
            PlanInvestigationRevisionRequest request,
            String ipAddress);

    PlanInvestigationResponse validatePlan(UUID investigationId, String ipAddress);

    PlanInvestigationResponse getPlan(UUID investigationId);

    List<RevisionPlanResponse> getPlanRevisions(UUID investigationId);

    IncidentObjectiviteResponse declareIncident(
            UUID investigationId,
            IncidentObjectiviteRequest request,
            String ipAddress);

    List<IncidentObjectiviteResponse> getIncidents(UUID investigationId);

    ProcedureUrgenceResponse demanderProcedureUrgence(
            UUID investigationId,
            ProcedureUrgenceRequest request,
            String ipAddress);

    ProcedureUrgenceResponse approuverProcedureUrgence(
            UUID investigationId,
            UUID procedureId,
            ProcedureUrgenceDecisionRequest request,
            String ipAddress);

    ProcedureUrgenceResponse rejeterProcedureUrgence(
            UUID investigationId,
            UUID procedureId,
            ProcedureUrgenceDecisionRequest request,
            String ipAddress);

    List<ProcedureUrgenceResponse> getProcedures(UUID investigationId);

    MesureConservatoireResponse declarerMesureConservatoire(
            UUID investigationId,
            MesureConservatoireRequest request,
            String ipAddress);

    List<MesureConservatoireResponse> getMesures(UUID investigationId);
}