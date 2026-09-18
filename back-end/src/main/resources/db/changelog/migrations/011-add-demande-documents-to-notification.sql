--liquibase formatted sql
--changeset dev:011-add-demande-documents-to-notification

-- FK nullable pour dedupliquer correctement les alertes de DemandeDocuments
-- (une investigation peut avoir plusieurs demandes de documents concurrentes
-- non recues ; existsByDossierIdAndType seul supprimerait a tort l'alerte
-- d'une deuxieme demande). Nulle pour tous les types d'alerte existants.
-- Voir docs/superpowers/specs/2026-09-18-alertes-delai-j3-design.md.

ALTER TABLE notification
    ADD COLUMN demande_documents_id UUID;

ALTER TABLE notification
    ADD CONSTRAINT fk_notification_demande_documents
        FOREIGN KEY (demande_documents_id) REFERENCES demande_documents(id);
