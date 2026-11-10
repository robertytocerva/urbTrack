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
| 405 | `METHOD_NOT_ALLOWED` | Método HTTP no permitido para la ruta. |
| 500 | `INTERNAL_ERROR` | Error inesperado. |

## 3. Índice de endpoints

### Auth
- `POST /api/auth/register/citizen`
- `POST /api/auth/register/unit`
- `POST /api/auth/login`
- `GET /api/me`

### Ciudadano, unidades, alertas y despacho
- Sin endpoints implementados todavía (Fase 2 en adelante).

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

Flujo implementado en esta fase (registro → login → consulta de perfil):

```bash
curl -X POST "$BASE_URL/api/auth/register/citizen" \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@example.com","password":"Secreto123!","fullName":"Ana Pérez"}'

curl -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@example.com","password":"Secreto123!"}'

curl "$BASE_URL/api/me" -H "Authorization: Bearer $TOKEN"
```

Ubicación, alerta, aceptación, tracking y resolución se implementarán en fases posteriores.

## 7. Changelog

- 2026-11-09 — Fase 0: creado el esqueleto de documentación y convenciones iniciales de la API.
- 2026-11-09 — Fase 1: documentados registro de ciudadano/unidad, login y consulta de perfil.
