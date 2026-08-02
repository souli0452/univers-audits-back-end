# Contrôle d'accès en écriture — `DossierServiceImpl.update()` — Design

Statut : approuvé par l'utilisateur le 2026-08-02. Périmètre : fermeture partielle du point
"Contrôle d'accès en ECRITURE" reporté par la revue finale du chantier habilitation-lecture
(2026-07-29).

## Contexte

Le chantier d'habilitation nominative (2026-07-29) a couvert la LECTURE d'un dossier
(`findById`/`findAll`/`findByReceptionDateBetween` via `DossierAccessGuard.checkReadAccess`)
mais a explicitement exclu les ~14 endpoints d'écriture de `DossierController`, chacun ayant
sa propre sémantique d'autorisation, à analyser transition par transition.

Inventaire fait le 2026-08-02 en lisant `DossierController` et
`DossierServiceImpl.update()` :

- **6 endpoints déjà réservés à CGE/CGEA/ADMIN_DDIC** (`declareAdmissible`,
  `declareInadmissible`, `transfer`, `close`, `confidential`, `priority`) : ces rôles
  bypassent déjà l'habilitation en lecture (`DossierAccessGuard.canSeeConfidential()`) — rien
  à corriger, le comportement actuel est cohérent avec le modèle existant.
- **`create`/`registerReception`** : actions de prise en charge d'un dossier pas encore
  affecté (`registerReception` EST l'action qui accorde l'habilitation `AGENT_IN_CHARGE`) —
  aucune vérification par-dossier possible ni pertinente.
- **4 endpoints "file de département"** (`startOpportunityStudy`, `requestComplement`,
  `complementReceived`, `submitToCtadp`, réservés à CONSEILLER_JURIDIQUE/MEMBRE_CTADP) : aucun
  mécanisme n'accorde actuellement d'habilitation nominative à ces rôles avant leur
  intervention. Exiger une habilitation stricte bloquerait tout conseiller juridique non
  `agentInCharge` — hypothèse d'un modèle de file partagée par rôle, non vérifiée. **Hors
  périmètre de cette spec, documenté comme arbitrage métier en attente (voir Hors périmètre).**
- **`update` (PUT /{id})** : édition large du contenu du dossier par AGENT_BRPD ou
  CONSEILLER_JURIDIQUE, sans aucune vérification d'affectation — seul cas net identifié. Une
  restriction spécifique existe déjà pour les dossiers "protection lanceur d'alerte" (CGE/CGEA
  uniquement), mais rien ne protège les dossiers non protégés contre une édition par un agent
  du bon rôle mais non affecté à CE dossier précis.

## Décision

Ajouter `DossierAccessGuard.checkReadAccess(dossier)` en tête de
`DossierServiceImpl.update()`, avant la vérification de protection lanceur d'alerte
existante. Réutilisation stricte de la logique déjà testée (bypass CGE/CGEA/ADMIN_DDIC, sinon
habilitation nominative active requise — `AGENT_IN_CHARGE`, `INVESTIGATION_TEAM` ou `MANUAL`,
peu importe la source). Aucune divergence de sémantique entre lecture et écriture n'est
justifiée ici : un membre de l'équipe d'investigation qui peut déjà lire un dossier doit
pouvoir en éditer le contenu au même titre que l'agent en charge.

Pas de nouveau mécanisme, pas de migration, pas de nouvelle méthode sur `DossierAccessGuard` —
un seul appel ajouté à une méthode existante.

## Effet de bord attendu

Un agent AGENT_BRPD ou CONSEILLER_JURIDIQUE non habilité sur un dossier reçoit désormais une
`BusinessException` ("Accès refusé — ce dossier ne vous est pas assigné") au lieu de pouvoir
modifier son contenu — comportement identique à celui déjà en place sur `findById` pour ces
mêmes rôles. Breaking change côté client : le frontend doit gérer ce cas d'erreur sur l'écran
d'édition de dossier s'il ne le fait pas déjà pour la lecture.

## Hors périmètre

- Les 4 endpoints "file de département" listés ci-dessus : point d'arbitrage métier consigné
  en mémoire projet (backlog, section D) — question exacte : modèle de file partagée par rôle
  (n'importe quel CONSEILLER_JURIDIQUE peut agir sur n'importe quel dossier à cette étape) vs
  affectation nominative requise (nécessiterait un mécanisme d'octroi d'habilitation au
  routage vers le département, qui n'existe pas aujourd'hui). Pas de code sans réponse du
  métier.
- Les 6 endpoints déjà réservés CGE/CGEA/ADMIN_DDIC : aucun changement, comportement jugé
  correct.
- `create`/`registerReception` : aucun changement, actions de prise en charge sans dossier
  affecté au moment de l'appel.

## Tests

- `DossierServiceImplTest` : cas de régression — agent habilité (mock `accessGuard` retourne
  normalement) peut toujours éditer ; agent non habilité (mock `accessGuard.checkReadAccess`
  lève `BusinessException`) voit l'exception propagée et `dossierRepository.save`/
  `dossierMapper.updateEntity` jamais appelés (même style de test que
  `findById_propagatesGuardRejection`, déjà présent dans ce fichier).
