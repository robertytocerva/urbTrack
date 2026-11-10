# AGENTS.md — Backend: Monitoreo Colaborativo de Brigadas de Emergencia

> Léelo completo antes de escribir código. Si algo de aquí contradice una suposición tuya, gana este documento.

## 1. Contexto del proyecto

Proyecto de la materia **Sistemas Distribuidos** (ITS Uruapan). Plataforma tipo "Uber de emergencias":

- **Ciudadano (`CITIZEN`)**: ve en un mapa las unidades cercanas dentro de un radio, genera una alerta (robo, incendio, médica, accidente...) y, cuando una unidad la acepta, ve en tiempo real el recorrido de esa unidad hacia él.
- **Unidad (`UNIT`)**: patrulla, bomberos, ambulancia o protección civil. Reporta su ubicación, recibe ofertas de alertas cercanas, acepta/rechaza, y al aceptar recibe la ruta al ciudadano.
- **Admin (`ADMIN`)**: rol opcional para un futuro panel de control. No priorizar.

**Alcance: SOLO backend.** El frontend/app móvil es de otra persona (o se prueba con Postman / un cliente WebSocket). No generes UI.

## 2. Decisiones de arquitectura (no cambiar)

- **Monolito modular** en Spring Boot. Un solo ejecutable, una sola BD. **Prohibido** introducir microservicios, Kafka, RabbitMQ, Redis o Kubernetes.
- **Orientado a eventos dentro del monolito**: los cambios (nueva ubicación, alerta creada, alerta aceptada...) se publican con `ApplicationEventPublisher` y se consumen con `@TransactionalEventListener(phase = AFTER_COMMIT)`. Los listeners empujan mensajes por WebSocket.
- **Tiempo real con WebSocket + STOMP** (`/ws`). REST para comandos y consultas; WebSocket solo para notificar.
- **Las unidades solo transmiten cuando hay cambio real** (movimiento > ~20 m o cambio de estatus), o cada pocos segundos si hay una alerta activa.
- **Tolerancia a fallos del cliente**: las ubicaciones tienen `event_id` (UUID generado en el móvil) y `recorded_at` (hora real de captura). El endpoint batch es **idempotente** para soportar la cola offline del móvil.

## 3. Stack y dependencias

**Versiones fijas:** Java **21**, Spring Boot **4.1.2** (Spring Framework 7), Maven.

### Dependencias iniciales (ya elegidas en Spring Initializr)

| Dependencia          | Uso en este proyecto |
|----------------------|----------------------|
| Spring Data JPA      | Persistencia con JPA/Hibernate sobre PostgreSQL |
| PostgreSQL Driver    | Driver JDBC para conectarse a PostgreSQL (Neon) |
| Spring Web           | API REST (Spring MVC, Tomcat embebido) |
| Spring Security      | Autenticación (JWT) y control de acceso por rol |
| Flyway Migration     | Control de versiones del esquema de la BD |
| Lombok               | Reducir boilerplate (herramienta de desarrollo) |

### Dependencias adicionales (agrégalas SOLO en la fase que las necesite y menciónalo en tu resumen)

- `spring-boot-starter-actuator` (Fase 0: health check)
- `spring-boot-starter-validation` (Fase 1: Bean Validation en DTOs)
- Librería JWT (Fase 1): `spring-boot-starter-oauth2-resource-server` **o** JJWT. Elige una y verifica compatibilidad con Boot 4 / Jackson 3.
- Testcontainers + PostgreSQL y starters de test (Fase 1)
- `spring-boot-starter-websocket` (Fase 4)
- `springdoc-openapi` (Fase 6, opcional; usar la línea de versiones compatible con Boot 4)

Si una dependencia adicional no está en esta lista, justifícala antes de agregarla.

### Notas específicas de Spring Boot 4.x (no asumes APIs de Boot 3; ante duda consulta la documentación oficial de 4.1.x)

