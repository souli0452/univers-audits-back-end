package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.CreateAgentRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.UpdateAgentRequest;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentRepository agentRepository;
    private final KeycloakAdminService keycloakAdminService;
    public Page<Agent> findAll(int page, int size) {
        return agentRepository.findAll(
                PageRequest.of(page, size, Sort.by("lastName").ascending())
        );
    }

    public Agent findById(UUID id) {
        return agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent introuvable: " + id));
    }

    public List<String> getAvailableKeycloakRoles() {
        return keycloakAdminService.getAvailableRoles();
    }

    public List<String> getAgentKeycloakRoles(UUID agentId) {
        Agent agent = findById(agentId);
        if (agent.getKeycloakId() == null) return List.of();
        return keycloakAdminService.getUserRoles(agent.getKeycloakId());
    }

    @Transactional
    public Agent createAgent(CreateAgentRequest req) {

        if (agentRepository.existsByEmail(req.email())) {
            throw new ConflictException("Email déjà utilisé: " + req.email());
        }
        if (agentRepository.existsByMatricule(req.matricule())) {
            throw new ConflictException("Matricule déjà utilisé: " + req.matricule());
        }

        String keycloakId = keycloakAdminService.createUser(
                req.email(), req.firstName(), req.lastName(), req.matricule()
        );

        // À partir d'ici, le user Keycloak existe déjà et n'est couvert par
        // aucune transaction DB — tout échec doit compenser en le supprimant,
        // sinon il reste orphelin (aucun Agent correspondant en base).
        try {
            if (req.keycloakRoles() != null && !req.keycloakRoles().isEmpty()) {
                keycloakAdminService.assignRoles(keycloakId, req.keycloakRoles());
            }

            keycloakAdminService.sendPasswordResetEmail(keycloakId);

            Agent agent = Agent.builder()
                    .matricule(req.matricule())
                    .firstName(req.firstName())
                    .lastName(req.lastName())
                    .email(req.email())
                    .phoneNumber(req.phoneNumber())
                    .grade(req.grade())
                    .keycloakId(keycloakId)
                    .actif(true)
                    .build();

            return agentRepository.save(agent);
        } catch (RuntimeException e) {
            log.error("Échec de la création de l'agent après création Keycloak "
                    + "— compensation : suppression du user Keycloak {}", keycloakId, e);
            keycloakAdminService.deleteUser(keycloakId);
            throw e;
        }
    }



    @Transactional
    public Agent updateAgent(UUID id, UpdateAgentRequest req) {
        Agent agent = findById(id);

        agent.setFirstName(req.firstName());
        agent.setLastName(req.lastName());
        agent.setEmail(req.email());
        agent.setPhoneNumber(req.phoneNumber());
        agent.setGrade(req.grade());

        if (req.keycloakRoles() != null && agent.getKeycloakId() != null) {
            updateKeycloakRoles(agent.getKeycloakId(), req.keycloakRoles());
        }

        return agentRepository.save(agent);
    }

    @Transactional
    public void updateAgentRoles(UUID agentId, List<String> newRoles) {
        Agent agent = findById(agentId);
        if (agent.getKeycloakId() == null) {
            throw new BusinessException("Agent sans compte Keycloak associé");
        }
        updateKeycloakRoles(agent.getKeycloakId(), newRoles);
    }



    @Transactional
    public Agent activate(UUID id) {
        Agent agent = findById(id);
        agent.setActif(true);
        if (agent.getKeycloakId() != null) {
            keycloakAdminService.setUserEnabled(agent.getKeycloakId(), true);
        }
        return agentRepository.save(agent);
    }

    @Transactional
    public Agent deactivate(UUID id) {
        Agent agent = findById(id);
        agent.setActif(false);
        if (agent.getKeycloakId() != null) {
            keycloakAdminService.setUserEnabled(agent.getKeycloakId(), false);
        }
        return agentRepository.save(agent);
    }



    private void updateKeycloakRoles(String keycloakId, List<String> newRoles) {
        List<String> current = keycloakAdminService.getUserRoles(keycloakId);
        List<String> toAdd    = newRoles.stream()
                .filter(r -> !current.contains(r)).toList();
        List<String> toRemove = current.stream()
                .filter(r -> !newRoles.contains(r)).toList();

        if (!toAdd.isEmpty())    keycloakAdminService.assignRoles(keycloakId, toAdd);
        if (!toRemove.isEmpty()) keycloakAdminService.removeRoles(keycloakId, toRemove);
    }
}