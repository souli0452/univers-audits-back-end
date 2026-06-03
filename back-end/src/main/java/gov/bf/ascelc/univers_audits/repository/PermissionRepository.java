package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    List<Permission> findAllByActiveTrueOrderByCategoryAscLabelAsc();
    List<Permission> findByPermissionKeyIn(Set<String> keys);
}