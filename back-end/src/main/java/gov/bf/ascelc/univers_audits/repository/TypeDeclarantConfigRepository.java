package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import gov.bf.ascelc.univers_audits.model.entity.TypeDeclarantConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TypeDeclarantConfigRepository
        extends JpaRepository<TypeDeclarantConfig, UUID> {

    Optional<TypeDeclarantConfig> findByTypeDeclarant(
            TypeDeclarant typeDeclarant);

    List<TypeDeclarantConfig> findByVisibleOnPublicFormTrueAndActiveTrueOrderByDisplayOrderAsc();
}