- Los starters son modulares. Respeta los que genera Initializr (p. ej. el de web MVC y `spring-boot-starter-flyway`). Si Flyway no trae el módulo de PostgreSQL, agrega `flyway-database-postgresql`.
- Jackson 3: los paquetes de `ObjectMapper` y similares son `tools.jackson.*` (las anotaciones siguen en `com.fasterxml.jackson.annotation`).
- Spring Security 7 y Hibernate 7: configuración de seguridad con `SecurityFilterChain` y lambda DSL; no existe `WebSecurityConfigurerAdapter`.
- En tests, `@MockBean` fue reemplazado por `@MockitoBean`; los starters de test son por módulo (web MVC, data JPA, security).
- Lombok: en entidades JPA **no uses `@Data`**; usa `@Getter`, `@Setter`, `@NoArgsConstructor` y define `equals/hashCode` con cuidado (por id). Los DTOs son `record` y no necesitan Lombok. Verifica que el annotation processor de Lombok esté configurado en el `pom.xml`.
- Tests: JUnit 5, MockMvc, Testcontainers (PostgreSQL).

Paquete base: `mx.itsu.emergencias`

## 4. Estructura de paquetes (por módulo, no por capa global)

```
mx.itsu.emergencias
├── auth/        # registro, login, JWT, filtros
├── user/        # entidad User, roles
├── unit/        # Unit, UnitLocation, estado, búsqueda de cercanas, batch de ubicaciones
├── alert/       # Alert, máquina de estados, creación, cancelación, cierre
├── dispatch/    # AlertDispatch, algoritmo de asignación, aceptación atómica, expiración
├── tracking/    # historial de ubicaciones, tracking por alerta
├── routing/     # cliente de rutas (OSRM) con fallback  [fase opcional]
├── realtime/    # config STOMP, publicadores de eventos WS, seguridad del canal
└── common/      # excepciones, ApiError, utilidades geo (Haversine), config
```

Dentro de cada módulo: `api` (controllers + DTOs), `domain` (entidades), `service`, `repo`. Los controllers **nunca** devuelven entidades JPA, solo DTOs (records).

## 5. Modelo de datos

El esquema ya está definido en `src/main/resources/db/migration/V1__esquema_inicial.sql`.
**No uses `ddl-auto=update/create`**; usa `validate`. Cualquier cambio de esquema = nueva migración `V2__...`, `V3__...`. **Nunca edites una migración ya aplicada.**

Tablas: `users`, `units`, `unit_locations` (última ubicación, 1 fila por unidad), `unit_location_history`, `alerts`, `alert_dispatches`, `alert_events` (bitácora de eventos).

## 6. Reglas de dominio

### Tipos de alerta → unidad requerida

| AlertType  | Unidades que reciben la oferta        |
|------------|---------------------------------------|
| ROBBERY    | POLICE                                |
| FIRE       | FIRE (y opcionalmente CIVIL_PROTECTION) |
| MEDICAL    | AMBULANCE                             |
| ACCIDENT   | AMBULANCE y POLICE (la primera que acepte gana; ver nota) |
| OTHER      | CIVIL_PROTECTION                      |

Nota: para ACCIDENT en esta versión basta con una sola unidad asignada (la primera en aceptar). Multi-unidad es mejora futura.
Guardar el resultado en `alerts.required_unit_type` (para ACCIDENT usar `AMBULANCE`; mantenerlo simple).

### Estados de la alerta

```
PENDING ──accept──► EN_ROUTE ──arrive──► ON_SCENE ──resolve──► RESOLVED
   │                    │
   ├─cancel(ciudadano)──┴──► CANCELLED
   └─sin unidades tras N rondas──► EXPIRED
```

Transiciones inválidas → `409 Conflict`. Cada transición inserta una fila en `alert_events` y publica un evento de dominio.

### Algoritmo de despacho

1. Al crear una alerta (`PENDING`): buscar unidades `AVAILABLE`, del tipo requerido, con ubicación actualizada en los últimos **2 minutos**, dentro del radio de la ronda (ronda 1 = 5 km, ronda 2 = 10 km, ronda 3 = 20 km; configurable en `application.yml`).
2. Ordenar por distancia y tomar las **N = 3** más cercanas que no tengan ya una oferta de esta alerta.
3. Crear `alert_dispatches` en estado `OFFERED` con `expires_at = now + 30 s` y notificar a cada unidad por WebSocket.
4. Un `@Scheduled` (cada 5–10 s) marca ofertas vencidas como `EXPIRED`; si no quedan ofertas vivas y la alerta sigue `PENDING`, lanza la siguiente ronda. Tras la última ronda sin aceptación → alerta `EXPIRED` y se notifica al ciudadano.

