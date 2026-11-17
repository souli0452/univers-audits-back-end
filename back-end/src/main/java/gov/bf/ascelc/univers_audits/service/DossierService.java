package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.model.dto.request.DossierCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.DossierUpdateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.SetPriorityRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.StatusTransitionRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResume;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

public interface DossierService {

    DossierResponse findById(UUID id);

    /**
     * Date de réception et statut du dossier, après le même contrôle d'accès que {@link #findById}. Pour les calculs
     * qui n'ont pas besoin du détail : l'identité du déclarant n'est pas renvoyée, donc pas non plus tracée.
     */
    DossierResume findResume(UUID id);

    DossierResponse findByAccessCode(String accessCode);

    Page<DossierResponse> findByStatus(DossierStatus status, Pageable pageable);

    Page<DossierResponse> findMyDossiers(Pageable pageable);

    Page<DossierResponse> findAll(Pageable pageable);

    Page<DossierResponse> findByReceptionDateBetween(
            Instant start, Instant end, Pageable pageable);

    DossierResponse submit(DossierCreateRequest request, String ipAddress);

    DossierResponse registerReception(UUID dossierId,
                                      StatusTransitionRequest request,
                                      String ipAddress);

    DossierResponse startOpportunityStudy(UUID dossierId,
                                          StatusTransitionRequest request,
                                          String ipAddress);

    DossierResponse requestComplement(UUID dossierId,
                                      StatusTransitionRequest request,
                                      String ipAddress);

    DossierResponse complementReceived(UUID dossierId,
                                       StatusTransitionRequest request,
                                       String ipAddress);

    DossierResponse submitToCtadp(UUID dossierId,
                                  StatusTransitionRequest request,
                                  String ipAddress);

    DossierResponse declareAdmissible(UUID dossierId,
                                      StatusTransitionRequest request,
                                      String ipAddress);

    DossierResponse declareInadmissible(UUID dossierId,
                                        StatusTransitionRequest request,
                                        String ipAddress);

    DossierResponse transfer(UUID dossierId,
                             StatusTransitionRequest request,
                             String ipAddress);

    DossierResponse orientAdministratif(UUID dossierId,
                                        StatusTransitionRequest request,
                                        String ipAddress);

    DossierResponse close(UUID dossierId,
                          StatusTransitionRequest request,
                          String ipAddress);

    DossierResponse update(UUID dossierId, DossierUpdateRequest request);

    DossierResponse setConfidential(UUID dossierId,
                                    boolean value,
                                    StatusTransitionRequest request);

    DossierResponse revokeWhistleblowerProtection(UUID dossierId,
                                                  StatusTransitionRequest request);

    DossierResponse setPriority(UUID dossierId,
                                SetPriorityRequest request,
                                String ipAddress);
}