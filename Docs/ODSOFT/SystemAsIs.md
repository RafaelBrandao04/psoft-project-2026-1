# System-as-is and Development Process Analysis

## Scope and evidence

This document describes the repository as observed on 18 September 2026. The evidence comes from the source tree, Maven configuration, tests, runtime configuration and project documentation. Claims about coverage or effectiveness are qualified where the repository does not provide a measurement.

## 1. System implementation

The system is a Java 17 Spring Boot 3.2.5 application built as a single Maven JAR. The application entry point is `PsoftG1Application`, and the code is organized by business capability:

- author management;
- book management;
- reader and user management;
- lending and fines;
- genres and reporting;
- shared photo/file-storage and value-object services.

The dominant implementation flow is REST controller -> application service -> repository -> JPA entity. The domain model is persisted through Spring Data JPA/Hibernate. The repository exposes nine repository interfaces and the production tree contains 144 Java files.

The main business rules currently implemented include:

- lending duration and fine values loaded from `library.properties`;
- a maximum of three outstanding loans and rejection of overdue readers;
- optimistic concurrency using JPA `@Version` on relevant entities;
- validation of value objects such as ISBN, title, name, phone number and lending number;
- optional photos stored through a local upload directory;
- analytical queries for top authors, books, genres and readers, overdue loans and average lending duration.

The REST surface and use-case mapping are documented in `Docs/Phase1/RestMapping.md` and `Docs/Phase2/RestMapping.md`. OpenAPI and Swagger UI are enabled. Authentication is stateless and JWT-based, with roles for readers, librarians and administrators. Passwords are encoded with BCrypt.

## 2. Implementation and deployment

### Local execution

The intended local runtime is Spring Boot launched through Maven. The default application profile is `bootstrap`, which loads sample users, authors, genres, books, photos and lendings through `CommandLineRunner` components. The default datasource is H2 in TCP/file mode (`jdbc:h2:tcp://localhost/~/psoft-g1`), so local execution expects an H2 server/database setup. The H2 console is enabled for development.

Tests override this with an in-memory H2 database, avoiding a persistent external database for the test environment.

### Packaging and deployment

The `spring-boot-maven-plugin` is configured, so the application can be packaged as an executable Spring Boot JAR. The repository does not contain a Dockerfile, Docker Compose file, Kubernetes manifest, infrastructure-as-code, release script or CI workflow. Consequently, deployment is currently a manual/local concern rather than a reproducible automated pipeline.

The Maven wrapper scripts exist, but the wrapper metadata is incomplete: `.mvn/wrapper/maven-wrapper.properties` is absent. The wrapper therefore cannot bootstrap Maven on a clean machine. A globally installed Maven was required during this analysis.

Important runtime configuration concerns:

- RSA private/public JWT keys are committed under `src/main/resources`;
- an API key is present in `application.properties` and the test properties;
- default database credentials are committed in configuration;
- the application enables the `bootstrap` profile by default;
- CORS allows every origin, header and method;
- multipart request limits allow 200 MB even though the domain rule states a 20 KB photo limit.

These choices are acceptable as development conveniences only. They should not be carried into a production deployment.

## 3. Build and testing practices

The build is Maven-based and uses Spring Boot dependency management. Java source and target release are configured as 17. The main testing dependencies are Spring Boot Test, JUnit, Spring Security Test and Mockito through the Spring Boot test starter.

The repository contains no explicit build stages beyond Maven's standard lifecycle, no formatter/checkstyle/PMD/SpotBugs configuration, no mutation testing, no JaCoCo coverage plugin and no Sonar configuration. There is also no documented CI command in `README.md`; the README currently contains only the project title.

The existing tests use three levels:

1. Unit-style domain tests for value objects and entity rules.
2. Spring service/repository tests using `@SpringBootTest`, `@DataJpaTest`, real JPA and H2.
3. A small amount of MVC scaffolding using `@WebMvcTest` and Mockito.

Several test classes are named `IntegrationTest`, although some mock repositories and validate only a narrow service path. The authentication API test class is entirely commented out, and parts of the lending service test are also commented out. This is evidence of an incomplete or experimental test suite rather than a stable test convention.

## 4. Test quantity, coverage and effectiveness

Repository counts:

| Indicator | Observed value |
|---|---:|
| Production Java files | 144 |
| Test Java files | 23 |
| Active `@Test` methods | 102 |
| Test classes executed | 19 |
| Latest observed Maven result | 102 passed, 0 failures, 0 errors, 0 skipped |
| Test-to-production file ratio | 16.0% |
| Measured line/branch coverage | Not available |
| Automated CI test execution | Not present |

