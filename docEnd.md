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
- Alertas y despacho: sin endpoints implementados todavía (Fase 3 en adelante).

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

No implementado en esta fase. Se documentará en Fase 4.

## 6. Flujo de ejemplo de punta a punta

Flujo disponible en Fases 1–2 (registro → login → estado → ubicación → búsqueda cercana):

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
```

Alertas, aceptación, tracking WebSocket y resolución se implementarán en fases posteriores.

## 7. Changelog

- 2026-11-09 — Fase 0: creado el esqueleto de documentación y convenciones iniciales de la API.
- 2026-11-09 — Fase 1: documentados registro de ciudadano/unidad, login y consulta de perfil.
- 2026-11-09 — Fase 2: documentados estado, ubicación simple/batch idempotente y búsqueda anónima de unidades cercanas.
