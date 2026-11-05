# Feature Specification: Identity & Access

**Feature Branch**: `002-identity-access`

**Created**: 2026-11-05

**Status**: Draft

**Input**: User description: "002 - Identity & Access: registro de clientes, inicio de sesión, renovación y cierre de sesión, identidad actual y primer administrador."

## Contexto

La plataforma base (feature 001) ya ofrece persistencia versionada, errores ProblemDetail, seguridad deny-by-default y verificación modular. Esta feature permite que las personas creen una cuenta, se autentiquen y obtengan una identidad con rol, de modo que las features siguientes (carrito, órdenes, administración) puedan proteger sus recursos. La autenticación es la superficie de ataque más expuesta del sistema: debe resistir la enumeración de cuentas, la fuerza bruta y el robo de sesiones.

**Actores**:

- **Invitado**: visitante sin cuenta ni sesión.
- **Cliente**: persona registrada que compra en la tienda (rol `CUSTOMER`).
- **Administrador**: gestiona la tienda (rol `ADMIN`); nunca se crea por registro público.
- **Consumidor de la API**: el frontend React u otro cliente HTTP.

## Clarifications

### Session 2026-11-05

- Q: Registro enviando rol `ADMIN`: ¿rechazar o ignorar el campo? → A: Rechazar con error de validación (400, ProblemDetail) y no crear la cuenta.
- Q: ¿Qué responde el inicio de sesión ante una cuenta bloqueada? → A: La misma respuesta genérica de credenciales inválidas (401), sin indicar el bloqueo ni el tiempo restante.
- Q: ¿Qué responde el inicio de sesión ante una cuenta deshabilitada? → A: La misma respuesta genérica de credenciales inválidas (401).
- Q: ¿Cómo se comporta el cierre de sesión con token de renovación ausente, desconocido o expirado, y requiere token de acceso? → A: Es público, idempotente y siempre responde 204; no requiere token de acceso.
- Q: ¿Límites del nombre visible? → A: Entre 1 y 100 caracteres tras recortar espacios.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registro de cliente (Priority: P1)

Como invitado, quiero crear una cuenta con mi correo, nombre y contraseña, para poder comprar y ver mi historial.

**Why this priority**: Sin cuentas no existe ninguna otra capacidad de la tienda (carrito persistente, órdenes, historial).

**Independent Test**: Enviar un registro válido y comprobar que la cuenta existe con rol de cliente y que se publicó el evento "usuario registrado".

**Acceptance Scenarios**:

```gherkin
Scenario: Registro exitoso
  Given un invitado sin cuenta
  When se registra con correo, nombre visible y contraseña válidos
  Then la cuenta queda creada con rol CUSTOMER
  And la respuesta no incluye la contraseña
  And se publica el evento de dominio "usuario registrado"

Scenario: Registro con correo ya existente
  Given existe una cuenta con el correo "ana@example.com"
  When un invitado intenta registrarse con "ANA@Example.com"
  Then el registro es rechazado indicando que el correo ya está en uso

Scenario Outline: Registro con datos inválidos
  When un invitado intenta registrarse con <dato inválido>
  Then el registro es rechazado con el detalle de validación del campo afectado

  Examples:
    | dato inválido                        |
    | una contraseña de 7 caracteres       |
    | una contraseña de 65 caracteres      |
    | un correo con formato inválido       |
    | un nombre visible vacío              |
    | un nombre visible de 101 caracteres  |

Scenario: Intento de elegir rol ADMIN
  When un invitado se registra enviando el rol "ADMIN"
  Then la petición es rechazada con error de validación
  And no se crea ninguna cuenta
```

---

### User Story 2 - Inicio de sesión (Priority: P1)

Como cliente o administrador, quiero iniciar sesión con correo y contraseña, para acceder a los recursos protegidos.

**Why this priority**: Es la puerta de entrada a todo recurso protegido; junto con el registro forma el MVP.

**Independent Test**: Iniciar sesión con una cuenta existente y usar el token de acceso obtenido contra un endpoint protegido.

**Acceptance Scenarios**:

