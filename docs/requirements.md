# Hazelcast Cluster Demo Requirements

## Objective

Use this project to demonstrate and explore Hazelcast cache cluster concepts in a small, repeatable Spring Boot application.

Reviewed: 2026-06-08. No requirement changes were needed after switching the demo payload to annotation lists, deriving stable annotation ids from content, splitting storage into a document index map plus an annotation object map, coordinating the two-phase append/read flow in the service layer, supporting both single and batch append operations, exposing cache-statistics endpoints, trimming the annotation API to append/read operations, and aligning Testcontainers member startup with the same Docker image used by Compose.

## Scope

- Build a Spring Boot application that can connect to or host a Hazelcast-backed cache.
- Support local cluster exploration with Docker Compose via `compose.yaml`.
- Use Testcontainers for automated tests instead of relying on a manually started local cluster.
- Keep the initial cluster intentionally small so behavior is easy to reason about during demos.

## Functional Requirements

### Application

- The application must start cleanly with the current Spring Boot baseline.
- The application must act as a Hazelcast client, not a cluster member.
- The application must expose at least one simple workflow that reads and writes cached data.
- The application must include a sample controller and a service that uses the Hazelcast cache.
- Cache access should be implemented through the service layer.
- The application must be able to run against a Hazelcast cluster configuration suitable for local demos.

### Local Cluster

- `compose.yaml` should define a small Hazelcast cluster for manual exploration.
- The local cluster should use 3 Hazelcast members.
- The local environment should include management or inspection tooling.
- Local startup should be simple enough for demos: one command to start, one command to stop.

### Testing

- Automated integration tests must use Hazelcast Testcontainers.
- Tests should start a small cluster, not a single standalone node, so cluster behavior can be exercised.
- The test cluster target is 3 members unless container support forces a different practical setup.
- Tests must run without depending on Docker Compose.

### Demo and Exploration

- The codebase should make it easy to explore cluster membership, data distribution, replication, and failover behavior.
- Configuration should stay explicit and easy to inspect rather than overly abstracted.
- The project should be structured so later experiments can add near-cache, eviction, backup count, split-brain handling, and client-vs-member comparisons.

## Non-Functional Requirements

- Favor reproducibility over cleverness.
- Keep dependencies minimal and aligned with Spring Boot and Hazelcast support.
- Prefer configurations that are easy to explain live.
- Document assumptions and tradeoffs as the project evolves.
- Ensure progress.md file is up to date after each request as well as this file

## Acceptance Criteria

- There are project trackers for requirements and implementation progress.
- The repo includes a documented plan for Docker Compose local cluster usage.
- The repo includes a documented plan for Hazelcast Testcontainers-based integration testing with a small cluster.
- Future implementation work can be tracked against explicit milestones rather than ad hoc notes.

## Confirmed Decisions

- The application connects to Hazelcast as a client.
- Cache interaction is implemented in the service layer and exercised through a sample controller.
- `compose.yaml` should include 3 Hazelcast members and management or inspection tooling.
- The first demo should prioritize replication and failover behavior.
