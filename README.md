# UrbTrack — Backend

Backend modular para el monitoreo colaborativo de brigadas de emergencia. Requiere Java 21, Maven y PostgreSQL.

## Configuración local

1. En Neon, copia la cadena de conexión PostgreSQL. Convierte `postgresql://HOST/BASE?sslmode=require` a `jdbc:postgresql://HOST/BASE?sslmode=require`.
2. Usa el host **directo** para `DB_MIGRATION_URL`; Flyway no debe conectarse por el host pooled (`-pooler`). `DB_URL` puede ser la URL de conexión normal.
3. Copia `.env.example` a `.env` y define `DB_URL`, `DB_MIGRATION_URL` (opcional), `DB_USER`, `DB_PASSWORD` y un `JWT_SECRET` aleatorio de al menos 32 bytes. No subas `.env` al repositorio.
4. Carga las variables en la terminal/IDE (Spring no carga `.env` automáticamente) y arranca:

   ```bash
   set -a
   source .env
   set +a
   ./mvnw spring-boot:run
   ```

   También puedes ejecutar `./mvnw test`. Las pruebas de integración usan Testcontainers/PostgreSQL y no se conectan a Neon; se requiere Docker.

La aplicación ejecuta Flyway al arrancar y Hibernate valida el esquema existente (`ddl-auto: validate`). El health check está en `GET /actuator/health`.

## Pendientes

- Fase 2: estado y ubicación de unidades, batch idempotente y búsqueda de unidades cercanas.
- Configurar las credenciales locales de Neon y confirmar la aplicación de la migración V1 en un ambiente accesible.
