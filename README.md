# proyecto-mangateca-api

## Arranque local

### Requisitos

- JDK 25
- Docker en ejecución, con `docker compose`

No hace falta nada más: no se instala PostgreSQL ni Maven (se usa el wrapper `./mvnw`).

### Comando

```bash
./mvnw spring-boot:run
```

### Qué ocurre

1. Docker Compose levanta el contenedor `postgres:18` definido en `compose.yaml`.
2. Flyway aplica la migración V1 sobre la base de datos.
3. La API escucha en `http://localhost:8080`.

### Detener la aplicación

Pulsa `Ctrl+C`. Al apagarse la aplicación también se detiene el contenedor de PostgreSQL
(no se elimina, así que los datos persisten entre reinicios).

### Sin Docker

Si Docker no está en ejecución o no tiene `docker compose`, el arranque falla con un mensaje
que indica el requisito ausente.

## Configuración por entorno

En local (perfil `dev`, activado por `./mvnw spring-boot:run`) no hace falta ninguna variable:
la base es el contenedor de `compose.yaml`, el JWT usa un par RSA efímero en memoria y CORS
admite `http://localhost:5173`. Ningún secreto se versiona.

Fuera de dev, la configuración llega por variables de entorno:

| Variable | Obligatoria | Ejemplo | Si falta o es inválida |
|----------|-------------|---------|------------------------|
| `SPRING_DATASOURCE_URL` | Sí | `jdbc:postgresql://db:5432/mangateca` | El arranque falla |
| `SPRING_DATASOURCE_USERNAME` | Sí | `mangateca` | El arranque falla |
| `SPRING_DATASOURCE_PASSWORD` | Sí | *(secreto)* | El arranque falla |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_PUBLIC_KEY_LOCATION` | Sí | `file:/run/secrets/jwt-public.pem` | El arranque falla (no hay `JwtDecoder`) |
| `MANGATECA_CORS_ALLOWED_ORIGINS` | No | `https://app.example.com,https://admin.example.com` | Vacía: ningún origen cruzado permitido. Con `*` o un valor que no sea `scheme://host[:port]`, la aplicación no arranca |

## Pruebas

```bash
./mvnw verify
```

Requiere Docker: la suite levanta una instancia efímera de `postgres:18` con Testcontainers,
independiente de la base de desarrollo. Solo se escriben tests vitales (arquitectura y
arranque, seguridad crítica, errores sin fugas y esquema); no hay umbral de cobertura. El build
falla ante cualquier advertencia de compilación.

## Decisiones de arquitectura

Los ADR de las dependencias y herramientas adoptadas están en [`docs/adr/`](docs/adr/).