### Aceptación concurrente (CRÍTICO: es el punto fuerte para la materia)

Dos unidades pueden aceptar al mismo tiempo. Solo una debe ganar. Hacerlo **atómico en BD**:

```sql
UPDATE alerts
   SET status = 'EN_ROUTE', assigned_unit_id = :unitId, accepted_at = now(), version = version + 1
 WHERE id = :alertId AND status = 'PENDING';
```

- Si afecta 1 fila → ganó: marcar su dispatch `ACCEPTED`, los demás `CANCELLED`, unidad → `BUSY`, publicar evento.
- Si afecta 0 filas → `409 Conflict` ("La alerta ya fue asignada").
- Respaldos a nivel BD ya existentes: índice único parcial que impide que una unidad tenga 2 alertas activas, y que un ciudadano tenga 2 alertas activas.
- Escribir un **test de concurrencia** (varios hilos aceptando la misma alerta; exactamente 1 éxito).

### Búsqueda de unidades cercanas

Usar **bounding box + Haversine** en consulta nativa (sin PostGIS por ahora). Esquema de la consulta:

```sql
SELECT u.id, u.unit_type, l.latitude, l.longitude,
       2 * 6371000 * asin(sqrt(
         power(sin(radians(l.latitude - :lat) / 2), 2) +
         cos(radians(:lat)) * cos(radians(l.latitude)) *
         power(sin(radians(l.longitude - :lon) / 2), 2)
       )) AS distance_m
  FROM units u JOIN unit_locations l ON l.unit_id = u.id
 WHERE u.status = 'AVAILABLE'
   AND l.updated_at > now() - interval '2 minutes'
   AND l.latitude  BETWEEN :minLat AND :maxLat
   AND l.longitude BETWEEN :minLon AND :maxLon
 ORDER BY distance_m
 LIMIT :n;
```

(`minLat/maxLat/minLon/maxLon` se calculan en Java a partir del radio.) Para el ciudadano, el endpoint de unidades cercanas **no debe exponer** `user_id`, nombre ni datos personales: solo `unitId` anónimo, tipo, lat, lon y heading.

### Ubicaciones

- `POST /api/units/me/location` actualiza `unit_locations` (upsert) e inserta en `unit_location_history`.
- Si el `recorded_at` recibido es **más viejo** que el que ya está en `unit_locations`, se guarda en historial pero **no** sobreescribe la ubicación actual.
- `event_id` duplicado → responder `200` idempotente (no error, no duplicar).
- Validar rangos: lat ∈ [-90, 90], lon ∈ [-180, 180]; rechazar `recorded_at` en el futuro (> now + 1 min).
- Si la unidad tiene alerta `EN_ROUTE`, publicar evento de tracking a `/topic/alerts/{alertId}`.

## 7. API REST

Todas bajo `/api`, JSON, errores con formato `ApiError {timestamp, status, code, message, path}`. Auth con `Authorization: Bearer <jwt>`.

**Auth** (públicos)
- `POST /api/auth/register/citizen`
- `POST /api/auth/register/unit` (body incluye `unitType` e `identifier`; en esta versión se permite el auto-registro, solo para desarrollo)
- `POST /api/auth/login` → `{accessToken, role, userId}`
- `GET  /api/me`

**Ciudadano** (`ROLE_CITIZEN`)
- `GET  /api/units/nearby?lat=&lon=&radiusKm=` → unidades disponibles cercanas (anónimas)
- `POST /api/alerts` `{type, description, latitude, longitude}` → crea y despacha
- `GET  /api/alerts/active` → alerta activa del ciudadano (con unidad asignada y su última ubicación)
- `POST /api/alerts/{id}/cancel`
- `GET  /api/alerts/{id}/route` → ruta de la unidad al ciudadano (fase opcional)

