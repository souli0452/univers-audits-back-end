package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.TypeInfraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TypeInfractionRepository
        extends JpaRepository<TypeInfraction, UUID> {

    Optional<TypeInfraction> findByCode(String code);

    List<TypeInfraction> findByActifTrueOrderByOrdreAsc();
}
