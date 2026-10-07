# PLAN DE TRAVAIL — PLATEFORME DE GESTION DEMATERIALISEE DES DENONCIATIONS ET DES PLAINTES (ASCE-LC)

Document consolide a partir de : Introduction du manuel, Processus A, Processus B (reception et priorisation), Processus C (conduite des investigations), Processus D (reporting et communication).

Statut : les annexes B1 a B5, les annexes du Processus C et le manuel de procedures d'enquete et d'investigation du controleur d'Etat ne sont pas encore integres. Les zones concernees sont marquees [EN ATTENTE ANNEXES].

---

## 0. OBJECTIF

Dematerialiser integralement le cycle de vie d'une saisine de l'ASCE-LC, de son depot jusqu'au suivi des suites judiciaires et administratives, en garantissant par construction la confidentialite, la tracabilite et la valeur probante des elements collectes.

Le systeme n'est pas un simple GED : c'est un moteur de procedure. Chaque acte du manuel doit correspondre a une transition tracee, horodatee, signee et opposable.

---

## 1. PERIMETRE FONCTIONNEL

Saisine -> Enregistrement -> Etude d'opportunite -> Decision CTADP / CGE -> Constitution d'equipe et mandats -> Plan d'investigation -> Conduite de l'enquete -> Rapport -> Circuit de validation interne -> Transmission aux autorites -> Suivi post-rapport -> Reporting et capitalisation.

Hors perimetre a ce stade : la gestion des declarations d'interets et de patrimoine elle-meme (seul le signalement issu du DDIP entre dans le systeme), et le manuel de procedures d'enquete du controleur d'Etat en tant que corpus autonome.

---

## 2. BASE LEGALE ET REFERENTIELS A EMBARQUER

### 2.1 Textes de reference (table `TexteJuridique`, utilisee dans les visas des documents generes)

- Loi organique 082-2015 (fondatrice ASCE-LC) — art. 9, 47, 48, 55, 56, 58
- Loi 025-2018 (code penal)
- Loi 040-2019 (code de procedure penale) — art. 240-1, 242-9, 252-2, 261-17 et suivants
- Loi 004-2015 et loi 033-2018 (prevention et repression de la corruption) — art. 63, 72, 79, 90
- Loi 010-2004/AN (protection des donnees a caractere personnel) — art. 5, 21
- Loi 016-2016 (blanchiment de capitaux et financement du terrorisme)
- Loi 045-2009 (services et transactions electroniques)
- Loi 026-2018 (renseignement)
- Decret 2016-016
- Decret 2019-0237/PRES/PM/MFPTPS/MDENP du 29 mars 2019 (plaintes usagers — motif de reorientation)
- Conventions ONU 2003 et UA 2003, protocoles CEDEAO
- Normes professionnelles : ISA 315, IIA 1210-A2, COSO 1 et 2, normes canadiennes de juricomptabilite, ACFE Fraud Examiners Manual, AICPA Practice Aid 07-01, AFNOR NF X50-110

Ces references sont des donnees, jamais des libelles codes en dur : elles changeront a la prochaine modification legislative.

### 2.2 Referentiel des qualifications penales (table `TypeInfraction`, seed en migration)

Champs : `code`, `libelle`, `art_code_penal`, `art_loi_004`, `implique_ddip`, `actif`, `ordre`.

| Libelle | Code penal 025-2018 | Loi 004-2015 |
|---|---|---|
| Corruption d'agents publics | 331-1 | 42 |
| Avantages injustifies dans la commande publique | 332-1, 332-5 | 43, 44 |
| Corruption dans la commande publique | — | 45 a 47 |
| Corruption d'agents publics etrangers / organisations internationales | 332-6 | 48 |
| Soustraction de bien par un agent public | 332-7 | 49 |
| Usage et retention illicites et abusifs de biens publics | 332-8 | 50 |
| Concussion | 332-9 | 51 |
| Exonerations et franchises illegales | 332-10 | 52 |
| Trafic d'influence et abus de fonction | 332-11, 332-12 | 53, 54 |
| Surfacturation | 332-13 | 55 |
| Nepotisme et favoritisme | 332-14, 332-15 | 56, 57 |
| Commerce incompatible | 332-16 | 58 |
| Detournement de biens publics | 332-17 | 59 |
| Conflit d'interet et prise illegale d'interet | 332-18 a 332-21 | 60, 61 |
| Simulation illicite | 332-22 | 62 |
| Delit d'apparence | 332-23 | 63 |
| Enrichissement illicite | 332-24 | — |
| Delit d'inities | 332-25 | 64 |
| Defaut ou fausse declaration d'interet ou de patrimoine | 332-26 | 65 |
| Delit d'acceptation de cadeaux indus | 332-28 | 67 |
| Prise d'emploi prohibe | — | 71 |
| Blanchiment du produit du crime et recel | — | 73, 74 |

