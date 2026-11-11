# API UrbTrack

## 1. Introducción

- URL base: `http://localhost:8080`.
- Autenticación: `Authorization: Bearer <jwt>` en endpoints protegidos.
- Fechas: formato ISO-8601 en UTC.
- IDs: UUID.
- Para obtener un token, registra una cuenta y usa `POST /api/auth/login`.

## 2. Formato de errores

```json
{
  "timestamp": "2026-11-09T12:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "La solicitud contiene datos inválidos",
  "path": "/api/auth/register/citizen"
}
```

| HTTP | code | Descripción |
|------|------|-------------|
| 400 | `VALIDATION_ERROR` | Datos de entrada inválidos. |
| 401 | `UNAUTHORIZED` | Token ausente/inválido o credenciales incorrectas. |
| 403 | `FORBIDDEN` | Rol no autorizado para el recurso. |
| 404 | `NOT_FOUND` | Recurso inexistente. |
| 409 | `EMAIL_ALREADY_EXISTS` | El correo ya está registrado. |
| 409 | `UNIT_IDENTIFIER_EXISTS` | El identificador de unidad ya está registrado. |
| 409 | `DATA_CONFLICT` | Conflicto con otra restricción de integridad. |
| 409 | `UNIT_BUSY` | Una unidad ocupada no puede cambiar su estado manualmente. |
| 409 | `ACTIVE_ALERT_EXISTS` | El ciudadano ya tiene una alerta activa. |
| 409 | `INVALID_ALERT_TRANSITION` | La transición de estado solicitada no está permitida. |
| 409 | `ALERT_ALREADY_ASSIGNED` | La alerta ya fue tomada o dejó de estar pendiente. |
| 409 | `DISPATCH_NOT_OFFERED` | La oferta ya expiró, fue respondida o cancelada. |
| 409 | `UNIT_NOT_AVAILABLE` | La unidad dejó de estar disponible para aceptar. |
| 409 | `UNIT_ALREADY_ASSIGNED` | La unidad ya tiene una alerta activa. |
| 405 | `METHOD_NOT_ALLOWED` | Método HTTP no permitido para la ruta. |
| 500 | `INTERNAL_ERROR` | Error inesperado. |

## 3. Índice de endpoints

### Auth
- `POST /api/auth/register/citizen`
- `POST /api/auth/register/unit`
- `POST /api/auth/login`
- `GET /api/me`

### Ciudadano, unidades, alertas y despacho
- `GET /api/units/nearby`
- `PUT /api/units/me/status`
- `POST /api/units/me/location`
- `POST /api/units/me/locations/batch`
- `POST /api/alerts`
- `GET /api/alerts/active`
- `POST /api/alerts/{id}/cancel`
- `POST /api/alerts/{id}/arrive`
- `POST /api/alerts/{id}/resolve`
- `GET /api/dispatches/pending`
- `POST /api/dispatches/{id}/accept`
- `POST /api/dispatches/{id}/reject`
- Ruta de alerta: sin endpoint implementado todavía (Fase 5).

### Operación
- `GET /actuator/health`

## 4. Detalle de endpoints

### POST /api/auth/register/citizen

**Descripción:** Registra un ciudadano.
**Rol requerido:** Público.
**Estado:** Implementado (Fase 1).

**Headers**
| Header | Valor |
|--------|-------|
| Content-Type | `application/json` |

**Parámetros de ruta / query:** Ninguno.

**Body**
```json
{
  "email": "ana@example.com",
  "password": "Secreto123!",
  "fullName": "Ana Pérez",
  "phone": "+524521234567"
}
```
| Campo | Tipo | Obligatorio | Reglas |
|-------|------|-------------|--------|
| email | string | Sí | Correo válido, máximo 255 caracteres. |
| password | string | Sí | 8–72 caracteres. |
| fullName | string | Sí | 2–150 caracteres. |
| phone | string | No | Máximo 30 caracteres. |

**Respuesta exitosa — 201 Created**
```json
{
  "userId": "c75501b5-9ec5-4f68-ae32-34c29f28c31d",
  "email": "ana@example.com",
  "fullName": "Ana Pérez",
  "phone": "+524521234567",
  "role": "CITIZEN",
  "unitId": null,
  "unitType": null,
  "identifier": null,
  "unitStatus": null
}
```

**Errores posibles**
| HTTP | code | Cuándo ocurre |
|------|------|---------------|
| 400 | `VALIDATION_ERROR` | Datos inválidos. |
| 409 | `EMAIL_ALREADY_EXISTS` | El correo ya está registrado. |
| 409 | `DATA_CONFLICT` | Conflicto de integridad ante registros concurrentes. |

**Ejemplo curl**
```bash
curl -X POST "$BASE_URL/api/auth/register/citizen" \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@example.com","password":"Secreto123!","fullName":"Ana Pérez","phone":"+524521234567"}'
```

### POST /api/auth/register/unit

**Descripción:** Registra una cuenta de unidad y su perfil operativo (auto-registro habilitado para desarrollo).
**Rol requerido:** Público.
**Estado:** Implementado (Fase 1).

