# Récépissé (Annexe B4) et sécurisation de l'upload de pièces jointes — Design

Statut : approuvé par l'utilisateur le 2026-07-30. Périmètre : suite de la fermeture
du Lot 1 (réception/enregistrement) du plan de travail ASCE-LC, plus la correction
d'une faille de sécurité découverte en cours de conception.

## Contexte

En explorant la génération du récépissé (§ci-dessous), deux lacunes ont été
constatées dans le code existant :

1. `PdfController.exportDossier` n'a aucune vérification d'habilitation par
   dossier (`@PreAuthorize("isAuthenticated()")` seulement) — n'importe quel
   agent authentifié peut télécharger le PDF de n'importe quel dossier.
2. `POST /api/v1/attachments/dossier/{dossierId}` (`AttachmentController.uploadFiles`)
   est configuré `permitAll()` dans `SecurityConfig.java` — **entièrement public,
   sans authentification**. Voulu à l'origine pour permettre à un citoyen anonyme
   (aucun compte) de joindre des preuves lors du dépôt initial, cette configuration
   ne limite en rien l'endpoint à ce cas : n'importe qui, sans être connecté, peut
   poster des fichiers sur n'importe quel dossier existant, à tout moment de son
   cycle de vie, en connaissant/devinant son UUID.

Le point 2 est un problème de sécurité réel sur un système protégeant des données
de lanceurs d'alerte, indépendant de la génération du récépissé, mais découvert
et corrigé dans le même chantier car les deux touchent `DossierAccessGuard`.

## Décisions

### 1. Génération du récépissé (Annexe B4)

- Nouvelle méthode `PdfExportService.exportRecepisse(UUID dossierId)`, même
  architecture que `exportDossier` existant (iText, layout Java, pas de moteur
  de templates).
- Contenu : numéro d'enregistrement, date/heure de réception, mode de réception,
  objet, identité du déposant ou « Anonyme » (même logique de masquage que la
  fiche dossier existante), code de suivi (`accessCode`), mentions
  institutionnelles.
- **Écart assumé** : le manuel prévoit un champ « lieu de dépôt » (§11 du
  formulaire) qui n'existe sur aucune entité aujourd'hui. Non ajouté dans ce
  chantier (ajouter un champ de schéma pour un seul document serait hors
  sujet) — le récépissé sera généré sans cette mention.
- Nouvel endpoint `GET /api/v1/pdf/recepisse/{id}`, protégé par
  `accessGuard.checkReadAccess(dossier)`.
- Centralisation des constantes institutionnelles (adresse, téléphone, email,
  site, numéro vert, slogan) dans une classe partagée utilisée par les deux
  documents PDF — corrige au passage l'incohérence du numéro vert
  (« 80 00 11 57 » dans `PdfExportService` vs « 80 00 11 11 » dans
  `EmailService`/`portal_config`).

### 2. Correction de `PdfController.exportDossier`

Ajout de `accessGuard.checkReadAccess(dossier)` avant la génération du PDF,
alignant ce contrôleur sur le reste du codebase (comme fait précédemment pour
`ObservationServiceImpl`).

### 3. Sécurisation de l'upload de pièces jointes

Nouvelle méthode `DossierAccessGuard.checkAttachmentUploadAccess(Dossier dossier)` :

- Si `dossier.getStatus()` est `SOUMIS` ou `EN_ATTENTE_COMPLEMENT` → autorisé
  sans aucune vérification. Ce sont les deux seuls moments où le citoyen,
  toujours sans compte, est censé pouvoir déposer des pièces : au dépôt
  initial, et en réponse à une demande de complément.
- Sinon → délègue à `checkReadAccess(dossier)` (authentification +
  habilitation nominative exigées, comme partout ailleurs dans le
  codebase).

Appelée dans `AttachmentStorageService.upload(String dossierId, List<MultipartFile> files)`,
juste après résolution du `Dossier`, avant toute écriture de fichier sur disque.

`SecurityConfig` reste inchangé (`permitAll()` sur
`POST /api/v1/attachments/dossier/**` toujours nécessaire pour que la requête
anonyme légitime atteigne la méthode) — c'est le service qui applique
désormais la vraie règle, pas le filtre HTTP.

Cette correction couvre aussi le besoin métier initial (agent authentifié
uploadant un formulaire papier signé/scanné comme preuve) sans nouvelle
entité ni nouvel endpoint : le type `AttachmentType.DOCUMENT` /
`AttachmentSource.INITIAL_SUBMISSION`, déjà auto-détectés par le service
existant, suffisent.

## Hors périmètre de cette spec

- Toute nouvelle entité ou endpoint dédié spécifiquement au « formulaire
  papier » — le mécanisme d'upload générique, une fois sécurisé, suffit.
- Le champ « lieu de dépôt » manquant sur `Dossier`/`Declarant`.
- Revue plus large des endpoints publics de `SecurityConfig` au-delà de
  `/api/v1/attachments/dossier/**` — seul celui-ci a été identifié comme
  problématique dans ce chantier.

## Tests

- Unitaires `DossierAccessGuard.checkAttachmentUploadAccess` : autorisé sans
  aucun mock d'authentification déclenché pour `SOUMIS` et
  `EN_ATTENTE_COMPLEMENT` ; délègue et rejette correctement pour les autres
  statuts sans habilitation active ; délègue et autorise pour les autres
  statuts avec habilitation active.
- Unitaire `AttachmentStorageService.upload` : vérifie que le nouveau garde-fou
  est appelé avant toute écriture de fichier.
- Unitaires `PdfExportService.exportRecepisse` : produit des octets non vides
  pour un dossier valide (premier test de ce type pour ce service — aucun
  test n'existe actuellement pour `PdfExportService`).
- Pas de test PDF-visuel (aucune infrastructure de ce type dans ce dépôt).