**Unidad** (`ROLE_UNIT`)
- `PUT  /api/units/me/status` `{status: AVAILABLE|OFFLINE}` (BUSY lo gestiona el sistema)
- `POST /api/units/me/location` `{eventId, latitude, longitude, heading?, speedKmh?, recordedAt}`
- `POST /api/units/me/locations/batch` `[ {...}, {...} ]` (cola offline)
- `GET  /api/dispatches/pending` → ofertas vigentes (por si perdió el WS)
- `POST /api/dispatches/{id}/accept` → 200 con datos de la alerta + ruta | 409 si ya fue tomada
- `POST /api/dispatches/{id}/reject`
- `POST /api/alerts/{id}/arrive` → `ON_SCENE`
- `POST /api/alerts/{id}/resolve` → `RESOLVED`, unidad → `AVAILABLE`

**Admin** (`ROLE_ADMIN`, baja prioridad)
- `GET /api/admin/units`, `GET /api/admin/alerts?status=`

## 8. WebSocket (STOMP)

- Endpoint: `/ws` (con SockJS opcional). Broker simple en memoria: prefijos `/topic` y `/queue`; destinos de usuario `/user/...`; app prefix `/app`.
- **Autenticación en el frame `CONNECT`** con el header `Authorization: Bearer <jwt>` (interceptor de canal). Conexión sin token válido → rechazada.
- **Autorización de suscripción** (interceptor): un ciudadano solo puede suscribirse a `/topic/alerts/{id}` de **su** alerta; una unidad solo a las alertas que tiene asignadas.

| Destino                       | Quién escucha | Contenido |
|-------------------------------|---------------|-----------|
| `/user/queue/dispatches`      | Unidad        | Nueva oferta de alerta (`DispatchOffer`) o cancelación de oferta |
| `/user/queue/alerts`          | Ciudadano     | Cambios de estado de su alerta (asignada, en sitio, resuelta, expirada) |
| `/topic/alerts/{alertId}`     | Ciudadano (y unidad asignada) | `UnitLocationUpdate` mientras la alerta está `EN_ROUTE` + cambios de estado |

Los mapas de "unidades cercanas" se refrescan con **polling REST cada 5–10 s**; no hace falta un topic por zona.

## 9. Seguridad

- Contraseñas con BCrypt. JWT firmado con HS256, secreto desde variable de entorno `JWT_SECRET` (≥ 32 bytes), expiración 1 h (configurable).
- Sin estado (`STATELESS`), CSRF deshabilitado para la API, CORS configurable.
- Autorización por rol con `@PreAuthorize` o reglas en `SecurityFilterChain`.
- Nunca loguear tokens ni contraseñas. Nunca exponer datos personales de una unidad al ciudadano más allá de lo necesario una vez asignada (tipo, identificador de unidad, ubicación).
- Rate-limit simple (opcional) en `POST /api/alerts` para evitar abuso (ya hay índice que limita a 1 alerta activa por ciudadano).

## 10. Configuración y base de datos (Neon)

### Base de datos

La BD es **PostgreSQL administrado en Neon** (serverless). **No** se usa Docker para la BD de desarrollo. Neon entrega una cadena de conexión con este formato:

```
postgresql://USUARIO:PASSWORD@ep-xxxx.region.aws.neon.tech/NOMBRE_BD?sslmode=require
```

Spring necesita formato **JDBC** y usuario/contraseña por separado:

```
DB_URL=jdbc:postgresql://ep-xxxx.region.aws.neon.tech/NOMBRE_BD?sslmode=require
DB_USER=usuario
DB_PASSWORD=password
```

Reglas:

- **Nunca** escribas credenciales reales en el código, en `application.yml`, en el README ni en logs. Todo por variables de entorno: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (y opcional `DB_MIGRATION_URL`).
- Crea un `.env.example` con valores de ejemplo falsos y agrega `.env` y `application-local.yml` al `.gitignore`. El desarrollador carga los valores reales en su IDE o entorno.
- **Conexión directa vs. pooled:** Neon ofrece un host "pooled" (contiene `-pooler`, usa PgBouncer) y uno directo. **Flyway debe usar la conexión directa** (los bloqueos que usa Flyway no son confiables con el pooler). Por eso existe `DB_MIGRATION_URL`: si no se define, se usa `DB_URL`. Si solo tienes una URL, usa la directa.
- **Autosuspensión de Neon:** el cómputo se suspende por inactividad y la primera conexión puede tardar. Mantén el pool de Hikari pequeño, con `minimum-idle: 0` y un `connection-timeout` generoso, para no fallar en el arranque ni agotar el límite de conexiones del plan.
- Las migraciones se aplican contra Neon al arrancar la app (Flyway). Si Flyway falla por un checksum, **no edites** la migración aplicada: crea una nueva.
- Los **tests nunca apuntan a Neon**: usan Testcontainers (ver sección 13).
- Si una credencial se expone, debe rotarse desde la consola de Neon.