**Headers**
| Header | Valor |
|--------|-------|
| Content-Type | `application/json` |

**Parámetros de ruta / query:** Ninguno.

**Body**
```json
{
  "email": "unidad@example.com",
  "password": "Secreto123!",
  "fullName": "Unidad U-17",
  "phone": "+524521234567",
  "unitType": "POLICE",
  "identifier": "U-17"
}
```
| Campo | Tipo | Obligatorio | Reglas |
|-------|------|-------------|--------|
| email | string | Sí | Correo válido, máximo 255 caracteres. |
| password | string | Sí | 8–72 caracteres. |
| fullName | string | Sí | 2–150 caracteres. |
| phone | string | No | Máximo 30 caracteres. |
| unitType | string | Sí | `POLICE`, `FIRE`, `AMBULANCE`, `CIVIL_PROTECTION`. |
| identifier | string | Sí | 1–50 caracteres. |

**Respuesta exitosa — 201 Created**
```json
{
  "userId": "c75501b5-9ec5-4f68-ae32-34c29f28c31d",
  "email": "unidad@example.com",
  "fullName": "Unidad U-17",
  "phone": "+524521234567",
  "role": "UNIT",
  "unitId": "27bab08f-c82e-4591-91b5-25ee14407a9e",
  "unitType": "POLICE",
  "identifier": "U-17",
  "unitStatus": "OFFLINE"
}
```

**Errores posibles**
| HTTP | code | Cuándo ocurre |
|------|------|---------------|
| 400 | `VALIDATION_ERROR` | Datos inválidos. |
| 409 | `EMAIL_ALREADY_EXISTS` | El correo ya está registrado. |
| 409 | `UNIT_IDENTIFIER_EXISTS` | El identificador ya está registrado. |
| 409 | `DATA_CONFLICT` | Conflicto de integridad ante registros concurrentes. |

**Ejemplo curl**
```bash
curl -X POST "$BASE_URL/api/auth/register/unit" \
  -H "Content-Type: application/json" \
  -d '{"email":"unidad@example.com","password":"Secreto123!","fullName":"Unidad U-17","unitType":"POLICE","identifier":"U-17"}'
```

### POST /api/auth/login

**Descripción:** Autentica por correo y contraseña y genera un JWT HS256 con expiración de 60 minutos.
**Rol requerido:** Público.
**Estado:** Implementado (Fase 1).

**Headers**
| Header | Valor |
|--------|-------|
| Content-Type | `application/json` |

**Parámetros de ruta / query:** Ninguno.

**Body**
```json
{ "email": "ana@example.com", "password": "Secreto123!" }
```
| Campo | Tipo | Obligatorio | Reglas |
|-------|------|-------------|--------|
| email | string | Sí | Correo válido. |
| password | string | Sí | No vacío. |

**Respuesta exitosa — 200 OK**
```json
{ "accessToken": "<jwt>", "role": "CITIZEN", "userId": "c75501b5-9ec5-4f68-ae32-34c29f28c31d" }
```

**Errores posibles**
| HTTP | code | Cuándo ocurre |
|------|------|---------------|
| 400 | `VALIDATION_ERROR` | Datos inválidos. |
| 401 | `UNAUTHORIZED` | Correo/contraseña incorrectos o cuenta deshabilitada. |

**Ejemplo curl**
```bash
curl -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@example.com","password":"Secreto123!"}'
```

### GET /api/me

**Descripción:** Devuelve el perfil de la cuenta autenticada; incluye datos operativos solo para una unidad.
**Rol requerido:** `CITIZEN`, `UNIT` o `ADMIN`.
**Estado:** Implementado (Fase 1).

**Headers**
| Header | Valor |
|--------|-------|
| Authorization | `Bearer <jwt>` |

**Parámetros de ruta / query:** Ninguno.

**Respuesta exitosa — 200 OK**
```json
{
  "userId": "c75501b5-9ec5-4f68-ae32-34c29f28c31d",
  "email": "ana@example.com",
  "fullName": "Ana Pérez",
  "phone": null,
  "role": "CITIZEN",
  "unitId": null,
  "unitType": null,
  "identifier": null,
  "unitStatus": null
}
```

**Errores posibles**
| HTTP | code | Cuándo ocurre |
|------|------|---------------|
| 401 | `UNAUTHORIZED` | Token ausente, expirado o inválido. |
| 404 | `NOT_FOUND` | La cuenta asociada al token ya no existe. |

**Ejemplo curl**
```bash
curl "$BASE_URL/api/me" -H "Authorization: Bearer $TOKEN"
```

### GET /api/units/nearby

**Descripción:** Busca unidades `AVAILABLE` con ubicación recibida en los últimos 120 segundos, ordenadas por distancia Haversine. La respuesta no incluye usuario, nombre, identificador operativo ni otros datos personales.
**Rol requerido:** `CITIZEN`.
**Estado:** Implementado (Fase 2).

**Headers**
| Header | Valor |
|--------|-------|
| Authorization | `Bearer <jwt>` |

**Parámetros de ruta / query**
| Parámetro | Tipo | Obligatorio | Reglas |
|-----------|------|-------------|--------|
| lat | número | Sí | Latitud entre -90 y 90. |
| lon | número | Sí | Longitud entre -180 y 180. |
| radiusKm | número | Sí | Número finito mayor que cero. |