```gherkin
Scenario: Inicio de sesión exitoso
  Given una cuenta activa con credenciales conocidas
  When la persona inicia sesión con correo y contraseña correctos
  Then recibe un token de acceso de 15 minutos y un token de renovación de 7 días

Scenario: Credenciales inválidas indistinguibles
  Given una cuenta existente y un correo que no existe
  When se inicia sesión con contraseña incorrecta para la cuenta existente
  And se inicia sesión con el correo inexistente
  Then ambas respuestas tienen el mismo código y el mismo mensaje

Scenario: Bloqueo tras cinco fallos
  Given una cuenta activa
  When se producen 5 intentos fallidos consecutivos
  Then la cuenta queda bloqueada durante 15 minutos
  And un sexto intento con la contraseña correcta es rechazado hasta que vence el bloqueo
  And la respuesta es idéntica a la de credenciales inválidas

Scenario: Un inicio exitoso reinicia el contador
  Given una cuenta con 4 intentos fallidos consecutivos
  When la persona inicia sesión con la contraseña correcta
  Then el contador de fallos vuelve a cero

Scenario: Cuenta deshabilitada
  Given una cuenta deshabilitada
  When intenta iniciar sesión con credenciales correctas
  Then el inicio de sesión es rechazado con la misma respuesta que unas credenciales inválidas
```

---

### User Story 3 - Renovación y cierre de sesión (Priority: P2)

Como cliente, quiero mantener mi sesión activa sin volver a escribir mi contraseña, y poder cerrarla cuando quiera.

**Why this priority**: Mejora la experiencia y limita la exposición del token de acceso, pero el sistema es usable sin ella (re-login cada 15 minutos).

**Independent Test**: Iniciar sesión, renovar, comprobar que el token anterior ya no sirve; cerrar sesión y comprobar que el token de renovación queda inservible.

**Acceptance Scenarios**:

```gherkin
Scenario: Renovación válida
  Given una sesión con un token de renovación vigente
  When se solicita la renovación
  Then se entregan un nuevo token de acceso y un nuevo token de renovación
  And el token de renovación anterior deja de servir

Scenario: Reutilización de un token rotado
  Given un token de renovación que ya fue rotado
  When alguien intenta usarlo de nuevo
  Then la petición es rechazada
  And todos los tokens de renovación de esa sesión quedan invalidados

Scenario: Cierre de sesión
  Given una sesión con un token de renovación vigente
  When la persona cierra sesión
  Then ese token de renovación ya no sirve para renovar

Scenario: Cierre de sesión idempotente
  When se cierra sesión con un token de renovación ausente, desconocido o expirado
  Then la respuesta es la misma que con un token vigente (204)
```

---

### User Story 4 - Identidad actual (Priority: P2)

Como cliente o administrador autenticado, quiero consultar mis datos de cuenta, para que el frontend muestre quién soy.

**Why this priority**: Necesario para que el frontend muestre la identidad y sirve de recurso protegido de referencia.

**Independent Test**: Consultar la identidad con un token válido, uno expirado y sin token.

**Acceptance Scenarios**:

```gherkin
Scenario: Consulta con token válido
  Given un token de acceso vigente
  When se consulta la identidad actual
  Then se devuelven identificador, correo, nombre visible y rol
  And no se devuelve ningún dato sensible

Scenario: Consulta con token expirado
  Given un token de acceso expirado
  When se consulta la identidad actual
  Then la respuesta es 401 en formato ProblemDetail

Scenario: Consulta sin token
  When se consulta la identidad actual sin credenciales
  Then la respuesta es 401 en formato ProblemDetail

Scenario: Cliente accede a recurso de administrador
  Given un token de acceso de un CUSTOMER
  When intenta acceder a un recurso reservado para ADMIN
  Then la respuesta es 403
```

---

### User Story 5 - Primer administrador (Priority: P3)

Como responsable de la plataforma, quiero que exista un administrador inicial sin abrir el registro público a ese rol.

**Why this priority**: Es necesario para operar la tienda, pero solo se ejecuta una vez y no bloquea el flujo de clientes.

**Independent Test**: Arrancar la aplicación con la configuración del entorno y verificar que existe exactamente un administrador, incluso tras reiniciar.

**Acceptance Scenarios**:

```gherkin
Scenario: Arranque con configuración de administrador inicial
  Given no existe ningún administrador
  And el entorno define las credenciales del administrador inicial
  When la aplicación arranca
  Then se crea un administrador

Scenario: Reinicio de la aplicación
  Given ya existe un administrador creado por el arranque
  When la aplicación se reinicia con la misma configuración
  Then no se crea ningún administrador adicional

Scenario: Arranque sin configuración
  Given no existe ningún administrador
  And el entorno no define credenciales de administrador inicial
  When la aplicación arranca
  Then no se crea ningún administrador
```

