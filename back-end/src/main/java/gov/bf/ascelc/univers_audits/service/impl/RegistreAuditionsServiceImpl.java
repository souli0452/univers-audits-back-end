package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.model.dto.response.RegistreAuditionEntryResponse;
import gov.bf.ascelc.univers_audits.model.entity.Audition;
import gov.bf.ascelc.univers_audits.model.entity.PVAudition;
import gov.bf.ascelc.univers_audits.repository.AuditionRepository;
import gov.bf.ascelc.univers_audits.repository.PVAuditionRepository;
import gov.bf.ascelc.univers_audits.service.RegistreAuditionsService;
import gov.bf.ascelc.univers_audits.shared.utils.AuditionDisplayNameMasker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegistreAuditionsServiceImpl implements RegistreAuditionsService {

    private final AuditionRepository auditionRepository;
    private final PVAuditionRepository pvAuditionRepository;
    private final AuditionDisplayNameMasker displayNameMasker;

    @Override
    public Page<RegistreAuditionEntryResponse> findAll(Pageable pageable) {
        Page<Audition> auditions = auditionRepository.findAllForRegistre(pageable);

        Map<UUID, PVAudition> pvByAuditionId = pvAuditionRepository
                .findByAuditionIdIn(auditions.getContent().stream().map(Audition::getId).toList())
                .stream()
                .collect(Collectors.toMap(pv -> pv.getAudition().getId(), pv -> pv));

        return auditions.map(a -> toEntry(a, pvByAuditionId.get(a.getId())));
    }

    private RegistreAuditionEntryResponse toEntry(Audition audition, PVAudition pv) {
        RegistreAuditionEntryResponse.PvStatus pvStatus = RegistreAuditionEntryResponse.PvStatus.AUCUN_PV;
        Integer pvVersion = 0;
        if (pv != null) {
            pvStatus = pv.isFinalized()
                    ? RegistreAuditionEntryResponse.PvStatus.FINALISE
                    : RegistreAuditionEntryResponse.PvStatus.BROUILLON;
            pvVersion = pv.getPvVersion();
        }
        return RegistreAuditionEntryResponse.builder()
                .auditionId(audition.getId())
                .investigationId(audition.getInvestigation().getId())
                .dossierNumber(audition.getInvestigation().getDossier().getNumber())
                .intervieweeType(audition.getIntervieweeType())
                .intervieweeDisplayName(displayNameMasker.mask(audition))
                .scheduledAt(audition.getScheduledAt())
                .status(audition.getStatus())
                .pvStatus(pvStatus)
                .pvVersion(pvVersion)
                .build();
    }
}