**Respuesta exitosa — 200 OK**
```json
[
  {
    "unitId": "27bab08f-c82e-4591-91b5-25ee14407a9e",
    "unitType": "POLICE",
    "latitude": 19.4167,
    "longitude": -102.0667,
    "heading": 90.0
  }
]
```

**Errores posibles**
| HTTP | code | Cuándo ocurre |
|------|------|---------------|
| 400 | `VALIDATION_ERROR` | Faltan parámetros o sus valores/rangos no son válidos. |
| 401 | `UNAUTHORIZED` | Token ausente o inválido. |
| 403 | `FORBIDDEN` | El rol no es `CITIZEN`. |

**Ejemplo curl**
```bash
curl "$BASE_URL/api/units/nearby?lat=19.4167&lon=-102.0667&radiusKm=5" \
  -H "Authorization: Bearer $TOKEN"
```

### PUT /api/units/me/status

**Descripción:** Cambia el estado manual de la unidad autenticada. `BUSY` es administrado por el sistema y no se acepta en esta operación.
**Rol requerido:** `UNIT`.
**Estado:** Implementado (Fase 2).

**Headers**
| Header | Valor |
|--------|-------|
| Authorization | `Bearer <jwt>` |
| Content-Type | `application/json` |

**Parámetros de ruta / query:** Ninguno.

**Body**
```json
{ "status": "AVAILABLE" }
```
| Campo | Tipo | Obligatorio | Reglas |
|-------|------|-------------|--------|
| status | string | Sí | `AVAILABLE` o `OFFLINE`. `BUSY` no puede establecerse manualmente. |

**Respuesta exitosa — 200 OK**
```json
{ "unitId": "27bab08f-c82e-4591-91b5-25ee14407a9e", "status": "AVAILABLE" }
```

**Errores posibles**
| HTTP | code | Cuándo ocurre |
|------|------|---------------|
| 400 | `VALIDATION_ERROR` | Estado faltante, desconocido o `BUSY`. |
| 401 | `UNAUTHORIZED` | Token ausente o inválido. |
| 403 | `FORBIDDEN` | El rol no es `UNIT`. |
| 404 | `NOT_FOUND` | La cuenta no tiene perfil de unidad. |
| 409 | `UNIT_BUSY` | La unidad está ocupada y no puede cambiar su estado manualmente. |

**Ejemplo curl**
```bash
curl -X PUT "$BASE_URL/api/units/me/status" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"AVAILABLE"}'
```

### POST /api/units/me/location

**Descripción:** Registra un evento de ubicación en el historial y actualiza la última ubicación si `recordedAt` no es anterior a la vigente. Un `eventId` repetido es idempotente y responde `200` sin insertar de nuevo.
**Rol requerido:** `UNIT`.
**Estado:** Implementado (Fase 2).

**Headers**
| Header | Valor |
|--------|-------|
| Authorization | `Bearer <jwt>` |
| Content-Type | `application/json` |

**Parámetros de ruta / query:** Ninguno.

**Body**
```json
{
  "eventId": "7e498979-f108-4a4d-86b3-b4f9f0c81e80",
  "latitude": 19.4167,
  "longitude": -102.0667,
  "heading": 90.0,
  "speedKmh": 35.5,
  "recordedAt": "2026-11-09T18:00:00Z"
}
```
| Campo | Tipo | Obligatorio | Reglas |
|-------|------|-------------|--------|
| eventId | UUID | Sí | Identificador único generado por el cliente. |
| latitude | número | Sí | Entre -90 y 90. |
| longitude | número | Sí | Entre -180 y 180. |
| heading | número | No | Entre 0 y 360. |
| speedKmh | número | No | Velocidad reportada por el cliente. |
| recordedAt | fecha/hora | Sí | ISO-8601; no más de 1 minuto en el futuro. |

**Respuesta exitosa — 200 OK**
```json
{ "eventId": "7e498979-f108-4a4d-86b3-b4f9f0c81e80", "duplicate": false, "currentLocationUpdated": true }
```

**Errores posibles**
| HTTP | code | Cuándo ocurre |
|------|------|---------------|
| 400 | `VALIDATION_ERROR` | Coordenadas, rumbo, fecha o cuerpo inválidos. |
| 401 | `UNAUTHORIZED` | Token ausente o inválido. |
| 403 | `FORBIDDEN` | El rol no es `UNIT`. |
| 404 | `NOT_FOUND` | La cuenta no tiene perfil de unidad. |

**Ejemplo curl**
```bash
curl -X POST "$BASE_URL/api/units/me/location" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"eventId":"7e498979-f108-4a4d-86b3-b4f9f0c81e80","latitude":19.4167,"longitude":-102.0667,"recordedAt":"2026-11-09T18:00:00Z"}'
```

### POST /api/units/me/locations/batch

**Descripción:** Persiste una lista de ubicaciones offline en el orden recibido. Cada `eventId` es idempotente. El lote completo se procesa en una transacción; ubicaciones históricas se guardan aunque sean antiguas, pero no reemplazan la ubicación vigente.
**Rol requerido:** `UNIT`.
**Estado:** Implementado (Fase 2).

