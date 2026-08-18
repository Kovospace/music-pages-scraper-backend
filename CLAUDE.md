# CLAUDE.md

Scraping REST API: queries bands and band profiles from music sites, caches results in Postgres.
Spring Boot 2.4.4, **Java 8 bytecode**, Gradle 7.1, jsoup for scraping.

## Commands

```bash
sh gradlew compileJava          # NOTE: `sh gradlew`, not `./gradlew` — the wrapper shebang is broken
sh gradlew bootJar -x test
sh gradlew bootRun              # needs a reachable DB, see below
```

There are **no tests** in the repo (`src/test` does not exist). `test { useJUnitPlatform() }` is
configured but has nothing to run — do not report "tests pass" as verification.

Running locally without Postgres:

```bash
SPRING_DATASOURCE_URL='jdbc:h2:mem:dev;DB_CLOSE_DELAY=-1;MODE=PostgreSQL' \
SPRING_DATASOURCE_USERNAME=sa SPRING_DATASOURCE_PASSWORD= \
SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.H2Dialect \
SPRING_JPA_HIBERNATE_DDL_AUTO=none \
sh gradlew bootRun
```

`DDL_AUTO=none` is required on H2: the migration's `text` columns come back as `CLOB`, which the
default `validate` rejects against the entities' `VARCHAR`. Not a problem on real Postgres.

App listens on **4000**. Deploy is a manually dispatched GitHub workflow that calls a shared
pipeline in the `Kovospace/kovostack-github-workflows` repo; the Dockerfile builds with JDK 11 and
runs the jar on a **JRE 8** base image.

## Java 8 constraint

The runtime image is `eclipse-temurin:8-jre-alpine`. Any dependency added must ship Java 8 bytecode
(major version 52) — this is why springdoc is pinned to the `1.6.x` line and not 2.x, and Postgres
driver to `42.2.x`. Check before adding: `unzip -p <jar> <some>.class | od -An -tu1 -j6 -N2`.

## Architecture — the part that is not obvious

Request flow: `MainController` → `*Service` → cache hit? return : scrape, then cache.

Everything platform-specific is resolved **by class-name convention at startup**, not by explicit
wiring. This is the single most important thing to understand before touching anything:

- `application.properties` holds `platforms={'bandzone':'Bandzone', ...}` — slug → class-name prefix.
- `ServiceSelectorBase.getService()` builds the expected bean name as
  `allPlatforms.get(slug) + <base class name minus "Impl">`, so slug `bandzone` + `BandsScraperImpl`
  → it looks for a bean literally named `BandzoneBandsScraper`.
- `MainCacher.init()` goes the other way: it reads its own generic parameter's class name
  (`BandzoneBandsDTO`), strips the `BandDTO`/`BandsDTO` suffix and lowercases it to get the slug.

Consequence: **renaming a scraper, cacher or DTO class breaks platform wiring at runtime**, not at
compile time. The failure surfaces as `NoServiceException` (503) or `NoPlatformException` (404).

Two independent sources of truth for "what platforms exist":

| Source | Drives |
|---|---|
| `platforms` property in `application.properties` | factory wiring, which platforms get scraped |
| `platform_entity` table | the `/platforms` endpoint response |

The table has **no seed migration** — rows are inserted manually. A platform can therefore work
while being absent from `/platforms`, or be listed but not implemented. `scrapers/bandcamp_com/`
exists but bandcamp is not in the `platforms` property, so it is dormant, not live.

### Adding a platform

Add the slug to the `platforms` property, insert a row in `platform_entity`, then create classes
whose names follow the convention exactly:

```
scrapers/<site>/<Prefix>BandsScraper.java    extends BandsScraperImpl   @Service
scrapers/<site>/<Prefix>BandScraper.java     extends BandScraperImpl    @Service
cahcers/<site>/<Prefix>BandsCacher.java      extends BandsCacherImpl<<Prefix>BandsDTO>  @Service
cahcers/<site>/<Prefix>BandCacher.java       extends BandCacherImpl<<Prefix>BandDTO>    @Service
cahcers/<site>/<Prefix>BandsDTO.java         extends BandsDTO<<Prefix>BandDTO>
cahcers/<site>/<Prefix>BandDTO.java          extends BandDTO<<Prefix>TrackDTO>
```

