package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.LoginLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface LoginLogRepository
        extends JpaRepository<LoginLog, UUID>,
        JpaSpecificationExecutor<LoginLog> {

    Page<LoginLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT COUNT(l) FROM LoginLog l WHERE l.success = true  AND l.createdAt >= :since")
    long countSuccessSince(@Param("since") Instant since);

    @Query("SELECT COUNT(l) FROM LoginLog l WHERE l.success = false AND l.createdAt >= :since")
    long countFailureSince(@Param("since") Instant since);
}