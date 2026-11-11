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

## WebSocket (Fase 4)

Conecta STOMP en `ws://localhost:8080/ws` y envía el JWT en el header nativo `Authorization` del frame `CONNECT`. El patrón de origen está en `app.websocket.allowed-origin-patterns`; para despliegues no locales, cámbialo a los orígenes permitidos.

Las rutas de alertas (`GET /api/alerts/{id}/route`) usan el servicio demo de OSRM configurado en `app.routing.osrm-base-url`; no tiene garantía de disponibilidad. Si falla, la API responde con la línea recta y `fallback: true`.

## OpenAPI, Postman y simulación (Fase 6)

- Swagger UI: `http://localhost:8080/swagger-ui.html`; especificación OpenAPI JSON: `http://localhost:8080/v3/api-docs`.
- Importa `postman/UrbTrack.postman_collection.json` en Postman. La colección crea cuentas con identificadores únicos y guarda los JWT en variables locales; no exportes una colección con tokens.
- El simulador usa solo Python 3 y la biblioteca estándar. Define `UNIT_TOKEN` con el JWT de una unidad y ejecuta:

  ```bash
  UNIT_TOKEN='<jwt-de-unidad>' python3 scripts/simulate_unit.py \
    --latitude 19.4167 --longitude -102.0667 --events 10 --interval-seconds 3
  ```

En producción se recomienda desactivar Swagger UI y OpenAPI (`springdoc.swagger-ui.enabled=false` y `springdoc.api-docs.enabled=false`) o protegerlos con la política de acceso del despliegue.

El broker STOMP simple reside en memoria solo para transporte de mensajes; el estado persistente sigue en PostgreSQL.

## Pendientes

- No hay fases obligatorias pendientes. La ruta OSRM sigue siendo un servicio demo sin garantía de disponibilidad.
