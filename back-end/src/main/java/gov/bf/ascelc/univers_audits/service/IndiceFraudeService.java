package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.IndiceFraudeRequest;
import gov.bf.ascelc.univers_audits.model.entity.IndiceFraude;

import java.util.List;

public interface IndiceFraudeService {

    List<IndiceFraude> findAllActifs(String categorie);

    List<IndiceFraude> findAll();

    IndiceFraude create(IndiceFraudeRequest request);

    IndiceFraude update(String code, IndiceFraudeRequest request);
}
