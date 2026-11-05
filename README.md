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