**Headers**
| Header | Valor |
|--------|-------|
| Authorization | `Bearer <jwt>` |
| Content-Type | `application/json` |

**Parámetros de ruta / query:** Ninguno.

**Body**
```json
[
  {
    "eventId": "7e498979-f108-4a4d-86b3-b4f9f0c81e80",
    "latitude": 19.4167,
    "longitude": -102.0667,
    "recordedAt": "2026-11-09T18:00:00Z"
  },
  {
    "eventId": "adc259af-c994-4481-9ee4-0214ce48c654",
    "latitude": 19.4170,
    "longitude": -102.0664,
    "heading": 91.0,
    "speedKmh": 30.0,
    "recordedAt": "2026-11-09T18:00:05Z"
  }
]
```
Cada elemento sigue las reglas del body de `POST /api/units/me/location`.

**Respuesta exitosa — 200 OK**
```json
{
  "received": 2,
  "stored": 2,
  "duplicates": 0,
  "currentLocationsUpdated": 2,
  "results": [
    { "eventId": "7e498979-f108-4a4d-86b3-b4f9f0c81e80", "duplicate": false, "currentLocationUpdated": true },
    { "eventId": "adc259af-c994-4481-9ee4-0214ce48c654", "duplicate": false, "currentLocationUpdated": true }
  ]
}
```
`stored` cuenta eventos nuevos del historial; `currentLocationsUpdated` cuenta los que reemplazaron la última ubicación.

**Errores posibles**
| HTTP | code | Cuándo ocurre |
|------|------|---------------|
| 400 | `VALIDATION_ERROR` | Algún elemento del lote es inválido. No se persiste parcialmente. |
| 401 | `UNAUTHORIZED` | Token ausente o inválido. |
| 403 | `FORBIDDEN` | El rol no es `UNIT`. |
| 404 | `NOT_FOUND` | La cuenta no tiene perfil de unidad. |

**Ejemplo curl**
```bash
curl -X POST "$BASE_URL/api/units/me/locations/batch" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '[{"eventId":"7e498979-f108-4a4d-86b3-b4f9f0c81e80","latitude":19.4167,"longitude":-102.0667,"recordedAt":"2026-11-09T18:00:00Z"}]'
```

### POST /api/alerts

**Descripción:** Crea una alerta `PENDING`, determina el tipo de unidad requerido y comienza el despacho por rondas: radios de 5, 10 y 20 km, hasta 3 ofertas por ronda y expiración de cada oferta a los 30 segundos. Solo considera unidades disponibles con ubicación recibida en los últimos 120 segundos. Si una ronda no encuentra unidades, se intenta la siguiente inmediatamente; sin unidades tras la última, la alerta expira.
**Rol requerido:** `CITIZEN`.
**Estado:** Implementado (Fase 3).

**Headers**
| Header | Valor |
|--------|-------|
| Authorization | `Bearer <jwt>` |
| Content-Type | `application/json` |

**Parámetros de ruta / query:** Ninguno.

**Body**
```json
{
  "type": "ROBBERY",
  "description": "Asalto cerca del parque",
  "latitude": 19.4167,
  "longitude": -102.0667
}
```
| Campo | Tipo | Obligatorio | Reglas |
|-------|------|-------------|--------|
| type | string | Sí | `ROBBERY`, `FIRE`, `MEDICAL`, `ACCIDENT`, `OTHER`. |
| description | string | No | Máximo 500 caracteres. |
| latitude | número | Sí | Entre -90 y 90. |
| longitude | número | Sí | Entre -180 y 180. |

Tipos requeridos: `ROBBERY` → `POLICE`, `FIRE` → `FIRE`, `MEDICAL`/`ACCIDENT` → `AMBULANCE`, `OTHER` → `CIVIL_PROTECTION`.

**Respuesta exitosa — 201 Created**
```json
{
  "id": "62320253-f1bd-4466-a031-82dc997c7b96",
  "type": "ROBBERY",
  "requiredUnitType": "POLICE",
  "description": "Asalto cerca del parque",
  "latitude": 19.4167,
  "longitude": -102.0667,
  "status": "PENDING",
  "dispatchRound": 1,
  "createdAt": "2026-11-10T18:00:00Z",
  "acceptedAt": null,
  "arrivedAt": null,
  "resolvedAt": null,
  "cancelledAt": null,
  "assignedUnit": null
}
```
Si no hay unidades disponibles en ninguna ronda, se devuelve `201` con estado `EXPIRED`.

**Errores posibles**
| HTTP | code | Cuándo ocurre |
|------|------|---------------|
| 400 | `VALIDATION_ERROR` | Body o coordenadas inválidos. |
| 401 | `UNAUTHORIZED` | Token ausente o inválido. |
| 403 | `FORBIDDEN` | El rol no es `CITIZEN`. |
| 409 | `ACTIVE_ALERT_EXISTS` | El ciudadano ya tiene una alerta activa. |