The 102 active tests are concentrated in domain/value-object validation and lending. There are repository integration tests for authors and lendings, service tests for authors and lendings, and a Spring context smoke test. The suite therefore gives useful evidence for selected invariants and lending workflows.

Coverage is not measurable from the repository because no coverage tool or report is configured. File and test counts must not be interpreted as percentage coverage. Based on the visible test distribution, the following areas are weakly evidenced or untested:

- most REST controllers and HTTP status/error contracts;
- authorization rules across the endpoint matrix;
- login, registration and JWT claims, because `TestAuthApi` is commented out;
- file upload, photo size/type validation and deletion behavior;
- most book, reader, genre and reporting services;
- negative paths and validation for many application services;
- deployment/startup using the documented default H2 TCP configuration;
- regression tests for all Phase 2 use cases listed in `Docs/Phase2/WorkPlanning.md`.

The test suite's effectiveness is therefore moderate for isolated domain rules and selected persistence/lending behavior, but low for end-to-end API confidence. The current suite can detect some invalid domain states and query regressions, but it cannot demonstrate feature parity for the complete REST contract.

During this analysis, `mvn test` completed successfully with JDK 21: 102 tests passed, with zero failures, errors or skips. A JDK 25 run failed in Lombok annotation processing with `com.sun.tools.javac.code.TypeTag :: UNKNOWN`, showing that the build is sensitive to the developer JDK despite the project target being Java 17. The Maven wrapper could not be used because its wrapper properties file is missing; the successful run used globally installed Maven 3.9.2.

## 5. Software quality indicators

### Positive indicators

- Clear modular organization around business capabilities.
- Domain constraints are represented in constructors/value objects and tested in several places.
- JPA optimistic locking is used for concurrent updates.
- Authentication, input validation, OpenAPI and persistence are integrated rather than left as placeholders.
- H2 in-memory configuration supports repeatable isolated repository tests.
- Work planning, domain glossary, aggregate descriptions and REST mappings provide useful traceability to use cases.

### Negative indicators and risks

- No objective coverage, static-analysis, quality-gate or CI metrics.
- No reproducible deployment definition.
- Incomplete Maven wrapper prevents clean-machine build bootstrap.
- Secrets and private signing keys are stored in resources/configuration.
- Default bootstrap data and permissive CORS are enabled in the main configuration.
- The documented REST mapping and security configuration contain duplicated or potentially inconsistent endpoint declarations, which increases regression risk.
- The project has only two commits in the visible history, so change traceability and review evidence are limited.
- Several tests are empty, commented out or named more broadly than their actual assertions.
- Runtime file storage is local, which is not durable or horizontally scalable without external storage.

## 6. Current process assessment

The development process is feature/use-case oriented. Phase documents track whether documentation, model, repository, service, controller, tests and Postman collections exist for each use case. This is a useful delivery checklist, but it records completion claims rather than executable quality evidence.

The current process can be characterized as:

1. Define domain/use-case artifacts and REST mappings.
2. Implement the feature through model, repository, service and controller layers.
3. Add selected unit or integration tests.
4. Exercise endpoints manually through Postman collections.
5. Run locally with Spring Boot and bootstrap data.

The missing process controls are automated build verification, coverage thresholds, static analysis, dependency/security scanning, review gates, reproducible deployment and a documented release procedure.

## 7. Recommended next actions

1. Repair and commit the Maven wrapper metadata, document the supported JDK (preferably Java 17 or a tested compatible JDK), and add a single reproducible `mvn clean verify` command to the README.
2. Add CI to compile, run tests and publish test reports on every push/pull request.
3. Add JaCoCo with separate line and branch thresholds, then establish a baseline rather than choosing an arbitrary high target.
4. Reactivate or replace authentication tests and add controller tests for authorization, validation, status codes and error responses.
5. Add service and repository tests for the uncovered book, reader, genre/reporting, photo and Phase 2 workflows.
6. Move JWT keys, API keys and database credentials to environment/secret management; disable bootstrap, H2 console and wildcard CORS outside development.
7. Add a Dockerfile or equivalent deployment definition and a smoke test that starts the packaged application against an explicitly configured database.
8. Add static analysis and dependency vulnerability checks to the same CI pipeline.

## Conclusion

The project has a substantial working application core and meaningful domain-level tests, but its quality is currently demonstrated mainly by source structure and selected local tests. It is not yet supported by measurable coverage, automated integration gates or reproducible deployment. The highest-value improvement is to make the build/test process deterministic first, then expand API/security coverage and add objective quality measurements.