package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.JourFerieRequest;
import gov.bf.ascelc.univers_audits.model.entity.JourFerie;

import java.util.List;
import java.util.UUID;

public interface JourFerieService {

    List<JourFerie> findAllActifs();

    List<JourFerie> findAll();

    JourFerie create(JourFerieRequest request);

    JourFerie update(UUID id, JourFerieRequest request);
}