Scrapers return **themselves** as the response (`fetch()` returns `this`), so a scraper is a
stateful, per-request-ish bean holding parsed jsoup `Element`s and exposing them through the
`Bands`/`Band` getters. Platform-specific extra fields (bandzone's `imageUrl`, `genre`, `city`) come
from per-platform sub-interfaces in `scrapers/<site>/interfaces/`.

Note the package is spelled `cahcers` (typo, load-bearing in imports — leave it).

## API surface

Four `GET` endpoints, all in `MainController`: `/v1/{s}/bands`, `/v1/bands`, `/v1/{s}/band`,
`/v1/platforms`.
`/bands` swallows per-platform failures and merges what succeeded, so it answers 200 even when a
platform is down; the single-platform endpoints propagate the error.

Errors map to status in `MainControllerAdvice` by **exception simple-name string switch**
(`PageNotFoundException` → 404, `PageScrapingException` → 501, `NoPlatformException` → 404,
`NoServiceException` → 503). Adding an exception subclass without adding a case there silently
yields 500.

Responses are typed as interfaces returning `List<?>`, and are frequently built as **anonymous
inner classes** (see `BandsServiceImpl.getBands`, `PlatformServiceImpl.getAll`).

## Versioning — decided, follow it

The version is the **first path segment**. `MainController` carries a class-level
`@RequestMapping("/v1")`; the method mappings stay version-free.

Verified routing fact: a literal first segment outranks the `{s}` platform variable in Spring's
matching, so `/v2/bands` is never mistaken for platform `v2`. Version-first is therefore safe even
though the slug also sits near the front. Do **not** put the version after the slug
(`/{s}/v2/bands`) — two endpoints have no slug, so it would force mixed placement and break
prefix-based grouping and ingress routing.

To publish a breaking change to *one* endpoint: add a new controller with
`@RequestMapping("/v2")` containing only that endpoint. Everything else keeps being served by
`MainController` under `/v1`. Endpoints are versioned individually; there is no requirement to move
the whole API at once.

Each version is its own `GroupedOpenApi` group in `OpenApiConfig`, giving a version dropdown in
Swagger UI and one spec per version. Adding v2 is one bean plus the controller:

```java
@Bean GroupedOpenApi v2Api() {
  return GroupedOpenApi.builder().group("v2").pathsToMatch("/v2/**").build();
}
```

Spec URLs (verified): `/v3/api-docs/v1` serves the v1 group, and `/v3/api-docs` **keeps serving the
full spec across all groups** — declaring groups does not take it away. Clients can use either;
prefer the group URL when you want one version in isolation.

## OpenAPI / Swagger

springdoc generates the spec from the controller at runtime: `/swagger-ui.html`, `/v3/api-docs`,
`/v3/api-docs.yaml`. `OpenApiConfig` holds title/description/server list.

When changing an endpoint, update its `@Operation`/`@Parameter`/`@ApiResponses` in the same edit —
the paths and types regenerate themselves, but the prose does not. Documented status codes must
keep matching `MainControllerAdvice`.

### Compatibility metadata

`@Compatibility` (in `annotations/`) declares an endpoint's backward-compatibility story;
`CompatibilityCustomizer` publishes it into the spec as the machine-readable `x-compatibility`
extension **and** appends a rendered block to the operation description. Both halves are needed:
Swagger UI does not render `x-` extensions anywhere a human will see them, and the description is
not parseable by a client. Setting `replacedBy` also flips the operation to deprecated.

Put `@Compatibility(since = "vN")` on every new handler method. It is the contract an API client
reads to decide whether a change affects it.

`CompatibilityCustomizer` implements **`GlobalOperationCustomizer`**, not plain
`OperationCustomizer`. With `GroupedOpenApi` groups declared, a plain `OperationCustomizer` bean is
only applied to a group if registered on that group's builder — it would silently emit nothing.
Keep the global interface when editing.

`server.forward-headers-strategy=framework` is set so Swagger UI's "Try it out" targets the public
https URL behind the reverse proxy instead of the internal container address. Don't remove it.

Because responses are `List<?>`, per-platform fields cannot be expressed in the schema; they are
described in prose in the `@Schema` annotations on `interfaces/Band.java` and `interfaces/Bands.java`.

## Conventions

Two-space indent in most classes, constructor injection with `@Autowired`, Lombok for DTO
accessors. The README deliberately contains **no endpoint/parameter documentation** — it points at
Swagger UI instead, so don't re-add tables that would duplicate the generated spec.

## Consumers

An Android app consumes this API. Treat the four endpoints' response shapes as a published
contract: removing or renaming a JSON field breaks shipped clients that cannot be forced to update.
