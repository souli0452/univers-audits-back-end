package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.RoleDefinitionRepository;
import gov.bf.ascelc.univers_audits.security.access.EndpointAccess;
import gov.bf.ascelc.univers_audits.security.access.EndpointAccessCollector;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.PermissionResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * « Qui peut faire quoi » : matrice des accès par rôle, calculée à partir des contrôles d'accès réellement
 * présents dans le code (@PreAuthorize sur les contrôleurs). Lecture seule : elle ne donne ni ne retire aucun droit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessMatrixService {

    /** Ordre d'affichage des rôles connus ; les autres rôles rencontrés dans le code sont ajoutés à la suite. */
    static final List<String> ROLES_CONNUS = List.of(
            "ADMIN_DDIC", "CGE", "CGEA", "CONSEILLER_JURIDIQUE",
            "CONTROLEUR_ETAT", "MEMBRE_CTADP", "AGENT_BRPD", "DCP");

    private static final Map<String, String> LIBELLES = Map.ofEntries(
            Map.entry("Dossier", "Dossiers"),
            Map.entry("DossierHabilitation", "Habilitations sur les dossiers"),
            Map.entry("FicheAffectation", "Fiches d'affectation"),
            Map.entry("EtudeOpportunite", "Étude d'opportunité"),
            Map.entry("SeanceCtadp", "Séances CTADP"),
            Map.entry("Investigation", "Investigations"),
            Map.entry("Audition", "Auditions"),
            Map.entry("RegistreAuditions", "Registre des auditions"),
            Map.entry("RapportEnquete", "Rapports d'enquête"),
            Map.entry("Statistique", "Statistiques"),
            Map.entry("Agent", "Agents"),
            Map.entry("RoleManagement", "Rôles et permissions"),
            Map.entry("AccessMatrix", "Accès par rôle"),
            Map.entry("Config", "Configuration publique"),
            Map.entry("Audit", "Journal d'audit"),
            Map.entry("Notification", "Notifications"),
            Map.entry("PortalConfig", "Paramètres du portail"),
            Map.entry("ParametreDelai", "Paramètres de délais"),
            Map.entry("Attachment", "Pièces jointes"),
            Map.entry("Pdf", "Documents PDF"),
            Map.entry("LeconAPartager", "Leçons à partager"),
            Map.entry("InformationPreoccupante", "Informations préoccupantes"));

    private final EndpointAccessCollector collector;
    private final PermissionResolver permissionResolver;
    private final RoleDefinitionRepository roleRepository;
    private final AgentRepository agentRepository;
    private final AgentService agentService;

    // ─── Données calculées ────────────────────────────────────────────────

    /** Un point d'entrée du back : son module, sa nature (lecture ou action) et les rôles autorisés (null = tout utilisateur connecté). */
    record Endpoint(String module, boolean lecture, Set<String> roles) {}

    public record Cellule(int consulter, int agir) {}

    public record ModuleAcces(String module, String libelle, int totalConsulter, int totalAgir,
                              Map<String, Cellule> parRole) {}

    public record RoleInfo(String code, int agents) {}

    public record Matrice(List<RoleInfo> roles, List<ModuleAcces> modules) {}

    // ─── API ──────────────────────────────────────────────────────────────

    public Matrice matrice() {
        List<EndpointAccess> acces = collector.collect();

        Set<String> tousRoles = new LinkedHashSet<>(ROLES_CONNUS);
        acces.stream().filter(a -> a.kind() == EndpointAccess.Kind.ROLES).forEach(a -> tousRoles.addAll(a.roles()));
        roleRepository.findAllActiveWithPermissions().forEach(r -> tousRoles.add(r.getRoleKey()));
        List<Endpoint> endpoints = versEndpoints(acces, tousRoles);

        Map<String, Integer> agentsParRole = compterAgentsParRole();
        List<RoleInfo> roles = tousRoles.stream()
                .map(r -> new RoleInfo(r, agentsParRole.getOrDefault(r, 0)))
                .toList();

        return new Matrice(roles, calculer(endpoints, new ArrayList<>(tousRoles)));
    }

    /** Cœur du calcul, sans dépendance à Spring (testable). */
    static List<ModuleAcces> calculer(List<Endpoint> endpoints, List<String> roles) {
        Map<String, List<Endpoint>> parModule = new TreeMap<>();
        for (Endpoint e : endpoints) {
            parModule.computeIfAbsent(e.module(), k -> new ArrayList<>()).add(e);
        }
        List<ModuleAcces> resultat = new ArrayList<>();
        for (var entry : parModule.entrySet()) {
            List<Endpoint> eps = entry.getValue();
            int totalLecture = (int) eps.stream().filter(Endpoint::lecture).count();
            int totalAction = eps.size() - totalLecture;
            Map<String, Cellule> cellules = new LinkedHashMap<>();
            for (String role : roles) {
                int lecture = 0, action = 0;
                for (Endpoint e : eps) {
                    boolean autorise = e.roles() == null || e.roles().contains(role);
                    if (!autorise) continue;
                    if (e.lecture()) lecture++; else action++;
                }
                cellules.put(role, new Cellule(lecture, action));
            }
            resultat.add(new ModuleAcces(entry.getKey(), libelle(entry.getKey()), totalLecture, totalAction, cellules));
        }
        resultat.sort(Comparator.comparing(ModuleAcces::libelle, String.CASE_INSENSITIVE_ORDER));
        return resultat;
    }

    /**
     * Rôles cités par une expression @PreAuthorize : null = tout utilisateur connecté,
     * ensemble vide = règle spécifique (non représentable dans la matrice).
     */
    static Set<String> roles(String expression) {
        return EndpointAccessCollector.roles(expression);
    }

    static String libelle(String module) {
        String l = LIBELLES.get(module);
        if (l != null) return l;
        return module.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    // ─── Lecture du code ──────────────────────────────────────────────────

    /**
     * Un point d'entrée protégé par une permission est ouvert aux rôles qui la détiennent en base ; les points
     * d'entrée publics et les règles spécifiques restent hors matrice.
     */
    private List<Endpoint> versEndpoints(List<EndpointAccess> acces, Set<String> roles) {
        Map<String, Set<String>> autoritesParRole = new HashMap<>();
        roles.forEach(r -> autoritesParRole.put(r, permissionResolver.resolve(List.of(r))));

        List<Endpoint> resultat = new ArrayList<>();
        for (EndpointAccess a : acces) {
            switch (a.kind()) {
                case AUTHENTICATED -> resultat.add(new Endpoint(a.module(), a.lecture(), null));
                case ROLES -> resultat.add(new Endpoint(a.module(), a.lecture(), a.roles()));
                case PERMISSION -> {
                    String autorite = AppPermission.AUTHORITY_PREFIX + a.permission().orElseThrow();
                    Set<String> autorises = new LinkedHashSet<>();
                    autoritesParRole.forEach((role, autorites) -> {
                        if (autorites.contains(autorite)) {
                            autorises.add(role);
                        }
                    });
                    resultat.add(new Endpoint(a.module(), a.lecture(), autorises));
                }
                default -> { /* public ou règle spécifique : hors matrice */ }
            }
        }
        return resultat;
    }

    private Map<String, Integer> compterAgentsParRole() {
        Map<String, Integer> compte = new HashMap<>();
        try {
            Set<String> idsAgents = new HashSet<>();
            for (Agent a : agentRepository.findAll()) {
                if (a.getKeycloakId() != null) idsAgents.add(a.getKeycloakId());
            }
            agentService.getRolesByKeycloakId().forEach((id, roles) -> {
                if (!idsAgents.contains(id)) return;
                roles.forEach(r -> compte.merge(r, 1, Integer::sum));
            });
        } catch (Exception ex) {
            log.warn("Comptage des agents par rôle impossible : {}", ex.getMessage());
        }
        return compte;
    }
}
