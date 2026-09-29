#!/bin/sh
# Restaura la base de datos desde una copia de seguridad.
#
#   restore.sh                      lista las copias disponibles
#   restore.sh <fichero> [--si]     restaura esa copia (--si: sin pedir confirmación)
#
# Es seguro ante fallos: la copia se restaura en una base de datos nueva y solo
# si termina bien sustituye a la actual. Antes de sustituirla se guarda una
# copia del estado actual ("antes-de-restaurar"), por si hay que volver atrás.
#
# Después hay que reiniciar el backend:  docker compose restart backend
set -eu

DIR="${BACKUP_DIR:-/backups}"
DB="$PGDATABASE"
NEW="${DB}_restaurando"
OLD="${DB}_anterior"

list() {
    echo "Copias disponibles en la carpeta de copias (la más reciente, primero):"
    FOUND="$(find "$DIR" -maxdepth 1 -type f -name 'ar_connector_*.dump' | sort -r)"
    if [ -z "$FOUND" ]; then
        echo "  (ninguna)"
        return
    fi
    echo "$FOUND" | while read -r F; do
        echo "  $(basename "$F")   $(du -h "$F" | cut -f1)"
    done
}

if [ $# -eq 0 ]; then
    list
    echo
    echo "Para restaurar una:  docker compose run --rm backup restore.sh <fichero>"
    exit 0
fi

FILE="$DIR/$(basename "$1")"
if [ ! -f "$FILE" ]; then
    echo "No existe la copia $(basename "$1")"
    echo
    list
    exit 1
fi
if ! pg_restore --list "$FILE" > /dev/null; then
    echo "ERROR: $(basename "$FILE") no es una copia válida o está dañada. No se ha tocado nada."
    exit 1
fi

if [ "${2:-}" != "--si" ]; then
    echo "Se va a sustituir TODA la base de datos por el contenido de $(basename "$FILE")."
    echo "Lo que se haya hecho después de esa copia dejará de verse en la aplicación"
    echo "(se guarda antes una copia del estado actual)."
    printf "Escribe RESTAURAR para continuar: "
    read -r ANSWER || ANSWER=""
    if [ "$ANSWER" != "RESTAURAR" ]; then
        echo "Cancelado. No se ha tocado nada."
        exit 1
    fi
fi

sql() {
    psql --dbname=postgres --quiet --no-psqlrc --set=ON_ERROR_STOP=1 --command="$1"
}

echo "1/4 Copia del estado actual"
backup.sh antes-de-restaurar

echo "2/4 Restaurando en una base de datos nueva"
sql "DROP DATABASE IF EXISTS \"$NEW\" WITH (FORCE)"
sql "CREATE DATABASE \"$NEW\""
if ! pg_restore --no-owner --exit-on-error --dbname="$NEW" "$FILE"; then
    sql "DROP DATABASE IF EXISTS \"$NEW\" WITH (FORCE)"
    echo "ERROR: la restauración ha fallado. La base de datos actual sigue intacta."
    exit 1
fi

echo "3/4 Sustituyendo la base de datos actual"
sql "DROP DATABASE IF EXISTS \"$OLD\" WITH (FORCE)"
sql "ALTER DATABASE \"$DB\" WITH ALLOW_CONNECTIONS false"
sql "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DB' AND pid <> pg_backend_pid()" > /dev/null
if ! sql "ALTER DATABASE \"$DB\" RENAME TO \"$OLD\""; then
    sql "ALTER DATABASE \"$DB\" WITH ALLOW_CONNECTIONS true"
    sql "DROP DATABASE IF EXISTS \"$NEW\" WITH (FORCE)"
    echo "ERROR: no se ha podido sustituir la base de datos. La actual sigue intacta."
    exit 1
fi
if ! sql "ALTER DATABASE \"$NEW\" RENAME TO \"$DB\""; then
    sql "ALTER DATABASE \"$OLD\" RENAME TO \"$DB\""
    sql "ALTER DATABASE \"$DB\" WITH ALLOW_CONNECTIONS true"
    echo "ERROR: no se ha podido sustituir la base de datos. Se ha dejado la que había."
    exit 1
fi

echo "4/4 Limpieza"
sql "DROP DATABASE IF EXISTS \"$OLD\" WITH (FORCE)"

echo
echo "Restaurada la copia $(basename "$FILE")."
echo "Ahora reinicia el backend:  docker compose restart backend"
