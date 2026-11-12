package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private final ApplicationContext context;
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
        List<Endpoint> endpoints = collecterEndpoints();

        Set<String> tousRoles = new LinkedHashSet<>(ROLES_CONNUS);
        endpoints.stream().filter(e -> e.roles() != null).forEach(e -> tousRoles.addAll(e.roles()));

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
        Set<String> trouves = new LinkedHashSet<>();
        Matcher any = Pattern.compile("hasAnyRole\\(([^)]*)\\)").matcher(expression);
        while (any.find()) {
            Matcher q = Pattern.compile("'([^']+)'").matcher(any.group(1));
            while (q.find()) trouves.add(q.group(1));
        }
        Matcher one = Pattern.compile("hasRole\\('([^']+)'\\)").matcher(expression);
        while (one.find()) trouves.add(one.group(1));
        Matcher auth = Pattern.compile("hasAuthority\\('ROLE_([^']+)'\\)").matcher(expression);
        while (auth.find()) trouves.add(auth.group(1));

        if (!trouves.isEmpty()) return trouves;
        if (expression.contains("isAuthenticated()")) return null;
        return Collections.emptySet();
    }

    static String libelle(String module) {
        String l = LIBELLES.get(module);
        if (l != null) return l;
        return module.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    // ─── Lecture du code ──────────────────────────────────────────────────

    private List<Endpoint> collecterEndpoints() {
        List<Endpoint> resultat = new ArrayList<>();
        RequestMappingHandlerMapping mapping =
                context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapping.getHandlerMethods().entrySet()) {
            HandlerMethod hm = e.getValue();
            Class<?> type = hm.getBeanType();
            if (!type.getName().startsWith("gov.bf.ascelc")) continue;

            PreAuthorize pa = AnnotatedElementUtils.findMergedAnnotation(hm.getMethod(), PreAuthorize.class);
            if (pa == null) pa = AnnotatedElementUtils.findMergedAnnotation(type, PreAuthorize.class);
            if (pa == null) continue;                              // point d'entrée public ou géré ailleurs

            Set<String> roles = roles(pa.value());
            if (roles != null && roles.isEmpty()) continue;        // règle spécifique : hors matrice

            Set<RequestMethod> methodes = e.getKey().getMethodsCondition().getMethods();
            boolean lecture = methodes.isEmpty() || methodes.contains(RequestMethod.GET)
                    || methodes.contains(RequestMethod.HEAD);
            String nom = type.getSimpleName().replaceAll("Controller$", "");
            resultat.add(new Endpoint(nom, lecture, roles));
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
