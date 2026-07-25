package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.ParametreDelaiRequest;
import gov.bf.ascelc.univers_audits.model.entity.ParametreDelai;

import java.util.List;

public interface ParametreDelaiService {

    int resolveDelaiJours(String code);

    List<ParametreDelai> findAllActifs();

    List<ParametreDelai> findAll();

    ParametreDelai update(String code, ParametreDelaiRequest request);
}
