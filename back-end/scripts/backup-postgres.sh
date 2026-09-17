#!/bin/sh
set -eu

# Sauvegarde automatisee de la base Postgres ASCE-LC (Lot 10 sous-chantier 3/3).
# Lance un pg_dump compresse immediatement, puis toutes les 24h, et supprime
# les sauvegardes de plus de RETENTION_DAYS jours.
#
# Restauration :
#   gunzip -c /backups/bd_univers_audit_<timestamp>.sql.gz | \
#     PGPASSWORD="$DB_PASSWORD" psql -h postgres -U postgres -d bd_univers_audit

BACKUP_DIR=/backups
RETENTION_DAYS=30
DB_HOST=postgres
DB_NAME=bd_univers_audit
DB_USER=postgres

mkdir -p "$BACKUP_DIR"

while true; do
  TIMESTAMP=$(date +%Y%m%d_%H%M%S)
  FILE="$BACKUP_DIR/${DB_NAME}_${TIMESTAMP}.sql.gz"
  TMPFILE="$BACKUP_DIR/.tmp_${DB_NAME}_${TIMESTAMP}.sql"

  echo "[backup] $(date -Iseconds) - demarrage sauvegarde vers $FILE"

  # pg_dump vers un fichier temporaire non compresse, hors pipe, pour pouvoir
  # verifier son code de sortie directement (dans "pg_dump | gzip > f", seul
  # le code de sortie de gzip est teste par "if", pas celui de pg_dump).
  if PGPASSWORD="$DB_PASSWORD" pg_dump -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" > "$TMPFILE"; then
    gzip -c "$TMPFILE" > "$FILE"
    rm -f "$TMPFILE"
    echo "[backup] $(date -Iseconds) - sauvegarde reussie ($(du -h "$FILE" | cut -f1))"
  else
    echo "[backup] $(date -Iseconds) - ECHEC de la sauvegarde" >&2
    rm -f "$TMPFILE" "$FILE"
  fi

  find "$BACKUP_DIR" -name "${DB_NAME}_*.sql.gz" -mtime "+$RETENTION_DAYS" -delete

  sleep 86400
done
