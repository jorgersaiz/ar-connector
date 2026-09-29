#!/bin/sh
# Hace una copia de seguridad completa de la base de datos.
#
#   backup.sh [auto|manual|antes-de-restaurar]
#
# La copia se escribe primero en un fichero temporal y se comprueba que se
# puede leer; solo entonces pasa a ser una copia válida. Las copias antiguas
# se borran únicamente después de haber hecho una nueva con éxito, y solo las
# automáticas: las manuales se conservan hasta que alguien las borre.
set -eu

KIND="${1:-manual}"
DIR="${BACKUP_DIR:-/backups}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-30}"

case "$KIND" in
    auto|manual|antes-de-restaurar) ;;
    *) echo "Tipo de copia desconocido: $KIND (auto, manual o antes-de-restaurar)"; exit 2 ;;
esac

mkdir -p "$DIR"
FILE="$DIR/ar_connector_$(date +%Y-%m-%d_%H%M%S)_$KIND.dump"
TMP="$FILE.tmp"

if ! pg_dump --format=custom --no-owner --file="$TMP"; then
    rm -f "$TMP"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: no se ha podido hacer la copia de seguridad"
    exit 1
fi
if ! pg_restore --list "$TMP" > /dev/null; then
    rm -f "$TMP"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: la copia generada no se puede leer; se descarta"
    exit 1
fi
mv "$TMP" "$FILE"
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Copia hecha: $(basename "$FILE") ($(du -h "$FILE" | cut -f1))"

find "$DIR" -maxdepth 1 -type f -name 'ar_connector_*_auto.dump' -mtime +"$KEEP_DAYS" | while read -r OLD; do
    rm -f "$OLD"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Copia antigua borrada: $(basename "$OLD")"
done
