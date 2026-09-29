# Copias de seguridad de la base de datos: mismas herramientas (pg_dump, pg_restore)
# y misma versión que el servidor PostgreSQL.
FROM postgres:16-alpine
COPY backup.sh restore.sh backup-loop.sh /usr/local/bin/
# En Windows git puede convertir los saltos de línea a CRLF, y un script con CRLF no arranca
RUN sed -i 's/\r$//' /usr/local/bin/backup.sh /usr/local/bin/restore.sh /usr/local/bin/backup-loop.sh \
    && chmod +x /usr/local/bin/backup.sh /usr/local/bin/restore.sh /usr/local/bin/backup-loop.sh
ENTRYPOINT []
CMD ["backup-loop.sh"]