---

### Edge Cases

- Registro concurrente del mismo correo (con distinta capitalización): solo una cuenta puede crearse; la otra petición recibe el rechazo de correo en uso.
- Correo con espacios al inicio o al final: se normaliza antes de validar y comparar.
- Intentos fallidos sobre un correo inexistente: no deben revelar la existencia de la cuenta ni por contenido ni por forma de la respuesta.
- Intento de inicio de sesión sobre una cuenta bloqueada con contraseña incorrecta: misma respuesta indistinguible que un fallo normal.
- Token de renovación expirado, malformado o desconocido: rechazado con la misma respuesta genérica.
- Token de acceso aún vigente tras cerrar sesión: sigue siendo válido hasta su expiración (máx. 15 minutos); solo el token de renovación se invalida.
- Cuenta deshabilitada que intenta renovar: la renovación es rechazada.
- Configuración de administrador inicial incompleta o con contraseña fuera del rango permitido: el arranque no crea administrador y no expone la contraseña en logs.
- Reinicio simultáneo de varias instancias con la configuración de administrador: solo se crea un administrador.

## Requirements *(mandatory)*

### Functional Requirements

**Registro (US1)**

- **FR-001**: El registro MUST requerir correo, nombre visible (1 a 100 caracteres tras recortar espacios) y contraseña.
- **FR-002**: El correo MUST ser único sin distinguir mayúsculas de minúsculas y MUST validarse su formato.
- **FR-003**: La contraseña MUST tener entre 8 y 64 caracteres; NO MUST imponerse reglas de composición obligatorias.
- **FR-004**: La contraseña MUST NOT almacenarse ni devolverse en texto plano; MUST guardarse con un algoritmo de hash adaptativo.
- **FR-005**: Toda cuenta creada por registro público MUST tener rol `CUSTOMER`; el registro MUST NOT permitir elegir rol: una petición que incluya un rol MUST rechazarse con error de validación sin crear la cuenta.
- **FR-006**: Al completarse un registro, el sistema MUST publicar un evento de dominio "usuario registrado" para que otros módulos puedan reaccionar.

**Inicio de sesión (US2)**

- **FR-007**: Con credenciales válidas, el sistema MUST entregar un token de acceso de 15 minutos y un token de renovación de 7 días.
- **FR-008**: El token de acceso MUST identificar al usuario y su rol, y MUST ser aceptado por todos los endpoints protegidos de la API.
- **FR-009**: Con credenciales inválidas, la respuesta MUST ser idéntica exista o no el correo (mismo código, mismo mensaje). La misma respuesta MUST usarse para cuentas bloqueadas (FR-010) y deshabilitadas (FR-011), de modo que el estado de la cuenta no sea observable.
- **FR-010**: Tras 5 intentos fallidos consecutivos, la cuenta MUST bloquearse temporalmente 15 minutos; un inicio de sesión exitoso MUST reiniciar el contador.
- **FR-011**: Una cuenta deshabilitada MUST NOT poder iniciar sesión.

**Renovación y cierre de sesión (US3)**

- **FR-012**: Con un token de renovación válido, el sistema MUST entregar un nuevo token de acceso y un nuevo token de renovación; el anterior MUST quedar invalidado (rotación).
- **FR-013**: Si se intenta reutilizar un token de renovación ya rotado, el sistema MUST tratarlo como robo: invalidar todos los tokens de renovación de esa sesión y rechazar la petición.
- **FR-014**: Al cerrar sesión, el token de renovación entregado MUST quedar invalidado. El cierre de sesión MUST ser idempotente: con un token ausente, desconocido o expirado responde igual (204) y no requiere token de acceso.
- **FR-015**: Los tokens de renovación MUST almacenarse de forma que una filtración de la base de datos no permita usarlos.

**Identidad actual (US4)**

- **FR-016**: Un usuario autenticado MUST poder consultar su identificador, correo, nombre visible y rol; la respuesta MUST NOT incluir datos sensibles.

**Primer administrador (US5)**