**Ejemplo curl**
```bash
curl -X POST "$BASE_URL/api/alerts" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"ROBBERY","description":"Asalto cerca del parque","latitude":19.4167,"longitude":-102.0667}'
```

### GET /api/alerts/active

**Descripción:** Devuelve la alerta activa del ciudadano autenticado. Si fue asignada, incluye tipo, identificador y última ubicación de la unidad, sin datos personales del usuario de la unidad.
**Rol requerido:** `CITIZEN`.
**Estado:** Implementado (Fase 3).

**Headers**
| Header | Valor |
|--------|-------|
| Authorization | `Bearer <jwt>` |

**Parámetros de ruta / query:** Ninguno.

**Respuesta exitosa — 200 OK**
```json
{
  "id": "62320253-f1bd-4466-a031-82dc997c7b96",
  "type": "ROBBERY",
  "requiredUnitType": "POLICE",
  "description": "Asalto cerca del parque",
  "latitude": 19.4167,
  "longitude": -102.0667,
  "status": "EN_ROUTE",
  "dispatchRound": 1,
  "createdAt": "2026-11-10T18:00:00Z",
  "acceptedAt": "2026-11-10T18:00:08Z",
  "arrivedAt": null,
  "resolvedAt": null,
  "cancelledAt": null,
  "assignedUnit": {
    "unitId": "27bab08f-c82e-4591-91b5-25ee14407a9e",
    "unitType": "POLICE",
    "identifier": "U-17",
    "latitude": 19.417,
    "longitude": -102.0664,
    "heading": 90.0
  }
}
```

**Errores posibles**
| HTTP | code | Cuándo ocurre |
|------|------|---------------|
| 401 | `UNAUTHORIZED` | Token ausente o inválido. |
| 403 | `FORBIDDEN` | El rol no es `CITIZEN`. |
| 404 | `NOT_FOUND` | El ciudadano no tiene alerta activa. |

**Ejemplo curl**
```bash
curl "$BASE_URL/api/alerts/active" -H "Authorization: Bearer $TOKEN"
```

### POST /api/alerts/{id}/cancel

**Descripción:** Cancela la alerta propia en estado `PENDING` o `EN_ROUTE`; libera la unidad asignada si la hubiera y cancela las ofertas vigentes.
**Rol requerido:** `CITIZEN` propietario de la alerta.
**Estado:** Implementado (Fase 3).

**Headers**
| Header | Valor |
|--------|-------|
| Authorization | `Bearer <jwt>` |

**Parámetros de ruta / query**
| Parámetro | Tipo | Obligatorio | Reglas |
|-----------|------|-------------|--------|
| id | UUID | Sí | ID de la alerta. |

**Body:** Ninguno.

**Respuesta exitosa — 200 OK**
```json
{
  "id": "62320253-f1bd-4466-a031-82dc997c7b96",
  "type": "ROBBERY",
  "requiredUnitType": "POLICE",
  "description": "Asalto cerca del parque",
  "latitude": 19.4167,
  "longitude": -102.0667,
  "status": "CANCELLED",
  "dispatchRound": 1,
  "createdAt": "2026-11-10T18:00:00Z",
  "acceptedAt": null,
  "arrivedAt": null,
  "resolvedAt": null,
  "cancelledAt": "2026-11-10T18:00:10Z",
  "assignedUnit": null
}
```

**Errores posibles**
| HTTP | code | Cuándo ocurre |
|------|------|---------------|
| 401 | `UNAUTHORIZED` | Token ausente o inválido. |
| 403 | `FORBIDDEN` | La alerta pertenece a otro ciudadano o el rol no es `CITIZEN`. |
| 404 | `NOT_FOUND` | Alerta inexistente. |
| 409 | `INVALID_ALERT_TRANSITION` | La alerta no está en `PENDING` ni `EN_ROUTE`. |

**Ejemplo curl**
```bash
curl -X POST "$BASE_URL/api/alerts/62320253-f1bd-4466-a031-82dc997c7b96/cancel" \
  -H "Authorization: Bearer $TOKEN"
```

### GET /api/dispatches/pending

**Descripción:** Lista las ofertas `OFFERED` vigentes de la unidad autenticada; permite recuperar ofertas si no recibió una notificación.
**Rol requerido:** `UNIT`.
**Estado:** Implementado (Fase 3).

**Headers**
| Header | Valor |
|--------|-------|
| Authorization | `Bearer <jwt>` |

**Parámetros de ruta / query:** Ninguno.

**Respuesta exitosa — 200 OK**
```json
[
  {
    "dispatchId": "f736d3e1-a714-43ad-9b7a-49f63fd295fa",
    "alertId": "62320253-f1bd-4466-a031-82dc997c7b96",
    "type": "ROBBERY",
    "requiredUnitType": "POLICE",
    "description": "Asalto cerca del parque",
    "latitude": 19.4167,
    "longitude": -102.0667,
    "round": 1,
    "distanceMeters": 125.4,
    "expiresAt": "2026-11-10T18:00:30Z"
  }
]
```

**Errores posibles:** `401 UNAUTHORIZED`, `403 FORBIDDEN` si el rol no es `UNIT`, `404 NOT_FOUND` si falta el perfil de unidad.

