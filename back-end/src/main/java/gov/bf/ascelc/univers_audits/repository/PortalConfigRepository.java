package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.PortalConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface PortalConfigRepository extends JpaRepository<PortalConfig, UUID> {
    Optional<PortalConfig> findByConfigKey(String configKey);
    List<PortalConfig> findAllByOrderByGroupNameAscConfigKeyAsc();
    List<PortalConfig> findByGroupNameOrderByConfigKeyAsc(String groupName);
}