- **FR-017**: El sistema MUST provisionar un administrador inicial a partir de configuración del entorno, solo si aún no existe ninguno; sin esa configuración, el arranque MUST NOT crear administradores.
- **FR-018**: Las credenciales del administrador inicial MUST NOT quedar escritas en el repositorio ni en las migraciones.

**Transversales**

- **FR-019**: Los endpoints de registro, inicio de sesión, renovación y cierre de sesión MUST ser públicos; todo lo demás de esta feature MUST requerir autenticación.
- **FR-020**: Todos los errores MUST responder en formato ProblemDetail, sin exponer trazas, y sin revelar si un correo existe salvo en el registro (correo ya en uso).
- **FR-021**: Los recursos protegidos de features futuras MUST poder restringirse por rol (`CUSTOMER`, `ADMIN`) a partir del token emitido aquí; un `CUSTOMER` que acceda a un recurso reservado para `ADMIN` MUST recibir 403.

### Key Entities

- **Usuario**: persona con cuenta. Atributos: identificador, correo (único, sin distinguir capitalización), nombre visible, credencial protegida, rol (`CUSTOMER` o `ADMIN`), estado (habilitada/deshabilitada), contador de intentos fallidos y fin de bloqueo temporal.
- **Sesión**: cadena de renovación asociada a un inicio de sesión; agrupa los tokens de renovación sucesivos para poder invalidarlos todos ante reutilización.
- **Token de renovación**: credencial opaca de larga duración, de un solo uso, almacenada de forma irrecuperable; pertenece a una sesión y a un usuario; puede estar vigente, rotado, revocado o expirado.
- **Evento "usuario registrado"**: hecho de dominio con el identificador del usuario y su rol, para consumo de otros módulos; no contiene datos sensibles.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un invitado puede registrarse e iniciar sesión en menos de un minuto desde el frontend.
- **SC-002**: Ninguna respuesta de la API permite saber si un correo está registrado, salvo el error explícito de registro (verificable comparando respuestas de correo existente vs. inexistente en inicio de sesión y renovación).
- **SC-003**: El 100% de los endpoints de features futuras puede proteger recursos por rol usando el token emitido aquí.
- **SC-004**: Tras 5 fallos consecutivos, el 100% de los intentos durante los siguientes 15 minutos son rechazados, incluso con la contraseña correcta.
- **SC-005**: El 100% de los intentos de reutilizar un token de renovación rotado son rechazados y dejan la sesión completa sin capacidad de renovar.
- **SC-006**: En ningún caso una contraseña, un token de renovación en claro o un hash de contraseña aparece en respuestas, logs o en la base de datos de forma utilizable.
- **SC-007**: Reiniciar la aplicación cualquier número de veces con la misma configuración deja exactamente un administrador inicial.

## Assumptions

- El rol de cliente se denomina `CUSTOMER` en el sistema, conforme a la Constitución (V); en el enunciado original aparece como "CLIENTE".
- El correo se normaliza (recorte de espacios y comparación sin distinguir capitalización) antes de validar y comparar.
- El bloqueo temporal es por cuenta (no por IP); la limitación de tasa por IP está fuera de alcance.
- El cierre de sesión invalida únicamente el token de renovación; el token de acceso ya emitido expira por sí solo (máx. 15 minutos).
- Por la Constitución (V), el token de renovación es opaco y viaja en una cookie inaccesible para scripts del cliente, y el token de acceso viaja en el encabezado `Authorization: Bearer`. Los detalles de transporte se definen en el plan.
- El acceso anónimo a los endpoints de registro, inicio de sesión, renovación y cierre de sesión (FR-019) está permitido por la Constitución v4.0.0 (Principio V), que los enumera explícitamente junto con la lectura de catálogo y reseñas.
- Un administrador deshabilitado o bloqueado sigue las mismas reglas que un cliente (FR-010, FR-011).
- Las credenciales del administrador inicial se inyectan por variables de entorno (Higiene Operacional de la Constitución).
- Fuera de alcance: edición de perfil y direcciones (feature 009), gestión de usuarios por el administrador (feature 010), recuperación de contraseña, verificación de correo, inicio de sesión social, autenticación multifactor y limitación de tasa por IP.
- Dependencia: la plataforma base de la feature 001 (persistencia, errores ProblemDetail, seguridad deny-by-default, verificación modular).
