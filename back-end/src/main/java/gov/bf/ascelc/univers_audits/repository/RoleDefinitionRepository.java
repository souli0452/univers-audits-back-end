package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.RoleDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface RoleDefinitionRepository extends JpaRepository<RoleDefinition, UUID> {

    Optional<RoleDefinition> findByRoleKey(String roleKey);

    boolean existsByRoleKey(String roleKey);

    @Query("""
        SELECT DISTINCT rd FROM RoleDefinition rd
        LEFT JOIN FETCH rd.permissions
        WHERE rd.active = true
        ORDER BY rd.displayOrder ASC
    """)
    List<RoleDefinition> findAllActiveWithPermissions();

    @Query("""
        SELECT rd FROM RoleDefinition rd
        LEFT JOIN FETCH rd.permissions
        WHERE rd.roleKey = :roleKey
    """)
    Optional<RoleDefinition> findByRoleKeyWithPermissions(@Param("roleKey") String roleKey);
}