package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.Agent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentRepository extends JpaRepository<Agent, UUID> {

    Optional<Agent> findByKeycloakId(String keycloakId);

    Optional<Agent> findByEmail(String email);

    Optional<Agent> findByMatricule(String matricule);

    boolean existsByKeycloakId(String keycloakId);

    boolean existsByEmail(String email);

    boolean existsByMatricule(String matricule);

    Page<Agent> findByActifTrue(Pageable pageable);
}