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


    InvestigationResponse addMember(UUID investigationId,
                                    AddMemberRequest request,
                                    String ipAddress);

    InvestigationResponse removeMember(UUID investigationId,
                                       UUID agentId,
                                       String ipAddress);
}