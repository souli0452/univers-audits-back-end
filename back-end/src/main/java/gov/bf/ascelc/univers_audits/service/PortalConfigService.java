package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.entity.PortalConfig;
import gov.bf.ascelc.univers_audits.repository.PortalConfigRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortalConfigService {

    private final PortalConfigRepository repo;

    @Transactional(readOnly = true)
    public Map<String, List<PortalConfig>> findAllGrouped() {
        return repo.findAllByOrderByGroupNameAscConfigKeyAsc()
                .stream()
                .collect(Collectors.groupingBy(
                        PortalConfig::getGroupName,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    @Transactional(readOnly = true)
    public Map<String, String> findAllAsMap() {
        return repo.findAllByOrderByGroupNameAscConfigKeyAsc()
                .stream()
                .collect(Collectors.toMap(
                        PortalConfig::getConfigKey,
                        c -> c.getConfigValue() != null ? c.getConfigValue() : "",
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    @Transactional
    public PortalConfig update(String configKey, String newValue, String updatedBy) {
        PortalConfig config = repo.findByConfigKey(configKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Configuration introuvable : " + configKey));

        config.setConfigValue(newValue);
        config.setUpdatedBy(updatedBy);
        PortalConfig saved = repo.save(config);
        log.info("[PortalConfig] Clé '{}' mise à jour par {}", configKey, updatedBy);
        return saved;
    }

    @Transactional
    public List<PortalConfig> updateBatch(Map<String, String> updates, String updatedBy) {
        List<PortalConfig> saved = new ArrayList<>();
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            repo.findByConfigKey(entry.getKey()).ifPresent(config -> {
                config.setConfigValue(entry.getValue());
                config.setUpdatedBy(updatedBy);
                saved.add(repo.save(config));
            });
        }
        log.info("[PortalConfig] {} clé(s) mise(s) à jour par {}", saved.size(), updatedBy);
        return saved;
    }
}