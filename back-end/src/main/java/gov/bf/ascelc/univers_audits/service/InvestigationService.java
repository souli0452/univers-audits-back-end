package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.InvestigationCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.InvestigationUpdateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.ExtendDeadlineRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.AddMemberRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.InvestigationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface InvestigationService {

    InvestigationResponse findById(UUID id);
    InvestigationResponse findByDossierId(UUID dossierId);
    Page<InvestigationResponse> findAll(Pageable pageable);
    Page<InvestigationResponse> findOverdue(Pageable pageable);

    InvestigationResponse open(UUID dossierId,
                               InvestigationCreateRequest request,
                               String ipAddress);

    // Démarrage officiel après constitution de l'équipe
    InvestigationResponse start(UUID investigationId,
                                String ipAddress);

    // Suspension temporaire avec motif obligatoire
    InvestigationResponse suspend(UUID investigationId,
                                  String reason,
                                  String ipAddress);

    // Reprise après suspension
    InvestigationResponse resume(UUID investigationId,
                                 String reason,
                                 String ipAddress);

    // Extension du délai de 90 jours — validée par le CGEA
    InvestigationResponse extendDeadline(UUID investigationId,
                                         ExtendDeadlineRequest request,
                                         String ipAddress);

    // Remise du rapport final — démarre le circuit d'approbation
    InvestigationResponse submitReport(UUID investigationId,
                                       InvestigationUpdateRequest request,
                                       String ipAddress);

    // Approbation DEI (15 jours ouvrables)
    InvestigationResponse approveDei(UUID investigationId,
                                     String ipAddress);

    // Approbation conseiller juridique (10 jours ouvrables)
    InvestigationResponse approveLegalAdvisor(UUID investigationId,
                                              String ipAddress);

    // Approbation finale CGE/CGEA (20 jours ouvrables)
    InvestigationResponse approveCge(UUID investigationId,
                                     String reason,
                                     String ipAddress);

    // ── Gestion de l'équipe
    InvestigationResponse addMember(UUID investigationId,
                                    AddMemberRequest request,
                                    String ipAddress);

    InvestigationResponse removeMember(UUID investigationId,
                                       UUID agentId,
                                       String ipAddress);
}