### `application.yml` (base)

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 5
      minimum-idle: 0
      connection-timeout: 30000
      idle-timeout: 300000
      max-lifetime: 600000
  jpa:
    hibernate.ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
    url: ${DB_MIGRATION_URL:${DB_URL}}
    user: ${DB_USER}
    password: ${DB_PASSWORD}
    locations: classpath:db/migration
app:
  jwt:
    secret: ${JWT_SECRET}
    expiration-minutes: 60
  dispatch:
    offers-per-round: 3
    offer-ttl-seconds: 30
    radius-km-rounds: [5, 10, 20]
    location-max-age-seconds: 120
```

Perfiles `dev` y `test`. El perfil `test` configura Testcontainers (no usa las variables de Neon).

Incluye un `README.md` con: cómo obtener la cadena de Neon, cómo convertirla a formato JDBC, qué variables de entorno definir y cómo arrancar el proyecto.

## 11. Documentación de endpoints (`docEnd.md`)

Existe un documento vivo **`docEnd.md` en la raíz del proyecto** (junto al `pom.xml`). Es la referencia completa de la API para quien construya el cliente.

Reglas:

1. Créalo en la **Fase 0** con el esqueleto (intro, convenciones, errores, índice vacío, changelog).
2. **Un endpoint no está terminado hasta que está documentado en `docEnd.md`.** Actualízalo en el mismo cambio en el que creas, modificas o eliminas un endpoint.
3. Documenta únicamente lo que **realmente está implementado** (no lo planeado). Si un endpoint cambia o se elimina, actualiza o quita su entrada.
4. Los ejemplos de JSON deben ser **válidos y coherentes con los DTOs reales**. Los `curl` usan las variables `$BASE_URL` y `$TOKEN`.
5. Cada vez que se agregue, cambie o elimine algo, añade una línea al **Changelog** al final (fecha, fase, cambio).
6. Escríbelo en español.

Estructura del documento:

1. **Introducción**: URL base (`http://localhost:8080`), autenticación (`Authorization: Bearer <jwt>`), formato de fechas (ISO-8601 UTC), IDs (UUID), cómo obtener un token.
2. **Formato de errores** (`ApiError`) y tabla de códigos HTTP / `code` de error que usa la API.
3. **Índice** de endpoints agrupado por módulo (Auth, Ciudadano, Unidad, Alertas, Despacho...).
4. **Detalle de cada endpoint** con la plantilla de abajo.
5. **WebSocket**: URL de conexión, cómo enviar el JWT en `CONNECT`, destinos de suscripción, formato de cada mensaje y ejemplo.
6. **Flujo de ejemplo de punta a punta** (registro → login → ubicación de unidad → alerta → aceptar → tracking → resolver) con `curl`.
7. **Changelog**.

Plantilla por endpoint:

````markdown
### POST /api/alerts

**Descripción:** Crea una alerta de emergencia y dispara el despacho a las unidades cercanas.
**Rol requerido:** `CITIZEN`
**Estado:** Implementado (Fase 3)

**Headers**
| Header | Valor |
|--------|-------|
| Authorization | `Bearer <jwt>` |
| Content-Type | `application/json` |

**Parámetros de ruta / query:** (ninguno)

**Body**
```json
{
  "type": "ROBBERY",
  "description": "Asalto en la esquina",
  "latitude": 19.4167,
  "longitude": -102.0667
}
```
| Campo | Tipo | Obligatorio | Reglas |
|-------|------|-------------|--------|
| type | string | Sí | `ROBBERY`, `FIRE`, `MEDICAL`, `ACCIDENT`, `OTHER` |
| ... | ... | ... | ... |

**Respuesta exitosa — 201 Created**
```json
{ "id": "…", "status": "PENDING", "...": "..." }
```

