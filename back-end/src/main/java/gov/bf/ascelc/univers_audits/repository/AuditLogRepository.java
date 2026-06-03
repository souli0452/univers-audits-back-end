package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, UUID>,
        JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.createdAt >= :since")
    long countSince(@Param("since") Instant since);

    @Query("""
        SELECT a.action, COUNT(a)
        FROM AuditLog a
        GROUP BY a.action
        ORDER BY COUNT(a) DESC
    """)
    List<Object[]> countByAction();

    @Query("""
        SELECT COALESCE(a.agentName, 'Inconnu'), COUNT(a)
        FROM AuditLog a
        GROUP BY a.agentName
        ORDER BY COUNT(a) DESC
    """)
    List<Object[]> countByAgent();
}