**Ejemplo curl**
```bash
curl "$BASE_URL/api/dispatches/pending" -H "Authorization: Bearer $TOKEN"
```

### POST /api/dispatches/{id}/accept

**Descripción:** Acepta una oferta vigente. La asignación es atómica en PostgreSQL; una sola unidad puede ganar aunque varias acepten simultáneamente. Al ganar, la unidad pasa a `BUSY` y las demás ofertas se cancelan.
**Rol requerido:** `UNIT` destinataria de la oferta.
**Estado:** Implementado (Fase 3).

**Headers**
| Header | Valor |
|--------|-------|
| Authorization | `Bearer <jwt>` |

**Parámetros de ruta / query**
| Parámetro | Tipo | Obligatorio | Reglas |
|-----------|------|-------------|--------|
| id | UUID | Sí | ID del dispatch ofrecido a esta unidad. |

**Body:** Ninguno.

**Respuesta exitosa — 200 OK**
```json
{
  "id": "62320253-f1bd-4466-a031-82dc997c7b96",
  "type": "ROBBERY",
  "requiredUnitType": "POLICE",
  "description": "Asalto cerca del parque",
  "latitude": 19.4167,
  "longitude": -102.0667,
  "status": "EN_ROUTE",
  "dispatchRound": 1,
  "createdAt": "2026-11-10T18:00:00Z",
  "acceptedAt": "2026-11-10T18:00:08Z",
  "arrivedAt": null,
  "resolvedAt": null,
  "cancelledAt": null,
  "assignedUnit": {
    "unitId": "27bab08f-c82e-4591-91b5-25ee14407a9e",
    "unitType": "POLICE",
    "identifier": "U-17",
    "latitude": 19.417,
    "longitude": -102.0664,
    "heading": 90.0
  }
}
```
La ruta de navegación queda para la Fase 5.

**Errores posibles**
| HTTP | code | Cuándo ocurre |
|------|------|---------------|
| 401 | `UNAUTHORIZED` | Token ausente o inválido. |
| 403 | `FORBIDDEN` | El rol no es `UNIT`. |
| 404 | `NOT_FOUND` | Dispatch inexistente/no pertenece a esa unidad o falta perfil de unidad. |
| 409 | `DISPATCH_NOT_OFFERED` | La oferta ya no está vigente. |
| 409 | `ALERT_ALREADY_ASSIGNED` | Otra unidad ganó o la alerta ya no está pendiente. |
| 409 | `UNIT_NOT_AVAILABLE` / `UNIT_ALREADY_ASSIGNED` | La unidad ya no puede aceptar otra alerta. |

**Ejemplo curl**
```bash
curl -X POST "$BASE_URL/api/dispatches/f736d3e1-a714-43ad-9b7a-49f63fd295fa/accept" \
  -H "Authorization: Bearer $TOKEN"
```

### POST /api/dispatches/{id}/reject

**Descripción:** Rechaza una oferta vigente. Si no quedan ofertas vivas, dispara la siguiente ronda de despacho.
**Rol requerido:** `UNIT` destinataria de la oferta.
**Estado:** Implementado (Fase 3).

**Headers**
| Header | Valor |
|--------|-------|
| Authorization | `Bearer <jwt>` |

**Parámetros de ruta / query:** `id` (UUID del dispatch ofrecido a esta unidad).
**Body:** Ninguno.

**Respuesta exitosa — 200 OK**
```json
{
  "dispatchId": "f736d3e1-a714-43ad-9b7a-49f63fd295fa",
  "alertId": "62320253-f1bd-4466-a031-82dc997c7b96",
  "status": "REJECTED"
}
```

**Errores posibles:** `401 UNAUTHORIZED`, `403 FORBIDDEN`, `404 NOT_FOUND` o `409 DISPATCH_NOT_OFFERED`.

**Ejemplo curl**
```bash
curl -X POST "$BASE_URL/api/dispatches/f736d3e1-a714-43ad-9b7a-49f63fd295fa/reject" \
  -H "Authorization: Bearer $TOKEN"
```

### POST /api/alerts/{id}/arrive

**Descripción:** Cambia la alerta asignada de `EN_ROUTE` a `ON_SCENE`.
**Rol requerido:** `UNIT` asignada.
**Estado:** Implementado (Fase 3).

**Headers:** `Authorization: Bearer <jwt>`.
**Parámetros de ruta / query:** `id` (UUID de la alerta).
**Body:** Ninguno.

**Respuesta exitosa — 200 OK**
```json
{
  "id": "62320253-f1bd-4466-a031-82dc997c7b96",
  "type": "ROBBERY",
  "requiredUnitType": "POLICE",
  "description": "Asalto cerca del parque",
  "latitude": 19.4167,
  "longitude": -102.0667,
  "status": "ON_SCENE",
  "dispatchRound": 1,
  "createdAt": "2026-11-10T18:00:00Z",
  "acceptedAt": "2026-11-10T18:00:08Z",
  "arrivedAt": "2026-11-10T18:05:00Z",
  "resolvedAt": null,
  "cancelledAt": null,
  "assignedUnit": {
    "unitId": "27bab08f-c82e-4591-91b5-25ee14407a9e",
    "unitType": "POLICE",
    "identifier": "U-17",
    "latitude": 19.4167,
    "longitude": -102.0667,
    "heading": 90.0
  }
}
```

