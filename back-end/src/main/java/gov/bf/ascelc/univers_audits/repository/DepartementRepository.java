package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.Departement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepartementRepository
        extends JpaRepository<Departement, UUID> {

    Optional<Departement> findByCode(String code);

    List<Departement> findByActifTrueOrderByOrdreAffichageAsc();
}