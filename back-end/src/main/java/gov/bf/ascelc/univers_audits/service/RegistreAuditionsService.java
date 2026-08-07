package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.response.RegistreAuditionEntryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RegistreAuditionsService {

    Page<RegistreAuditionEntryResponse> findAll(Pageable pageable);
}