**Errores posibles**
| HTTP | code | Cuándo ocurre |
|------|------|---------------|
| 400 | VALIDATION_ERROR | Datos inválidos |
| 401 | UNAUTHORIZED | Token ausente o inválido |
| 403 | FORBIDDEN | El rol no es `CITIZEN` |
| 409 | ACTIVE_ALERT_EXISTS | El ciudadano ya tiene una alerta activa |

**Ejemplo curl**
```bash
curl -X POST "$BASE_URL/api/alerts" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"ROBBERY","description":"Asalto","latitude":19.4167,"longitude":-102.0667}'
```
````

## 12. Convenciones de código

- DTOs como `record` con anotaciones de Bean Validation.
- Servicios transaccionales (`@Transactional`); lecturas con `readOnly = true`.
- Sin lógica de negocio en controllers.
- Excepciones de dominio (`NotFoundException`, `ConflictException`, `ForbiddenException`...) traducidas por un único `@RestControllerAdvice`.
- Fechas siempre `Instant`/`OffsetDateTime` en UTC; columnas `TIMESTAMPTZ`.
- IDs `UUID` (excepto tablas de historial, `BIGSERIAL`).
- Nombres de código en inglés; mensajes al usuario en español.
- Commits pequeños y descriptivos por tarea.

## 13. Pruebas mínimas esperadas

- Unit tests: Haversine, máquina de estados de la alerta, selección de unidades por ronda.
- Integración (Testcontainers + PostgreSQL real, **no H2** y **nunca contra la BD de Neon**): migraciones Flyway se aplican limpias; registro/login; crear alerta → genera ofertas; aceptar alerta; **aceptación concurrente (1 ganador)**; ubicación batch idempotente; ubicación vieja no sobreescribe la actual.
- Una prueba de WebSocket que verifique que el ciudadano recibe la ubicación de la unidad tras aceptar.

## 14. Plan por fases (detente al terminar cada una y reporta)

0. **Esqueleto**: proyecto Maven (Java 21, Spring Boot 4.1.2), dependencias iniciales, conexión a Neon por variables de entorno, `application.yml`, `.env.example`, `.gitignore`, `docEnd.md` inicial (esqueleto), migración V1 aplicada en Neon, la app arranca y `GET /actuator/health` responde OK.
1. **Auth y usuarios**: registro ciudadano/unidad, login JWT, `/api/me`, seguridad por rol.
2. **Unidades y ubicaciones**: estado, ubicación (simple y batch idempotente), `units/nearby`.
3. **Alertas y despacho**: crear alerta, rondas de ofertas, aceptar/rechazar atómico, expiración programada, cancelar, arrive/resolve, bitácora `alert_events`.
4. **Tiempo real**: STOMP + JWT, notificaciones de ofertas, tracking por alerta.
5. **Ruta** (opcional): `GET /api/alerts/{id}/route` consultando OSRM (`router.project-osrm.org` solo para desarrollo; es un servicio demo sin garantías). Si falla, devolver línea recta con `fallback: true`.
6. **Pulido**: OpenAPI/Swagger, README, colección Postman, script de simulación de unidades moviéndose.

**En todas las fases:** cada endpoint creado o modificado se documenta en `docEnd.md` (sección 11) y el resumen final de la fase debe indicar qué entradas se agregaron o cambiaron.

## 15. Lo que NO debes hacer

- No inventar tablas/columnas fuera del esquema sin crear una migración nueva.
- No usar `localStorage`-style hacks, caches en memoria globales como fuente de verdad, ni estado compartido entre requests en singletons (la BD es la fuente de verdad; el estado debe sobrevivir a un reinicio).
- No añadir dependencias pesadas sin justificar.
- No saltarte las fases ni mezclar varias en un solo cambio gigante.
- No dejar `TODO` silenciosos: si algo se difiere, anótalo en `README.md` sección "Pendientes".
- No hagas commit de credenciales de Neon, `JWT_SECRET` ni archivos `.env`. No imprimas la URL de conexión en logs.
- No des un endpoint por terminado sin su entrada completa en `docEnd.md`.
- No uses sintaxis o APIs de Spring Boot 3 / Jackson 2 si el proyecto es Boot 4.1.2: verifica en la documentación.