**Errores posibles:** `401 UNAUTHORIZED`, `403 FORBIDDEN` si la alerta pertenece a otra unidad, `404 NOT_FOUND`, `409 INVALID_ALERT_TRANSITION` si el estado actual no es `EN_ROUTE`.

**Ejemplo curl**
```bash
curl -X POST "$BASE_URL/api/alerts/62320253-f1bd-4466-a031-82dc997c7b96/arrive" \
  -H "Authorization: Bearer $TOKEN"
```

### POST /api/alerts/{id}/resolve

**Descripción:** Cambia la alerta asignada de `ON_SCENE` a `RESOLVED` y libera la unidad (`AVAILABLE`).
**Rol requerido:** `UNIT` asignada.
**Estado:** Implementado (Fase 3).

**Headers:** `Authorization: Bearer <jwt>`.
**Parámetros de ruta / query:** `id` (UUID de la alerta).
**Body:** Ninguno.

**Respuesta exitosa — 200 OK:** El mismo `AlertResponse`, con `status: "RESOLVED"`, `resolvedAt` asignado y `assignedUnit` conservada como referencia histórica.

**Errores posibles:** `401 UNAUTHORIZED`, `403 FORBIDDEN` si la alerta pertenece a otra unidad, `404 NOT_FOUND`, `409 INVALID_ALERT_TRANSITION` si el estado actual no es `ON_SCENE`.

**Ejemplo curl**
```bash
curl -X POST "$BASE_URL/api/alerts/62320253-f1bd-4466-a031-82dc997c7b96/resolve" \
  -H "Authorization: Bearer $TOKEN"
```

### GET /actuator/health

**Descripción:** Health check de Spring Boot Actuator.
**Rol requerido:** Público.
**Estado:** Implementado (Fase 0).

**Headers:** Ninguno.
**Parámetros de ruta / query:** Ninguno.

**Respuesta exitosa — 200 OK**
```json
{ "status": "UP" }
```

**Errores posibles:** `503` si la aplicación o una dependencia requerida no está saludable.

**Ejemplo curl**
```bash
curl "$BASE_URL/actuator/health"
```

## 5. WebSocket

**Estado:** Implementado (Fase 4). STOMP 1.2 sobre WebSocket, broker simple en memoria; los comandos continúan en REST.

**URL de conexión:** `ws://localhost:8080/ws`.

Autentica en el frame `CONNECT` mediante el header STOMP nativo `Authorization: Bearer <jwt>` (no como header HTTP del handshake). Si falta o no es válido, se rechaza la conexión. Ejemplo de frames STOMP (cada frame termina con NUL):

```text
CONNECT
accept-version:1.2
heart-beat:0,0
Authorization:Bearer <jwt>

\0

SUBSCRIBE
id:alerts
destination:/user/queue/alerts
ack:auto

\0
```

**Autorización de suscripción:** `CITIZEN` puede escuchar `/user/queue/alerts` y `/topic/alerts/{id}` solo si la alerta pertenece a su cuenta; `UNIT` puede escuchar `/user/queue/dispatches` y el topic solo si tiene asignada esa alerta. Otros destinos y frames cliente `SEND` se rechazan.

| Destino | Suscriptor | Mensaje |
|---------|------------|---------|
| `/user/queue/dispatches` | `UNIT` | `PendingDispatchResponse` al crear oferta, o `DispatchUpdateNotification` al cancelar/expirar. |
| `/user/queue/alerts` | `CITIZEN` | `AlertStatusNotification` tras crear, aceptar, cancelar, llegar, resolver o expirar. |
| `/topic/alerts/{alertId}` | Ciudadano dueño y unidad asignada | `AlertStatusNotification` y `UnitLocationUpdate` mientras la unidad está `EN_ROUTE`. |

**Oferta — `PendingDispatchResponse`**
```json
{
  "dispatchId": "f736d3e1-a714-43ad-9b7a-49f63fd295fa",
  "alertId": "62320253-f1bd-4466-a031-82dc997c7b96",
  "type": "ROBBERY",
  "requiredUnitType": "POLICE",
  "description": "Asalto cerca del parque",
  "latitude": 19.4167,
  "longitude": -102.0667,
  "round": 1,
  "distanceMeters": 125.4,
  "expiresAt": "2026-11-10T18:00:30Z"
}
```

**Estado de alerta — `AlertStatusNotification`**
```json
{
  "alertId": "62320253-f1bd-4466-a031-82dc997c7b96",
  "eventType": "ALERT_ACCEPTED",
  "alert": {
    "id": "62320253-f1bd-4466-a031-82dc997c7b96",
    "type": "ROBBERY",
    "requiredUnitType": "POLICE",
    "description": "Asalto cerca del parque",
    "latitude": 19.4167,
    "longitude": -102.0667,
    "status": "EN_ROUTE",
    "dispatchRound": 1,
    "createdAt": "2026-11-10T18:00:00Z",
    "acceptedAt": "2026-11-10T18:00:08Z",
    "arrivedAt": null,
    "resolvedAt": null,
    "cancelledAt": null,
    "assignedUnit": {
      "unitId": "27bab08f-c82e-4591-91b5-25ee14407a9e",
      "unitType": "POLICE",
      "identifier": "U-17",
      "latitude": 19.417,
      "longitude": -102.0664,
      "heading": 90.0
    }
  },
  "occurredAt": "2026-11-10T18:00:08Z"
}
```
El objeto `alert` contiene el `AlertResponse` documentado arriba.

