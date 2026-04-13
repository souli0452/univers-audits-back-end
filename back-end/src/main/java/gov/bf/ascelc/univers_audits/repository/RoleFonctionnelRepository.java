package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.RoleFonctionnel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleFonctionnelRepository
        extends JpaRepository<RoleFonctionnel, UUID> {

    Optional<RoleFonctionnel> findByCode(String code);

    List<RoleFonctionnel> findByActifTrue();
}