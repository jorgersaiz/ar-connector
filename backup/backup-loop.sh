#!/bin/sh
# Copias automáticas mientras la aplicación está arrancada.
#
# No usa una hora fija porque el piloto corre en un PC que puede estar
# apagado de noche: hace una copia en cuanto la última automática tiene más
# de BACKUP_EVERY_HOURS horas, y lo comprueba cada pocos minutos.
set -u

DIR="${BACKUP_DIR:-/backups}"
EVERY_HOURS="${BACKUP_EVERY_HOURS:-24}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-30}"
CHECK_SECONDS="${BACKUP_CHECK_SECONDS:-600}"

trap 'exit 0' TERM INT

mkdir -p "$DIR"
echo "Copias automáticas de la base de datos: una cada $EVERY_HOURS h, se conservan $KEEP_DAYS días"

while true; do
    RECENT="$(find "$DIR" -maxdepth 1 -type f -name 'ar_connector_*_auto.dump' -mmin -"$((EVERY_HOURS * 60))" | head -n 1)"
    if [ -z "$RECENT" ]; then
        backup.sh auto || echo "[$(date '+%Y-%m-%d %H:%M:%S')] Se reintentará en $CHECK_SECONDS segundos"
    fi
    sleep "$CHECK_SECONDS" &
    wait $!
done
