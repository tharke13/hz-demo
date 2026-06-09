# Hazelcast Cluster Demo Progress

## Status

Current state: the demo is implemented as an append-only annotation cache backed by Hazelcast and MongoDB, with both local Compose and automated Testcontainers support working.

## Kept Decisions

- The application connects to Hazelcast as a client.
- Docker Compose is the manual local exploration environment.
- Testcontainers is the automated integration test environment.
- The local and test cluster target is 3 Hazelcast members.
- Docker Compose and Testcontainers use the same custom Hazelcast member image.
- The cache stores document-to-annotation relationships in two maps:
  - `document-annotation-ids`: `documentId -> List<String annotationId>`
  - `annotation-objects`: `annotationId -> Annotation`
- Hazelcast uses Mongo-backed lazy loading and write-through persistence for both maps.
- The service layer owns the two-phase write and read coordination across the two maps.
- Split-brain recovery for the document index map merges both id lists and deduplicates annotation ids while preserving order.
- The public annotation API is append/read-only.
- The public API exposes cache statistics for both configured Hazelcast maps.
- Management Center stays enabled for local inspection and is exposed on host port `8181`.
- The Spring Boot `dev` profile targets the local Compose ports.
- Member-side Hazelcast runtime code lives in `hz-demo-cache-service` and is packaged for both Compose and Testcontainers member startup paths.

## Implemented Model

- Shared annotation model: `Annotation { String id, int start, int end, String value }`
- `Annotation.id` is derived on construction/build from a Murmur3 hash of `start`, `end`, and `value`.
- Hazelcast document index map: `document-annotation-ids`
- Hazelcast annotation object map: `annotation-objects`
- Document index key/value: `documentId -> List<String annotationId>`
- Annotation object key/value: `annotationId -> Annotation`
- Persistence stores:
  - MongoDB collection `document-annotation-ids`
  - MongoDB collection `annotation-objects`

## Implemented Behavior

- `POST /api/annotations/{documentId}` appends one annotation and returns the full updated list.
- `POST /api/annotations/{documentId}/batch` appends multiple annotations in one call and returns the full updated list.
- `GET /api/annotations/{documentId}` returns the current annotation list for a document.
- `GET /api/annotations/cluster` returns Hazelcast cluster connectivity details.
- `GET /api/annotations/cache-stats` returns cache statistics for both configured maps.
- `GET /api/annotations/cache-stats/{mapName}` returns cache statistics for one map.
- Appends are coordinated in the service layer under a document-level Hazelcast lock.
- Append write order is:
  - write or reuse the `annotationId -> Annotation` entry
  - update the `documentId -> List<annotationId>` index once with the appended ids in order
  - reconstruct and return the full ordered `List<Annotation>`
- Batch append reuses the same document lock and two-map coordination, so one call can append multiple annotations without resubmitting the existing list.
- Reads are coordinated in the service layer under the same document-level Hazelcast lock.
- Mongo-backed lazy loading on Hazelcast members is enabled for both maps.
- Cache statistics are gathered by dispatching a member-side callable to every Hazelcast member and returning each member's `LocalMapStats` plus the cluster-wide map size.
- Hazelcast split-brain merge uses `AnnotationIdListUnionMergePolicy` for the document index map, which preserves the existing side's order and appends only new annotation ids.
- Full-key enumeration from Mongo is explicitly disabled; `loadAllKeys()` returns an empty iterable and never scans Mongo.
- Swagger UI is enabled and the application logs the resolved Swagger URL on startup.

## Failure Cases

- If the second phase of append fails after creating a new `annotationId -> Annotation` entry, the service performs a best-effort rollback by removing that newly created annotation object.
- Concurrent appends to the same `documentId` are serialized by the service using a Hazelcast document lock, but callers still only receive the list state as of their own append.
- Reads depend on both maps being available; if the document index references an annotation id whose object is missing, the service fails the read with an inconsistency error.
- The first read or append for a document that exists only in Mongo requires loading both the document index entry and any referenced annotation objects through Hazelcast map loaders.
- Split-brain deduplication is hash-based; if two distinct annotations ever collide on the derived Murmur3 id, the merge policy will treat them as duplicates.
- This design assumes append-only semantics for the public annotation API; if future code mutates cached lists outside the append path, Mongo persistence behavior will no longer match the intended contract.
- `loadAllKeys()` is intentionally disabled, so any future feature that expects Hazelcast to enumerate all persisted document IDs through this loader will not work without redesign.

## Runtime Shape

- `compose.yaml` starts MongoDB, 3 Hazelcast members, and Hazelcast Management Center.
- The Hazelcast member image now bakes in both the member config and member-side runtime classes, so Compose and Testcontainers start members the same way.
- Local Compose startup should use `docker compose up -d --build` after member image changes so the running cluster does not stay on a stale image.
- Local Spring Boot dev profile runs on `8080`.
- Local Hazelcast member ports are `5701`, `5702`, and `5703`.
- Hazelcast Management Center runs on `8181`.

## Verification

- `./mvnw test` on 2026-06-08
- Result: `BUILD SUCCESS`
- Reactor modules passing:
  - `hz-demo-models`
  - `hz-demo-cache-service`
  - `hz-demo-client`
- Verified behaviors:
  - derived annotation ids are stable for identical `start`/`end`/`value` inputs
  - append writes the annotation object map entry and the document index map entry, then returns the full ordered `List<Annotation>`
  - batch append writes or reuses multiple annotation object map entries, updates the document index once, and returns the full ordered `List<Annotation>`
  - Mongo-backed lazy load returns existing document index entries and annotation object entries
  - append works for a document that exists only in Mongo and is not preloaded into Hazelcast
  - batch append works for a document that exists only in Mongo and is not preloaded into Hazelcast
  - cache-service unit coverage confirms the document index map store persists ordered annotation id lists
  - cache-service unit coverage confirms the annotation object map store persists annotation objects by derived annotation id
  - cache-service unit coverage confirms service-layer append rolls back a newly created annotation object if the document index write fails
  - cache-service unit coverage confirms service-layer batch append rolls back only newly created annotation objects if the document index write fails
  - cache-service unit coverage confirms cache statistics return the configured map name, cluster-wide entry count, and sorted per-member stats
  - cache-service unit coverage confirms split-brain merge unions document index id lists and deduplicates by annotation id
  - Testcontainers builds and runs the same Hazelcast member Dockerfile used by Compose
  - repeated appends leave the final cached list complete and ordered
  - controller `GET /api/annotations/{documentId}` returns `200 []` for `Optional.of(emptyList())` and `404` for `Optional.empty()`
  - controller `POST /api/annotations/{documentId}/batch` returns the latest full list and rejects an empty payload
  - controller cache-stats endpoints return serialized cache statistics responses
  - integration coverage confirms cache-stats reports both configured maps and returns 3 member-stat snapshots per map in a live cluster
  - both member-side loaders return no keys from `loadAllKeys()`, so Hazelcast cannot scan Mongo document IDs or annotation IDs through these components
