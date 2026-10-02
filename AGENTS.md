# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project overview

`hmpps-subject-access-request-html-renderer` is a Kotlin/Spring Boot service (based on the
[hmpps-template-kotlin](https://github.com/ministryofjustice/hmpps-template-kotlin) template) that renders
HTML reports for HMPPS Subject Access Requests. It fetches subject data from upstream services and the
subject access request document store, applies Handlebars/Mustache templates, and produces HTML output.

## Tech stack

- Kotlin, Spring Boot (webflux + webmvc), built with Gradle (`gradlew`)
- JVM toolchain 25
- Flyway migrations, PostgreSQL (runtime) / H2 (tests), Spring Data JPA
- Handlebars (`com.github.jknack:handlebars`) and Spring Mustache for HTML templating
- AWS S3 client (`aws.sdk.kotlin:s3`) for document storage
- WireMock for integration test stubs

## Repository layout

- `src/main/kotlin/.../subjectaccessrequesthtmlrenderer/`
  - `backlog`, `client`, `config`, `controller`, `documentstore`, `exception`, `health`,
    `models`, `rendering`, `repository`, `service`, `template` — main application packages
- `src/main/resources/templates` — Handlebars/Mustache HTML templates
- `src/main/resources/db/{sar,sar_h2,sar_postgresql}` — Flyway migrations
- `src/test/kotlin/...` — unit and integration tests (mirrors main package layout, plus `integration/`)
- `src/test/resources/integration-tests` — reference HTML stubs and fixtures used in integration tests
- `helm_deploy/` — Helm chart for Kubernetes deployment

## Build, test, and lint commands

Always use the Gradle wrapper (`./gradlew`), not a system-installed Gradle.

- Build and run all checks (what CI runs): `./gradlew assemble check`
- Run tests only: `./gradlew test`
- Run a single test class: `./gradlew test --tests "uk.gov.justice.digital.hmpps.subjectaccessrequesthtmlrenderer.SomeTestClass"`
- Generate a sample report HTML for manual inspection: `./gradlew generateReport --service=<service-name> [--noData]`

CI (`.github/workflows/pipeline.yml`) runs `./gradlew assemble check`, plus CodeQL, Snyk, OWASP, and
Veracode security scans. Keep changes passing `./gradlew check` before considering work complete.

## Coding conventions

- Follow existing Kotlin style in the codebase; formatting/lint rules come from the
  `uk.gov.justice.hmpps.gradle-spring-boot` plugin (ktlint under the hood) — run `./gradlew check` to catch
  violations rather than hand-formatting.
- Mirror the existing package structure: new main classes go in the matching
  `src/main/kotlin/.../subjectaccessrequesthtmlrenderer/<package>` folder, and tests go in the equivalent
  `src/test/kotlin/...` package.
- Prefer constructor injection and existing patterns in `config`/`service` for new Spring beans.

## Testing guidelines

- Unit tests use JUnit5 + the HMPPS Kotlin test starter.
- Integration tests live under `.../integration/` and use WireMock stubs
  (`src/test/kotlin/.../integration/wiremock`) and reference HTML fixtures
  (`src/test/resources/integration-tests/reference-html-stubs`) — update these fixtures when template
  output intentionally changes.
- Run `./gradlew test` (or `./gradlew check`) after changes and before declaring work complete.

## Security and secrets

- Do not commit real credentials, tokens, or AWS keys. Local/dev config uses placeholder values in
  `application-dev.yml`-style profiles and `docker-compose*.yml`.
- Security scanning config lives in `.snyk`, `dps-gradle-spring-boot-suppressions.xml`, and
  `.github/workflows/security_*.yml` — do not weaken or remove suppressions/ignores without clear
  justification tied to a specific, understood vulnerability.

## Pull requests

- Keep changes scoped and consistent with the HMPPS Kotlin template conventions.
- Update Flyway migrations additively (new versioned migration files) rather than editing already-applied
  migrations.