Regle : `implique_ddip = true` pour *enrichissement illicite* et *defaut ou fausse declaration* — routage automatique vers le DDIP.

### 2.3 Qualifications non penales (axe distinct de l'ecran de qualification)

- **Irregularite** : contravention a des regles ou procedures sans gain ni intention illicite. Ordre administratif, non criminel -> transmission a l'autorite hierarchique, jamais au Parquet. Ferme la branche « requete au Parquet ».
- **Fraude**, **acte de collusion**, **actes illicites** (violation de la loi, gaspillage, mauvaise gestion, abus de pouvoir) : qualifications de travail, utilisables en phase d'investigation avant requalification penale.

L'ecran de qualification porte donc deux axes : nature (administrative / penale) et qualification precise.

### 2.4 Autres referentiels administrables

- Canaux de saisine : correspondance au CGE, depot en personne au BRPD, telephone vert, courriel, site web, soit-transmis du Procureur du Faso, commission rogatoire, auto-saisine, transfert d'institution partenaire, remontee interne (DAC, DDIP), veille presse
- Institutions : deux categories aux droits distincts — **membres titulaires** du cadre de concertation (dont les ITS : orientent les saisines, peuvent participer aux missions sans conduire de forensic audit) et **observateurs** (CENTIF, BNAF, Autorite Nationale de Lutte contre la Fraude, Comite National de l'Ethique, ARCOP)
- Motifs de classement / irrecevabilite, incluant deux motifs nommes : « demande d'information » et « plainte usager relevant du decret 2019-0237 », tous deux avec reorientation
- Secteurs, types de preuve, modes d'obtention, structures internes de l'ASCE-LC
- Typologies d'indices de fraude (marches publics, postes sensibles, regies, subventions, gestion budgetaire, declarations de patrimoine)

---

## 3. ACTEURS ET ROLES

| Acteur | Role dans le systeme |
|---|---|
| Denonciateur / plaignant | Depot externe, identifie ou anonyme, suivi par code |
| Agent BRPD (greffier + 2 OPJ) | Reception, enregistrement, recepisse, recueil documentation initiale, mise en etat, **tenue des statistiques** |
| Conseiller juridique | Etude d'opportunite, revue qualite du rapport, requete et inventaire au Parquet |
| CTADP (CGEA + 5 chefs de departement + conseiller juridique) | Seance hebdomadaire, priorisation, recommandation |
| CGEA | Supervision, propose suites et affectations, responsable de la completude et de la conservation securisee, **gestionnaire du referentiel** |
| CGE | Statue **en dernier ressort**, signe, delivre les mandats, recoit aussi des saisines directes par courrier |
| Chef DEI | Valide le plan d'investigation, analyse les rapports, coordonne les enqueteurs |
| Chef de mission / enqueteurs | Conduite terrain, collecte, auditions, redaction du rapport |
| DAC | **Producteur de saisines internes** (faits decouverts en audit) + membre d'equipe de mission |
| DDIP | **Producteur de saisines internes** (soupcons issus des declarations) + membre d'equipe |
| DSRAJ | Propose la saisine judiciaire, suit les recommandations et les actions en justice |
| DIDA | **Maitre d'ouvrage delegue de la plateforme**, administrateur technique, archives |
| DCP | Veille presse, alimente le module d'information preoccupante, communication |
| DSNP | Consommateur des statistiques (promotion de la culture de la denonciation) |

Regle transversale : le cloisonnement se fait **par dossier**, jamais par role seul. L'appartenance a un role ne donne acces a aucun dossier ; l'affectation est nominative, tracee, revocable. Le secret s'impose y compris a l'egard des autres membres du personnel de l'ASCE-LC (principe directeur 9) — c'est une obligation textuelle, pas un choix d'architecture.

---

## 4. NATURES DE SAISINE — DISCRIMINANT DU MODELE

`NatureSaisine` est un discriminant avec regles de validation differenciees, pas un simple champ de qualification.

**Denonciation** — emise par une personne exterieure a l'infraction, sans prejudice direct. Nominative ou anonyme. Peut ne comporter aucune preuve. Mecanisme du lanceur d'alerte.

**Plainte** — emise par un plaignant **nominativement enregistre** ayant subi un prejudice. L'anonymat est structurellement impossible : contrainte a coder en validation. Fondement = denonciation + reconnaissance d'un prejudice.

**Signalement** — acte **officiel** emanant d'une institution de controle (ITS, IGF, services fiscaux, douaniers, Tresor, comptabilite publique), d'un responsable d'administration agissant au nom de sa structure, ou d'un bailleur de fonds. L'emetteur est une entite du referentiel institutions, pas une personne physique. Traite comme une denonciation, avec obligation pour le CGE d'informer l'emetteur des suites reservees.

**Information preoccupante** — issue de la veille (presse ecrite/parlee/televisee, ONG, REN-LAC, rapports d'audit, rumeurs, existence de projets importants). **N'ouvre pas de dossier au sens du Processus B.** Entite separee (module de veille, lot 9), avec deux issues : alimenter le fonds documentaire permanent, ou declencher une auto-saisine qui cree alors un dossier normal. Rattachement a posteriori possible a un ou plusieurs dossiers.

### 4.1 Derivation de la nature depuis le formulaire d'enregistrement

Le formulaire officiel ne comporte pas de champ « nature de saisine » : il porte un champ **qualite du deposant** a trois valeurs. La nature se derive :

| Qualite du deposant | Nature derivee | Anonymat |
|---|---|---|
| Victime | Plainte | interdit |
| Representant de la victime | Plainte | interdit |
| Temoin | Denonciation | autorise |

Regle de validation croisee : si le champ nom vaut « Anonyme », la qualite « Victime » et « Representant de la victime » sont rejetees. Inversement, selectionner l'une de ces deux qualites rend l'identification obligatoire.

> **Mise a jour 2026-10-07** : le manuel des procedures (mai 2021, sections introduction et C.2.6) et le site de l ASCE-LC admettent les plaintes anonymes. Une victime ou un representant qui ne veut pas s identifier n est donc plus refuse : le signalement est enregistre comme **denonciation** (la qualite declaree est conservee). Une plainte reste nominative. A confirmer par l ASCE-LC.

Cette derivation vaut pour les depots citoyens uniquement. Les signalements institutionnels, soit-transmis du Procureur, commissions rogatoires, remontees internes DAC/DDIP et auto-saisines n'utilisent pas ce formulaire et doivent disposer d'un ecran de saisie distinct, avec emetteur de type institution et non personne physique.

---

## 5. MODELE DE DOMAINE

**Saisine et parties**
`Saisine` (nature derivee, canal, mode de reception, date et heure de reception, lieu de depot, numero d'enregistrement annuel) — `Deposant` (nullable si anonyme ; nom ou « Anonyme », prenom, profession, telephone fixe, cellulaire, email, adresse, commune, localite, qualite) — `PersonneVisee` (polymorphe : personne physique avec nom/prenom/adresse, ou entreprise privee ou publique avec denomination/adresse) — `OrganismeDesFaits` (denomination, adresse, type : institution gouvernementale, collectivite, societe d'Etat, etablissement public, projet de developpement, autre) — `Temoin` (0..n : identite et lieu de contact) — `PieceJointe` (0..n) — `InstitutionConcernee`

**Dossier**
`Dossier` (code unique, numero au registre chronologique, statut, secteur, institution, montants en cause, financeurs, version du referentiel applicable) — `AffectationDossier` (habilitation nominative)

**Actes de reception**
`Recepisse` — `AccuseReceptionSuitesADonner` — `ReponseMotiveeDeRejet`

**Routage interne**
`FicheAffectation` (1..1 par dossier) — decision du CGE (affectation directe au CGEA pour imputation, ou echange prealable CGE / CGEA pour orientations complementaires), observations et instructions du CGE, departement competent designe par le CGEA, date d'imputation, observations du CGEA, date de retour du departement, etat d'avancement, commentaires et recommandations, double signature CGE et CGEA avec dates.
`HistoriqueImputation` (n) — un dossier peut etre reimpute ; la fiche ne doit pas ecraser l'imputation precedente.

**Instruction**
`EtudeOpportunite` (avis du conseiller juridique + grille de questions) — `SeanceCTADP` — `DecisionCTADP` — `DecisionCGE`

**Mission**
`EquipeEnquete` — `MembreEquipe` — `Mandat` — `EngagementConfidentialite` — `DeclarationConflitInterets` — `IncidentObjectivite`

**Planification**
`PlanInvestigation` — `PlanningProcedures` — `RevisionPlan` (historique complet, le plan n'est pas un cadre fige)

**Conduite**
`DemandeDocuments` (+ relance, sommation huissier, saisine judiciaire) — `VisiteTerrain` — `PVConstat` (y compris de carence) — `Audition` — `PVAudition` — `RegistreAuditions` — `ProcedureUrgence` — `MesureConservatoire`

**Preuve**
`ElementDePreuve` (type, origine, mode d'obtention volontaire/requisition, codage, force probante) — `ChainePossession` (transmissions successives) — `IndexPieces` — `CahierDePieces` — `DossierDeTravail` (arborescence normalisee)

**Restitution**
`RapportEnquete` — `NoteRecommandations` (document separe) — `RequeteParquet` — `InventairePieces` — `CircuitValidation` (etapes, delais, avis, retours motives) — `ChecklistDossierTravail` (22 points)

**Suivi**
`TransmissionAutorite` — `RelanceSuites` — `PlanActions` (entite controlee) — `NoteAvancement` — `MissionSuivi` — `ConstitutionPartieCivile` (art. 58 loi 082-2015) — `SuiviProcedurePenale`

**Capitalisation et pilotage**
`FicheRETEX` — `LeconAPartager` — `InformationPreoccupante` — `IndicateurReporting` — `TableauDeBord`

**Socle**
`Utilisateur` — `Role` — `Habilitation` — `JournalAudit` — `ModeleDocument` (versionne) — `ParametreDelai` (versionne) — `Referentiel` (versionne)

---

## 6. MACHINE A ETATS DU DOSSIER

Etat nominal :

```
RECUE
  -> ENREGISTREE
  -> TRANSMISE_CABINET_CGE
  -> IMPUTEE (decision CGE + designation du departement par le CGEA)
  -> EN_ETUDE_OPPORTUNITE
  -> EXAMINEE_CTADP
       |- CLASSEE (reponse motivee au plaignant)
       |- TRANSMISE_INSTITUTION_PARTENAIRE
       |- ORIENTEE_ADMINISTRATIF (irregularite -> autorite hierarchique)
       \- VALIDEE_POUR_INVESTIGATION
             -> EQUIPE_CONSTITUEE
             -> PLAN_VALIDE
             -> INVESTIGATION_EN_COURS
             -> RAPPORT_REDIGE
             -> REVUE_JURIDIQUE
             -> ANALYSE_DEI
             -> APPROBATION_CGEA
             -> APPROBATION_CGE
             -> TRANSMIS_AUTORITES
             -> SUIVI_POST_RAPPORT
             -> CLOTURE
```

Transitions de retour obligatoires :
- ANALYSE_DEI -> REVUE_JURIDIQUE (probleme resoluble a ce niveau)
- ANALYSE_DEI -> INVESTIGATION_EN_COURS (approfondissement demande)
- APPROBATION_CGE -> ANALYSE_DEI (non-approbation, avec remarques, justifications et recommandations)

Transitions exceptionnelles :
- Declenchement d'une procedure d'urgence (demande DEI + aval CGE) depuis tout etat d'investigation
- Remplacement d'equipe defaillante (defaillances techniques, retards, omissions, vices de procedure) sans delai apres constat de carence
- Prolongation motivee du delai d'enquete apres accord DEI + CGEA + CGE

Trois issues possibles du rapport, a modeliser explicitement :
1. Presomption confirmee avec preuves -> responsabilites etablies, sanctions recommandees
2. Presomption non confirmee (preuves insuffisantes ou non convaincantes) -> alimente obligatoirement le dossier permanent pour exploitation ulterieure
3. Non fondee -> preuves disculpant la partie visee, ou plainte malveillante avec intention de nuire, ou futile (mauvaise interpretation des faits sans intention de nuire)

---

## 7. DELAIS ET ALERTES

Tous les delais sont des parametres administrables et versionnes, jamais codes en dur.

| Etape | Delai |
|---|---|
| Reception + enregistrement + transmission CGEA | 7 jours ouvrables |
| Avis du conseiller juridique (etude d'opportunite) | 7 jours |
| Seance CTADP | hebdomadaire |
| Accuse de reception / suites a donner | 3 jours apres seance CTADP |
| Reponse motivee en cas de rejet | 3 jours ouvrables |
| Transmission a une institution partenaire | 7 jours |
| Constitution equipe + delivrance des mandats | 45 jours |
| Validation du plan d'investigation par le DEI | 8 jours ouvrables apres mandats |
| Duree d'enquete | 90 jours, prolongeable |
| Demande de documents : reponse attendue | 10 jours ouvrables |
| -> correspondance de rappel puis attente | 5 jours ouvrables |
| -> sommation par huissier puis attente | 1 jour franc |
| -> a defaut : saisine judiciaire, art. 72 loi 004-2015 | immediat |
| Revue conseiller juridique du rapport | 10 jours ouvrables |
| Analyse DEI | 15 jours ouvrables |
| Approbation CGEA | 10 jours ouvrables |
| Approbation CGE | 10 ou 20 jours ouvrables — **a arbitrer** |
| Plan d'actions de l'entite controlee | 20 jours ouvrables apres note de recommandations |
| Relance des suites donnees par les autorites | 30 jours apres transmission |
| Suivi DSRAJ | rapport trimestriel |
| Mission de suivi des plans d'actions | dans l'annee suivant l'intervention |

Mecanisme : compteur en jours ouvrables (calendrier des jours feries burkinabe administrable), alerte a J-3, alerte a echeance, escalade automatique au superieur hierarchique, tableau des dossiers en depassement par acteur.

---

## 8. EXIGENCES TRANSVERSALES NON NEGOCIABLES

### 8.1 Confidentialite et cloisonnement
- L'identite du denonciateur n'est jamais accessible a la personne mise en cause. Celle-ci n'a droit de connaitre ni le nom du denonciateur, ni celui des temoins, ni l'origine des preuves.
- Masquage par defaut, devoilement uniquement sur habilitation explicite et tracee, hors autorite judiciaire en charge du traitement penal.
- Signature electronique de l'engagement de confidentialite prealable a tout acces a un dossier — pour les membres de l'equipe **et** pour les personnes auditionnees.
- Espace archives chiffre, acces limite au CGE, au CGEA et au chef DEI.
- Les OPJ detaches au DEI ne peuvent communiquer aucun renseignement a leur hierarchie d'origine : a cabler dans le modele d'habilitation.

### 8.2 Integrite et valeur probante
- Journal d'audit inalterable sur toute lecture et toute ecriture (qui, quoi, quand, depuis ou).
- Chaine de possession numerique : empreinte du fichier, horodatage, auteur du depot, personne remettante, origine, mode d'obtention (volontaire ou requisition), codage automatique du type ACC-A-00001 selon la provenance.
- Index des pieces genere automatiquement (description, provenance, date de remise, caractere volontaire, numero de code).
- Distinction stricte original / copie de travail : l'original n'est jamais annote ni modifie.
- Versionnage de tous les documents, suppression logique uniquement — le principe directeur 10 impose la conservation de toute information, meme deja exploitee ou sans interet probant immediat.
- Hierarchie de force probante a exposer dans l'IHM : acte authentique > acte sous seing prive > document manuscrit signe > document non signe > temoignage. Et pour les sources : observation physique > documents de tiers > documents de l'organisation > informations verbales.

### 8.3 Impartialite (principe directeur 7)
L'ASCE-LC instruit a charge **et a decharge**. Consequences fonctionnelles :
- Section « elements a decharge » obligatoire et non facultative dans le PV d'audition et dans le rapport
- Point de controle dedie dans la check-list du dossier de travail
- Controle bloquant avant generation de la requete au Parquet : seuls des faits objectivement et juridiquement averes, accompagnes des moyens de preuve exiges par le CPP, peuvent etre transmis
- Interdiction de conclure sur la culpabilite : le systeme n'autorise que la formulation de presomptions de charges

### 8.4 Objectivite (principe directeur 8)
Fonctionnalite **declaration d'incident d'objectivite** : accessible a tout agent affecte a un dossier, notifie directement le CGE, tracee, permanente. Distincte de la declaration de conflit d'interets, qui est prealable a l'affectation.

### 8.5 Conformite donnees personnelles
- Loi 010-2004/AN : traitement sans consentement admis au titre de l'art. 21 (necessaire a la constatation d'une infraction ou a l'exercice d'un droit en justice) — a documenter comme base legale de chaque traitement.
- Proscrire par conception les enregistrements dissimules visant a pieger l'interlocuteur : tout enregistrement suppose accord prealable trace de la personne auditionnee.
- Elements marques « prive » dans les ressources professionnelles saisies : traitement separe, consultation conditionnee.
- Durees de conservation et archivage legal parametrables.

---

## 9. DOCUMENTS GENERES

Tous produits en PDF depuis des modeles parametrables et versionnes, avec mention « CONFIDENTIEL » et visas juridiques automatiques :

recepisse, accuse de reception / suites a donner, reponse motivee de rejet, mandat, lettre d'engagement de confidentialite, demande de documents, correspondance de rappel, sommation, PV de constat, PV d'audition, plan d'investigation, rapport d'enquete, note de recommandations, requete au Parquet, inventaire des pieces, fiche RETEX, tableaux de bord periodiques.

Contrainte specifique : l'art. 55 de la loi organique 082-2015 donne aux controleurs et enqueteurs la qualite d'OPJ, et l'art. 56 impose que leurs rapports soient dresses **sous la forme d'un proces-verbal d'enquete preliminaire**. Le generateur doit produire ce format precis avec les mentions du CPP.

Contraintes de mise en forme du rapport (C.3.9) : numerotation de chaque paragraphe, texte aere, interligne 1,5 ou 2, police de lecture papier, impression recto seul, tableaux et graphiques dans un document distinct, environ dix pages hors annexes, style a la premiere personne du pluriel, voix active, definition immediate de tout terme technique.

[EN ATTENTE ANNEXES] Maquettes exactes du recepisse (annexe B4), de l'accuse de reception (annexe B5), des formulaires d'enregistrement (annexes B2 et B3), du PV d'audition et de la lettre d'engagement de confidentialite (manuel d'enquete du controleur d'Etat).

---

## 10. GOUVERNANCE DU PARAMETRAGE

Le CGEA est responsable de la mise a jour du manuel et de la base de donnees associee. Traduction technique :
- Modeles documentaires, delais et referentiels sont versionnes
- Chaque version porte un auteur et une date d'entree en vigueur
- Un dossier reste rattache a la version applicable au moment de son ouverture
- Role « gestionnaire du referentiel » = CGEA ; administration technique = DIDA

---

## 11. LOTS DE LIVRAISON

### Lot 0 — Socle
Modele de donnees, gestion des utilisateurs, habilitations fines par dossier, journal d'audit inalterable, moteur de workflow generique, moteur de delais et notifications, GED interne avec empreintes et versionnage, generateur documentaire par modeles, chargement des referentiels (textes juridiques, qualifications penales, canaux, institutions, motifs, secteurs, typologies d'indices).

### Lot 1 — Processus B : reception et enregistrement

**Perimetre gele.** Ecran de saisie reproduisant le formulaire officiel, section par section :

1. Numero d'enregistrement, format sequence/annee, remise a zero au 1er janvier
2. Information generale du deposant : nom ou « Anonyme », prenom, profession, telephone fixe, cellulaire, email, adresse, commune, localite, date et heure de reception, lieu de depot. Saisie en majuscules imposee par le formulaire — a traiter en presentation, pas en stockage
3. Mode de reception : ecrit, telephone, email, SMS, fax, en personne
4. Qualite du deposant : victime, temoin, representant de la victime — discriminant de la nature de saisine (voir 4.1)
5. Personne visee : personne privee (nom, prenom, adresse) ou entreprise privee ou publique (denomination, adresse)
6. Organisme ou les faits ont ete perpetres : denomination, adresse
7. Attentes vis-a-vis de l'ASCE-LC : texte long
8. Temoins eventuels : bloc repetable (identite, lieu de contact)
9. Motifs de la denonciation ou plainte : texte long
10. Nature des faits : recit chronologique, description de la partie visee si inconnue, possibilite d'annexer une feuille separee
11. Existence d'une decision de justice ou d'une instance en cours : booleen + precision
12. Autre institution deja saisie : booleen + laquelle + adresse
13. Pieces ou references accompagnant la saisine : liste de pieces jointes

Les points 11 et 12 sont des **controles de recevabilite**, pas de simples informations : ils alimentent directement la grille d'etude d'opportunite du lot 2 (litispendance, competence d'une autre institution) et doivent apparaitre en evidence dans l'ecran d'analyse du conseiller juridique.

Egalement au perimetre : registre chronologique, generation du code d'identification, recepisse, recueil de la documentation initiale, analyse preliminaire d'admissibilite et avis de recevabilite, visa du formulaire, ouverture de dossier, transmission au CGEA.

**Fiche speciale d'affectation** (document distinct, meme lot) : numero de dossier, date de reception au BRPD, decision du CGE, observations et instructions du CGE, departement competent designe par le CGEA, date d'imputation, observations du CGEA, date de retour du departement, etat d'avancement, commentaires, double signature CGE et CGEA.

Regle : l'etat d'avancement de la fiche (en cours / cloture / autre) est **derive** de la machine a etats du chapitre 6, jamais saisi deux fois. La fiche est une vue imprimable de l'etat du dossier, pas une source de verite concurrente.

**Constantes institutionnelles a parametrer** (pied de page des documents generes) : 01 BP 617 Ouagadougou 01 BF, Ouaga 2000, Avenue Pascal Zagre ; telephone (00226) 25 37 40 56 ; emails info@asce-lc.bf et contact@asce-lc.bf ; site www.asce-lc.bf ; numero vert 80 00 11 02 ; slogan « Au nom de notre integrite, combattons la corruption ! ». Mention CONFIDENTIEL en en-tete de chaque page.

**Lacunes du formulaire a combler en aval, pas au depot** : date des faits (distincte de la date de reception), montants en cause, financeurs, qualification penale. Ces donnees sont exigees par le reporting D.2 mais ne peuvent etre demandees au deposant. Elles sont saisies a l'etude d'opportunite ou en cours d'investigation, et le tableau de bord doit gerer leur absence initiale.

[EN ATTENTE ANNEXES] Maquettes du recepisse (B4) et de l'accuse de reception / suites a donner (B5).

### Lot 2 — Etude d'opportunite et priorisation
Avis du conseiller juridique avec grille de questions structuree du manuel (existence d'une reelle preoccupation, competence de l'ASCE-LC ou d'une autre institution, qualification penale, suffisance des preuves, necessite d'une enquete complementaire, urgence de mise en securite des preuves, opportunite de saisir le Procureur, secteur sensible ou prioritaire, solidite de l'allegation). Convocation, ordre du jour et proces-verbal de seance CTADP. Decision CGE. Generation de l'accuse de reception / suites a donner ou de la reponse motivee.

### Lot 3 — Lancement de mission
Constitution d'equipe (chef de mission DEI + au moins deux investigateurs + personnes ressources + conseil juridique obligatoire), controle de conflit d'interets, appreciation finale et delivrance des mandats par le CGE, engagements de confidentialite, redaction du plan d'investigation et du planning avec validation DEI et historique de revisions, procedure d'urgence et mesures conservatoires.

### Lot 4 — Conduite de l'investigation
Demandes de documents avec escalade automatique (rappel, sommation, saisine judiciaire), gestion des retours pour adresse erronee avec remise a zero des delais. Visites terrain et PV de constat y compris de carence. Planification et deroulement des auditions dans l'ordre impose : denonciateur, puis temoins non impliques, puis temoins possiblement impliques, puis mis en cause en dernier ; alerte si l'ordre n'est pas respecte ; controle de la presence d'au moins deux enqueteurs ; gestion de la seconde audition, deconseillee pour le mis en cause. Redaction assistee des PV avec relecture et signature de l'auditionne, gestion des corrections justifiees. Registre confidentiel des PV au DEI. Gestion des pieces, chaine de possession, index. Dossier de travail structure selon l'arborescence normalisee : administration de la mission, prise de connaissance de l'entite, prise de connaissance de l'environnement, puis etapes ou entites ou sites ou cycles comptables.

### Lot 5 — Rapport et circuit de validation
Redaction structuree selon le plan impose : page de titre confidentielle, table des matieres, introduction et remarques preliminaires, methode suivie, description des informations collectees, resultats (expose factuel des anomalies, quantification du prejudice, reserves), conclusions, recommandations en note separee, annexes et cahier de pieces. Distinction explicite entre faits, analyse, conclusions et opinions. Check-list des 22 points du dossier de travail, bloquante avant soumission. Circuit de validation avec delais, avis et retours motives. Preparation de la requete et de l'inventaire des pieces pour le Parquet par le conseiller juridique.

### Lot 6 — Post-investigation
Transmission aux autorites administratives et judiciaires par le CGE via son secretariat et le DSRAJ. Relance formelle a 30 jours. Depot et suivi des plans d'actions par les entites controlees, notes d'avancement, missions de suivi et rapport structure (objectifs, synthese des recommandations appliquees et non appliquees avec causes, nouvelles recommandations). Suivi de la procedure penale a toutes ses phases. Constitution de partie civile au nom de l'Etat.

### Lot 7 — Processus D : reporting et pilotage
Tableaux de bord par periode (mensuel, trimestriel, semestriel, annuel) couvrant les points D.2 : volumes et tendances, modalites de saisine, resultats des priorisations, motifs et mesures prises, institutions et personnes mises en cause, montants en cause et financeurs, etat de traitement distinguant anciens et nouveaux dossiers, resultats des investigations, types et natures des recommandations. Ratios de delais moyens (enregistrement, etude d'opportunite, edition et transmission de l'accuse de reception, information des partenaires techniques et financiers, conduite des missions, analyse par conseiller juridique et DSRAJ, approbation par DEI puis CGE/CGEA, transmission des plans d'actions) et ratios de couverture (denonciations recues / accuses transmis, recues / investiguees, investigations / rapports produits, rapports / information du denonciateur). Exports pour le rapport annuel d'activite.

### Lot 8 — Portail externe
Depot en ligne, mode anonyme avec code de suivi, televersement de pieces, consultation de l'etat d'avancement, information du denonciateur ou du plaignant sur la conclusion finale. Restitution des supports de la strategie de communication D.3 (site web, campagnes SMS et telephone vert, publications).

### Lot 9 — Veille et capitalisation
Module d'information preoccupante alimente par la DCP, avec rattachement a posteriori a un ou plusieurs dossiers et declenchement possible d'auto-saisine. Fiche RETEX par mission (type d'infraction, lieu, difficultes rencontrees, origine des premiers soupcons, impact financier, originalite des schemas detectes, collaborateurs planifies, jours charges, contexte, strategie et methodes, synthese des resultats, enseignements et axes d'amelioration). Base de lecons a partager consultable. Referentiel des indices et typologies exploitable comme aide a l'enquete et comme grille de cartographie des risques.

### Lot 10 — Securisation et mise en production
Chiffrement au repos, cloisonnement reseau, politique de mots de passe et double facteur, sauvegardes, archivage, plan de reprise d'activite, tests de securite et d'intrusion, documentation, formation des utilisateurs par role.

---

## 12. ORDRE D'EXECUTION

Lot 0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7, puis 8, 9, 10.

Jalon pilote utilisable : fin du lot 5. A ce stade un dossier peut etre recu, instruit, investigue et transmis de bout en bout.
Jalon de mise en service complete : fin du lot 7.

---

## 13. POINTS A ARBITRER AVEC LE METIER (a ne pas trancher cote code)

1. **Delai d'approbation du CGE** : 10 jours ouvrables en C.3.10, 20 jours dans le tableau de synthese C.5 et dans le tableau B.3. Parametrer, pas coder en dur.
2. **Structures de communication et documentation** : l'organigramme A.2 affiche une Direction de la Documentation et de la Communication (DDC) alors que A.2.1 decrit deux directions separees, DIDA et DCP. Le referentiel des structures doit rester administrable.
3. **Sigle BRPD** : developpe « Bureau de Reception des Plaintes et des Denonciations » dans les abreviations, « Bureau des Plaintes et Denonciations » en note du Processus A. Retenir BRPD.
4. **CAEDT vs BRPD** : le Processus D mentionne un Centre d'Accueil et d'Enregistrement des Denonciations et des Plaintes (CAEDT) comme teneur du registre, la ou les Processus A et B designent le BRPD. Verifier s'il s'agit de la meme structure sous deux appellations.
5. **Ordre des etapes du circuit rapport** : C.3.10 place le conseiller juridique avant le DEI ; le tableau B.3 inverse l'ordre. Retenir la sequence detaillee de C.3.10 (conseiller juridique -> DEI -> CGEA -> CGE) sauf arbitrage contraire.
6. **Position du CGE a l'entree du circuit — divergence majeure**. Le Processus B decrit : BRPD -> CGEA -> conseiller juridique -> CTADP -> CGE. La fiche d'affectation reelle decrit : BRPD -> Cabinet du CGE -> decision du CGE -> imputation par le CGEA -> departement. La pratique fait donc intervenir le CGE **en entree** et non seulement en sortie. J'ai retenu la fiche, qui reflete l'usage, mais il faut confirmer si elle remplace le circuit du manuel ou s'y superpose. C'est le point le plus structurant restant.
7. **Departements de la fiche d'affectation** : DEI, DAC, CJ/OR, CJ/OO, BRPD. Les sigles **CJ/OR** et **CJ/OO** n'apparaissent nulle part dans le manuel, qui cite DEI, DAC, DDIP, DSRAJ, DSNP. Faire expliciter leur signification et leur articulation avec les cinq departements metiers du CTADP avant de figer le referentiel des structures.
8. **Identifiants concurrents** : le manuel prevoit un code d'identification unique permettant de retracer les etapes ; le formulaire porte un numero d'enregistrement sequence/annee. Decider s'il s'agit du meme identifiant ou de deux identifiants distincts (l'un interne et stable, l'autre communique au deposant). Recommandation : deux identifiants, le numero annuel etant celui porte sur le recepisse.
9. **Canal « site web » et numero vert** : le formulaire ne propose que six modes de reception et n'inclut pas le depot en ligne, alors que le Processus B et la strategie D.3 les prevoient. Le referentiel des canaux doit etre plus large que l'enumeration du formulaire papier ; prevoir l'extension a la mise en service du lot 8.

---

## 14. ZONES EN ATTENTE

- Annexe B1 : logigramme de traitement -> validation de la machine a etats du chapitre 6, et surtout arbitrage du point 6 du chapitre 13
- ~~Annexes B2 et B3 : formulaires d'enregistrement~~ **recues, lot 1 gele**
- Annexes B4 et B5 : modeles de recepisse et d'accuse de reception -> maquettes du generateur documentaire
- Annexes du Processus C : supports et outils de conduite des missions -> lots 3 et 4
- Manuel de procedures d'enquete et d'investigation du controleur d'Etat : PV d'audition, lettre d'engagement de confidentialite, modalites de rassemblement des preuves, elements de preuve electroniques et technico-legaux -> lots 3, 4 et 5

Ces pieces ne remettent pas en cause l'architecture ni le decoupage en lots. Elles precisent des champs et des maquettes.
