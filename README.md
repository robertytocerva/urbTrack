# UrbTrack — Backend

Backend modular para el monitoreo colaborativo de brigadas de emergencia. Requiere Java 21, Maven y PostgreSQL.

## Configuración local

1. En Neon, copia la cadena de conexión PostgreSQL. Convierte `postgresql://HOST/BASE?sslmode=require` a `jdbc:postgresql://HOST/BASE?sslmode=require`.
2. Usa el host **directo** para `DB_MIGRATION_URL`; Flyway no debe conectarse por el host pooled (`-pooler`). `DB_URL` puede ser la URL de conexión normal.
3. Copia `.env.example` a `.env` y define `DB_URL`, `DB_MIGRATION_URL` (opcional), `DB_USER`, `DB_PASSWORD` y un `JWT_SECRET` aleatorio de al menos 32 bytes. No subas `.env` al repositorio.
4. Carga las variables en la terminal/IDE (Spring no carga `.env` automáticamente) y arranca. En IntelliJ, abre **Run → Edit Configurations → UrbtrackApplication → Environment variables** y define allí las cinco variables indicadas arriba usando los valores de `.env` (sin incluir las comillas que los rodean). Deja **Working directory** en la raíz del proyecto. También puedes iniciar desde la terminal integrada de IntelliJ:

   ```bash
   set -a
   source .env
   set +a
   ./mvnw spring-boot:run
   ```

   También puedes ejecutar `./mvnw test`. Las pruebas de integración usan Testcontainers/PostgreSQL y no se conectan a Neon; se requiere Docker.

La aplicación ejecuta Flyway al arrancar y Hibernate valida el esquema existente (`ddl-auto: validate`). El health check está en `GET /actuator/health`.

## Pendientes

- Fase 4: WebSocket/STOMP y publicación de eventos de tracking/notificación en tiempo real.
- Fase 5: ruta OSRM opcional.
- Fase 6: OpenAPI, colección Postman y utilidades de simulación.
- Las ubicaciones se persisten desde Fase 2; la emisión de notificaciones en tiempo real queda para Fase 4.
