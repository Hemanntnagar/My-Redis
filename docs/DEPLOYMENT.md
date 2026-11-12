# Deployment

Run My-Redis as a standalone executable JAR or via Docker Compose. Persistence files (RDB snapshot and optional AOF) live under `MYREDIS_PERSISTENCE_DIRECTORY` (default `./data` locally, `/data` in Docker).

## Configuration

Settings are read from `myredis.*` system properties, then `MYREDIS_*` environment variables, then defaults. Common options:

| Variable | Default | Description |
|----------|---------|-------------|
| `MYREDIS_HOST` | `0.0.0.0` | Bind address |
| `MYREDIS_PORT` | `6379` | TCP listen port (also used inside the container) |
| `MYREDIS_PUBLISH_PORT` | `6379` | Host port in Docker Compose only (see `docker-compose.yml`) |
| `MYREDIS_PERSISTENCE_DIRECTORY` | `./data` (JAR) / `/data` (image) | Snapshot and AOF directory |
| `MYREDIS_AOF_ENABLED` | `false` | Enable append-only file |

## Executable JAR

Build a fat JAR (dependencies included):

```bash
./mvnw -DskipTests package
```

The runnable artifact is `target/my-redis-0.1.0-SNAPSHOT.jar` (original classifier JAR remains beside it if you need the thin JAR).

### Startup (JAR)

```bash
export MYREDIS_HOST=127.0.0.1
export MYREDIS_PORT=6379
export MYREDIS_PERSISTENCE_DIRECTORY="$(pwd)/data"
mkdir -p "$MYREDIS_PERSISTENCE_DIRECTORY"

java -jar target/my-redis-0.1.0-SNAPSHOT.jar
```

Override port or data directory in one line:

```bash
MYREDIS_PORT=6380 MYREDIS_PERSISTENCE_DIRECTORY=/tmp/myredis-data java -jar target/my-redis-0.1.0-SNAPSHOT.jar
```

### Shutdown (JAR)

The server registers a JVM shutdown hook. Stop it with `Ctrl+C`, or send `SIGTERM` to the process (`kill <pid>`). In-flight work is flushed when persistence is enabled; allow a few seconds before `SIGKILL`.

## Docker

Build the image:

```bash
docker build -t my-redis:local .
```

Run with a named volume and published port:

```bash
docker run --rm -d \
  --name myredis \
  -p 6379:6379 \
  -e MYREDIS_PORT=6379 \
  -e MYREDIS_PERSISTENCE_DIRECTORY=/data \
  -v myredis-data:/data \
  my-redis:local
```

Change the host port while keeping the container on 6379:

```bash
docker run --rm -d --name myredis -p 6380:6379 -v myredis-data:/data my-redis:local
```

Stop gracefully (30s timeout by default):

```bash
docker stop myredis
```

## Docker Compose

Start in the background:

```bash
docker compose up -d --build
```

Custom host port and AOF:

```bash
MYREDIS_PUBLISH_PORT=6380 MYREDIS_AOF_ENABLED=true docker compose up -d --build
```

Stop and remove the container (data remains in the `myredis-data` volume):

```bash
docker compose down
```

Remove the persistent volume as well:

```bash
docker compose down -v
```

## Smoke test

With the server listening on port 6379:

```bash
redis-cli -p 6379 PING
redis-cli -p 6379 SET deploy:test ok
redis-cli -p 6379 GET deploy:test
```

Without `redis-cli`, use `nc` or any RESP2 client against `MYREDIS_HOST:MYREDIS_PORT`.
