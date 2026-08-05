# Porte EQUIPE_CONSTITUEE/PLAN_VALIDE dans start() (Lot 3, sous-chantier 6/6) — Design

Statut : approuvé par l'utilisateur le 2026-08-05. Sixième et dernier sous-chantier du
Lot 3 (Lancement de mission), §5/§6/§11 du plan de travail
(`docs/reference/plan-de-travail-asce-lc.md`). Les sous-chantiers 1/6 à 5/6 sont
livrés et mergés :
1. Constitution d'équipe + Mandat — livré
2. Engagement de confidentialité + Déclaration de conflit d'intérêts — livré
3. Plan d'investigation — livré
4. Incident d'objectivité — livré
5. Procédure d'urgence + mesures conservatoires — livré
6. Insertion effective de la porte `EQUIPE_CONSTITUEE`/`PLAN_VALIDE` (ce document)

C'était le sous-chantier identifié comme le plus risqué du Lot dès la décomposition
initiale (touche un flux `open()`/`start()` déjà fonctionnel), volontairement gardé
pour la fin. L'exploration ci-dessous réduit considérablement ce risque par rapport à
ce qui était anticipé.

## Contexte

**Découverte clé** : le `DossierStatus` réel de ce dépôt est bien plus grossier que le
texte littéral de §6, qui liste `EQUIPE_CONSTITUEE`, `PLAN_VALIDE`,
`INVESTIGATION_EN_COURS`, `RAPPORT_REDIGE`, `REVUE_JURIDIQUE`, `ANALYSE_DEI`,
`APPROBATION_CGEA`, `APPROBATION_CGE` comme des états séparés de la machine à états.
L'enum réel (`enums/DossierStatus.java`) compresse tout cela en
`RECEVABLE → EN_INVESTIGATION → RAPPORT_PRODUIT → DECISION_RENDUE → CLOS` — et les
sous-étapes granulaires du rapport (validation DEI, conseiller juridique, CGE) sont
**déjà** suivies via des champs directs sur `Investigation`
(`deiApprovedAt`/`legalAdvisorApprovedAt`/`cgeApprovedAt`), jamais par de nouveaux
`DossierStatus`. Vérifié : ces 4 valeurs (`RAPPORT_PRODUIT`, `DECISION_RENDUE`
comprises) suffisent à couvrir toute la séquence d'approbation existante, malgré sa
complexité comparable (voire supérieure) à celle de la constitution d'équipe.

**Décision utilisateur (2026-08-05)** : appliquer le même patron déjà éprouvé plutôt
que d'ajouter de nouveaux `DossierStatus`. `EQUIPE_CONSTITUEE`/`PLAN_VALIDE` ne
deviennent pas des valeurs d'enum — ce sont des conditions vérifiées au niveau
`Investigation`, exactement comme les approbations du rapport.

**État déjà couvert par les sous-chantiers précédents** (vérifié en lisant
`InvestigationServiceImpl.start()` tel qu'il existe aujourd'hui) :
- Composition d'équipe complète (1 `CHEF_MISSION`, ≥2 `INVESTIGATEUR`, 1
  `CONSEIL_JURIDIQUE`) — vérifiée depuis le sous-chantier 1/6
  (`validateTeamComposition`).
- Mandat délivré par le CGE — vérifié depuis le sous-chantier 1/6.

**Ce qui manque encore, et que ce sous-chantier ajoute** : la validation DEI du plan
d'investigation (sous-chantier 3/6, `PlanInvestigation.validatedAt`) n'est **pas**
actuellement vérifiée par `start()` — c'était explicitement différé à ce
sous-chantier 6/6 lors de la conception du sous-chantier 3/6.

**`open()` — décision utilisateur** : `Dossier.status` passe déjà de `RECEVABLE` à
`EN_INVESTIGATION` dès la **création** de l'investigation (dans `open()`), avant même
que l'équipe soit formée — c'est le « trou structurel » noté à l'origine, avant la
décomposition du Lot 3. Décision : **ne pas déplacer cette transition vers `start()`**.
`EN_INVESTIGATION` couvre toute la phase d'enquête de bout en bout (« ouverte mais pas
démarrée » vs « en cours » est déjà distingué au niveau `InvestigationStatus`
(`INITIATED` vs `IN_PROGRESS`), pas besoin de dupliquer cette distinction au niveau
`Dossier`). 9 fichiers référencent déjà `EN_INVESTIGATION`
(`PdfExportService`, `EmailService`, `DossierRepository`, `StatistiqueServiceImpl`,
`ConfigController`, `Dossier.java`, `DossierServiceImpl`, `InvestigationServiceImpl`,
`DossierStatus.java`) — déplacer la transition exigerait d'auditer chacun pour
vérifier s'il suppose « l'investigation existe » ou « le travail de terrain a
effectivement démarré », un risque de régression large pour un gain limité au regard
du texte, qui ne distingue pas non plus explicitement ces deux moments pour les autres
transitions déjà compressées (cf. le rapport).

## Décision

### Nouvelle précondition dans `start()`

En plus des deux vérifications déjà en place, `start()` exige désormais qu'un
`PlanInvestigation` existe pour l'investigation **et** qu'il soit validé :

- Rejette avec un message distinct si aucun `PlanInvestigation` n'existe encore
  (« Aucun plan d'investigation n'a été soumis pour cette investigation. »).
- Rejette avec un message distinct si un plan existe mais `validatedAt == null`
  (« Le plan d'investigation n'a pas encore été validé par le DEI. »).
- Ordre des vérifications : composition d'équipe → mandat → plan validé (ordre
  chronologique naturel : équipe formée → mandat délivré → plan soumis, qui exige déjà
  un mandat depuis le sous-chantier 3/6 → plan validé par le DEI → démarrage).

Aucun autre changement à `start()`, `open()`, `Investigation`, ou `Dossier`.

## Hors périmètre

- Tout nouveau `DossierStatus` (`EQUIPE_CONSTITUEE`, `PLAN_VALIDE` ou équivalent) —
  décision utilisateur, voir Contexte.
- Déplacement de la transition `Dossier.status → EN_INVESTIGATION` de `open()` vers
  `start()` — décision utilisateur, voir Contexte.
- Toute modification du comportement de `open()`.
- Tout ajout de champ ou de méthode sur `PlanInvestigation`/`Mandat`/
  `InvestigationMember` — les préconditions utilisent uniquement ce qui existe déjà.

## Tests

- `InvestigationServiceImplTest` : `start()` — nouveau rejet si aucun plan
  d'investigation n'existe (composition + mandat valides), nouveau rejet si le plan
  existe mais n'est pas validé. Les deux tests de succès existants
  (`start_succeedsWithFullCompositionAndMandat`,
  `start_succeedsWithExtraPersonneRessource`) doivent être mis à jour avec un nouveau
  stub `planInvestigationRepository.findByInvestigationId(...)` retournant un plan
  validé, sinon ils échoueront après ce changement — comportement attendu et
  documenté, pas une régression.