**Tracking — `UnitLocationUpdate`**
```json
{
  "alertId": "62320253-f1bd-4466-a031-82dc997c7b96",
  "unitId": "27bab08f-c82e-4591-91b5-25ee14407a9e",
  "latitude": 19.417,
  "longitude": -102.0664,
  "heading": 90.0,
  "speedKmh": 35.5,
  "recordedAt": "2026-11-10T18:01:00Z"
}
```

**Actualización de oferta — `DispatchUpdateNotification`**
```json
{
  "dispatchId": "f736d3e1-a714-43ad-9b7a-49f63fd295fa",
  "alertId": "62320253-f1bd-4466-a031-82dc997c7b96",
  "status": "CANCELLED",
  "reason": "ALERT_ACCEPTED"
}
```

## 6. Flujo de ejemplo de punta a punta

Flujo disponible en Fases 1–3 (registro → login → ubicación → alerta → aceptar → llegada → resolver):

```bash
curl -X POST "$BASE_URL/api/auth/register/citizen" \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@example.com","password":"Secreto123!","fullName":"Ana Pérez"}'

curl -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@example.com","password":"Secreto123!"}'

# Define TOKEN_CIUDADANO con el accessToken de la respuesta de login.
curl "$BASE_URL/api/me" -H "Authorization: Bearer $TOKEN_CIUDADANO"

curl -X POST "$BASE_URL/api/auth/register/unit" \
  -H "Content-Type: application/json" \
  -d '{"email":"unidad@example.com","password":"Secreto123!","fullName":"Unidad U-17","unitType":"POLICE","identifier":"U-17"}'

curl -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"unidad@example.com","password":"Secreto123!"}'
# Define TOKEN_UNIDAD con el accessToken de la respuesta de login de la unidad.

curl -X PUT "$BASE_URL/api/units/me/status" \
  -H "Authorization: Bearer $TOKEN_UNIDAD" -H "Content-Type: application/json" \
  -d '{"status":"AVAILABLE"}'

curl -X POST "$BASE_URL/api/units/me/location" \
  -H "Authorization: Bearer $TOKEN_UNIDAD" -H "Content-Type: application/json" \
  -d '{"eventId":"7e498979-f108-4a4d-86b3-b4f9f0c81e80","latitude":19.4167,"longitude":-102.0667,"recordedAt":"2026-11-09T18:00:00Z"}'

curl "$BASE_URL/api/units/nearby?lat=19.4167&lon=-102.0667&radiusKm=5" \
  -H "Authorization: Bearer $TOKEN_CIUDADANO"

curl -X POST "$BASE_URL/api/alerts" \
  -H "Authorization: Bearer $TOKEN_CIUDADANO" -H "Content-Type: application/json" \
  -d '{"type":"ROBBERY","description":"Asalto cerca del parque","latitude":19.4167,"longitude":-102.0667}'
# Guarda el id de la alerta como ALERT_ID.

curl "$BASE_URL/api/dispatches/pending" -H "Authorization: Bearer $TOKEN_UNIDAD"
# Guarda el dispatchId correspondiente como DISPATCH_ID.

curl -X POST "$BASE_URL/api/dispatches/$DISPATCH_ID/accept" \
  -H "Authorization: Bearer $TOKEN_UNIDAD"

curl "$BASE_URL/api/alerts/active" -H "Authorization: Bearer $TOKEN_CIUDADANO"

curl -X POST "$BASE_URL/api/alerts/$ALERT_ID/arrive" \
  -H "Authorization: Bearer $TOKEN_UNIDAD"

curl -X POST "$BASE_URL/api/alerts/$ALERT_ID/resolve" \
  -H "Authorization: Bearer $TOKEN_UNIDAD"
```

El flujo REST de alertas, aceptación, llegada y resolución está implementado arriba; al aceptar, el ciudadano y la unidad suscritos reciben estado y tracking por WebSocket. La ruta de navegación queda para la Fase 5.

## 7. Changelog

- 2026-11-09 — Fase 0: creado el esqueleto de documentación y convenciones iniciales de la API.
- 2026-11-09 — Fase 1: documentados registro de ciudadano/unidad, login y consulta de perfil.
- 2026-11-09 — Fase 2: documentados estado, ubicación simple/batch idempotente y búsqueda anónima de unidades cercanas.
- 2026-11-10 — Fase 3: documentados creación/cancelación/transiciones de alertas y operaciones de despacho.
- 2026-11-10 — Fase 4: documentados conexión STOMP/JWT, destinos autorizados y mensajes de oferta, estado y tracking.
