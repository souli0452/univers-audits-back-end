package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.TypeInfractionRequest;
import gov.bf.ascelc.univers_audits.model.entity.TypeInfraction;

import java.util.List;

public interface TypeInfractionService {

    List<TypeInfraction> findAllActifs();

    List<TypeInfraction> findAll();

    TypeInfraction create(TypeInfractionRequest request);

    TypeInfraction update(String code, TypeInfractionRequest request);
}
