# Contrato HTTP: superficie expuesta por la plataforma

**Feature**: `001-platform-foundation`

Esta feature no publica endpoints de negocio. Todo error usa
[problem-details.schema.json](problem-details.schema.json) con
`Content-Type: application/problem+json`.

## 1. Matriz de acceso

| Ruta                     | Método | Acceso  | Respuesta esperada                                   |
|--------------------------|--------|---------|------------------------------------------------------|
| `/actuator/health`       | GET    | Público | 200 `{"status":"UP","components":{...}}`; 503 si DOWN |
| `/actuator/info`         | GET    | Público | 200 `{"build":{"name":..., "version":...}}`          |
| `/v3/api-docs`, `/v3/api-docs/**` | GET | Público | 200, documento OpenAPI 3.x (JSON)            |
| `/swagger-ui.html`, `/swagger-ui/**` | GET | Público | 200/302, interfaz web navegable           |
| Cualquier otra ruta      | *      | Autenticado | Sin token o con token inválido: 401. Sin rol: 403 |
| Otros `/actuator/*`      | *      | —       | No expuestos (401 sin token; 404 con token)          |

Las rutas públicas aceptan la petición sin token. No exigen que el token esté ausente.

## 2. Autenticación

- Cabecera: `Authorization: Bearer <JWT>` firmado con RS256.
- 401 → cabecera `WWW-Authenticate: Bearer` + Problem Details con `detail` genérico
  (`"Se requiere autenticación válida."`). El cuerpo no indica la causa (token ausente,
  expirado o con firma inválida).
- 403 → Problem Details con `detail` `"No tiene permisos para acceder a este recurso."`.
- Las autoridades se obtienen del claim `roles` (`CUSTOMER` | `ADMIN`) con prefijo `ROLE_`. Es
  la convención acordada con el futuro módulo de identidad.

## 3. Catálogo de errores

| Situación                                  | Estado | `detail` (orientativo)                          | Extra    |
|--------------------------------------------|--------|-------------------------------------------------|----------|
| JSON malformado / no legible               | 400    | `"El cuerpo de la petición no es válido."`      | —        |
| Validación de DTO fallida                  | 400    | `"La petición contiene campos inválidos."`      | `errors` |
| Sin credenciales o credenciales inválidas  | 401    | ver §2                                          | `WWW-Authenticate` |
| Autenticado sin permisos                   | 403    | ver §2                                          | —        |
| Petición CORS de origen no autorizado      | 403    | `"Origen no autorizado."`                       | sin `Access-Control-Allow-Origin` |
| Ruta inexistente (autenticado)             | 404    | `"Recurso no encontrado."`                      | —        |
| Método no soportado                        | 405    | `"Método no soportado para este recurso."`      | `Allow`  |
| Content-Type no soportado                  | 415    | `"Tipo de contenido no soportado."`             | —        |
| Fallo inesperado                           | 500    | `"Se produjo un error interno."`                | log ERROR en servidor |
| Otros 4xx/5xx del framework (406, 413, 503, `sendError` del contenedor…) | 4xx / 5xx | 4xx: `"La petición no es válida."`; 5xx: `"Se produjo un error interno."` | 5xx: log ERROR en servidor |

> Nota (2026-11-05): la fila "Otros 4xx/5xx del framework" y el rango `400-599` de `status` en
> `problem-details.schema.json` son una ampliación del contrato tras la revisión de la Fase 6.
> Antes el esquema solo admitía 400, 401, 403, 404, 405, 415 y 500.

Los textos exactos de `detail` los fija la implementación. Los tests MUST verificar `status`,
`title`, el `Content-Type` y la ausencia de información interna, no el texto literal.

## 4. CORS

- Orígenes permitidos: `mangateca.cors.allowed-origins` (lista). Nunca `*`.
- Origen permitido: la respuesta incluye `Access-Control-Allow-Origin: <origen exacto>`,
  `Access-Control-Allow-Credentials: true` y `Vary: Origin`.
- Preflight (`OPTIONS` con `Access-Control-Request-Method`) desde un origen permitido: 200
  con `Allow-Methods` `GET, POST, PUT, PATCH, DELETE` y `Allow-Headers`
  `Authorization, Content-Type`. El preflight no requiere token.
- Origen no permitido: 403 Problem Details, sin cabeceras `Access-Control-Allow-*`.

## 5. Ejemplos

```http
GET /api/v1/anything HTTP/1.1

HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer
Content-Type: application/problem+json

{"type":"about:blank","title":"Unauthorized","status":401,
 "detail":"Se requiere autenticación válida.","instance":"/api/v1/anything"}
```

```http
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{"type":"about:blank","title":"Bad Request","status":400,
 "detail":"La petición contiene campos inválidos.","instance":"/api/v1/probes",
 "errors":[{"field":"name","message":"no debe estar vacío"}]}
```
