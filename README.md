# Hazelcast Demo Help

## Overview

This repo is a small multi-module Hazelcast demo built around an append-only annotation workflow.

- `hz-demo-models`: shared DTOs
- `hz-demo-cache-service`: Hazelcast client integration, member-side map stores, merge policy
- `hz-demo-client`: Spring Boot client app for testing cache service with HTTP API

The application runs as a Hazelcast client. The local demo cluster is 3 Hazelcast members plus MongoDB, RabbitMQ, and Hazelcast Management Center.
Compose and Testcontainers use the same custom Hazelcast member image defined in `docker/hazelcast-member/Dockerfile`.

## Local Runtime

Start the local cluster:

```bash
docker compose up -d --build
```

Use `--build` when member config or member-side cache code has changed, because the Hazelcast members now run from the custom image rather than a bind-mounted config.

Stop it:

```bash
docker compose down
```

Ports:

- Spring Boot app: `8080` when using the `dev` profile
- Hazelcast members: `5701`, `5702`, `5703`
- Hazelcast Management Center: `8181`
- MongoDB: `27017`
- RabbitMQ AMQP: `5672`
- RabbitMQ Management UI: `15672`

## Run The App

Run the client app against the local Compose cluster:

```bash
./mvnw -pl hz-demo-client spring-boot:run -Dspring-boot.run.profiles=dev
```

Swagger UI:

- `http://localhost:8080/swagger-ui.html`

## Build And Test

Run the full multi-module test suite:

```bash
./mvnw test
```

Build without tests:

```bash
./mvnw -DskipTests package
```

The integration tests use Testcontainers and do not depend on `docker compose`.
They build and run the same Hazelcast member Dockerfile used by the local Compose cluster.

## API

Base path:

- `/api/annotations`

Endpoints:

- `POST /api/annotations/{documentId}`
  - append one annotation
- `POST /api/annotations/{documentId}/batch`
  - append multiple annotations in one request
- `GET /api/annotations/{documentId}`
  - return the current annotation list
- `GET /api/annotations/cluster`
  - return Hazelcast cluster details
- `GET /api/annotations/cache-stats`
  - return cache statistics for both configured maps
- `GET /api/annotations/cache-stats/{mapName}`
  - return cache statistics for a single map

Example single append payload:

```json
{
  "start": 0,
  "end": 4,
  "value": "PERSON"
}
```

Example batch append payload:

```json
[
  {
    "start": 0,
    "end": 4,
    "value": "PERSON"
  },
  {
    "start": 10,
    "end": 16,
    "value": "LOCATION"
  }
]
```

`Annotation.id` is derived automatically from `start`, `end`, and `value`.

## Data Model

The cache uses two Hazelcast maps:

- `document-annotation-ids`
  - `documentId -> List<String annotationId>`
  - lazy-loaded from and written through to MongoDB
- `annotation-objects`
  - `annotationId -> Annotation`
  - lazy-loaded from MongoDB
  - writes publish persistent JSON messages to the durable RabbitMQ topic exchange `hz-demo.annotation-objects`

The Spring app binds the durable queue `hz-demo.annotation-objects.mongo` to that topic with routing key `annotation.objects`.
Its Rabbit listener consumes annotation object messages and upserts them into the MongoDB `annotation-objects` collection, while external consumers can bind their own durable queues to the same exchange.

The service layer coordinates writes across both maps under a document-level Hazelcast lock.
Cache statistics are collected by executing a member-side callable on each Hazelcast member and combining the returned `LocalMapStats` with the cluster-wide map size.

## Notes

- Java baseline is `11`.
- On Java `24+`, the Maven build enables `--enable-native-access=ALL-UNNAMED` automatically to suppress Tomcat native-access warnings.
- `loadAllKeys()` is intentionally disabled in the map stores, so Hazelcast cannot scan all persisted document or annotation keys from MongoDB.
