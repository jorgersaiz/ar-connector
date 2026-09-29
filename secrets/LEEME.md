# Credencial de Activity Report

Deja aquí el JSON de la cuenta de servicio que envía BBVA (por Google Drive, a
los correos registrados como Requestor), por ejemplo `secrets/ar-au.json`, y
apunta a él desde el `.env`:

```
AR_CLIENT=http
AR_CREDENTIALS_FILE=/app/secrets/ar-au.json
```

La ruta es la de dentro del contenedor: Docker monta esta carpeta en
`/app/secrets` en modo de solo lectura.

- Git ignora todo lo que hay en esta carpeta salvo este fichero.
- No envíes el JSON por correo ni chat, ni lo copies a otras carpetas del
  repositorio. Contiene la clave privada con la que Arktic se identifica.
- BBVA rota la credencial cada enero. Guarda la nueva con otro nombre, cambia
  `AR_CREDENTIALS_FILE`, reinicia y, cuando compruebes que funciona, confirma
  a BBVA que puede retirar la anterior.
