package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.IndiceFraude;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IndiceFraudeRepository
        extends JpaRepository<IndiceFraude, UUID> {

    Optional<IndiceFraude> findByCode(String code);

    boolean existsByCode(String code);

    List<IndiceFraude> findByActifTrueOrderByOrdreAsc();

    List<IndiceFraude> findByActifTrueAndCategorieOrderByOrdreAsc(String categorie);
}
