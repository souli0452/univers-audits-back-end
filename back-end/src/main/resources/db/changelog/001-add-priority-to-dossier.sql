ALTER TABLE dossier
    ADD COLUMN IF NOT EXISTS priority              VARCHAR(20)  DEFAULT 'NORMAL',
    ADD COLUMN IF NOT EXISTS priority_reason       TEXT,
    ADD COLUMN IF NOT EXISTS priority_deadline     TIMESTAMP,
    ADD COLUMN IF NOT EXISTS priority_set_at       TIMESTAMP,
    ADD COLUMN IF NOT EXISTS priority_set_by_id    UUID REFERENCES agent(id);

CREATE INDEX IF NOT EXISTS idx_dossier_priority
    ON dossier(priority)
    WHERE priority IN ('CRITIQUE','URGENT');

COMMENT ON COLUMN dossier.priority           IS 'CRITIQUE / URGENT / NORMAL / FAIBLE';
COMMENT ON COLUMN dossier.priority_reason    IS 'Motif (obligatoire si != NORMAL)';
COMMENT ON COLUMN dossier.priority_deadline  IS 'Date d''échéance souhaitée par l''agent';
COMMENT ON COLUMN dossier.priority_set_at    IS 'Horodatage de la définition';
COMMENT ON COLUMN dossier.priority_set_by_id IS 'Agent ayant défini la priorité';