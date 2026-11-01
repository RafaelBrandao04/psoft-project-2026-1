# System-as-is and Baseline (Current System Analysis)

## 1. Implementation and Deployment

### 1.1. Technology Stack and Configuration
- **Framework and Language:** Java 17, Spring Boot 3.2.5. The project contains 144 production Java files, organized modularly by business domain and following an internal layered architecture: *REST controller -> application service -> repository -> JPA entity*.
- **Dependency Management:** Maven (`pom.xml`). The local wrapper is incomplete (missing `.mvn/wrapper/maven-wrapper.properties`), preventing builds on a clean machine without a globally pre-installed Maven runtime.
- **Database and Persistence:** H2 Database. Configured in `application.properties` via TCP server mode (`jdbc:h2:tcp://localhost/~/psoft-g1;IGNORECASE=TRUE`). This requires an external H2 TCP server process listening on port `9092` and files pre-created on the user's home directory, causing immediate execution failures on new/clean environments unless manually reconfigured to in-memory (`jdbc:h2:mem:testdb`). Database credentials (`mysqluser`/`mysqlpass`) are hardcoded in the repository.
- **Authentication and Secrets:** Stateless JWT authentication with role-based access control (roles: `READER`, `LIBRARIAN`, `ADMIN`; passwords hashed with BCrypt). The system relies on RSA asymmetric key pairs (`rsa.private.key` and `rsa.public.key`) which are insecurely committed in plain text under `src/main/resources`.
- **API Documentation:** OpenAPI 3 and Swagger UI are enabled (`/swagger-ui` and `/api-docs`).
- **Uploads and Storage:** Storage is local to the filesystem (`uploads-psoft-g1` directory). An architectural inconsistency exists: business/domain rules mandate a 20KB limit for photos, while Spring's `MultipartProperties` configure a 200MB threshold. Storing uploads directly on the local filesystem prevents horizontal scaling and container portability.
- **Runtime Configurations and CORS:** The `bootstrap` profile is permanently active by default (`spring.profiles.active=bootstrap`), automatically seeding test data on startup. CORS policies are globally permissive (`Customizer.withDefaults()`), allowing any origin, header, and method.

### 1.2. Current Deployment Process (Manual)
The existing deployment process is entirely manual, fragile, and non-reproducible:
1. **Manual Synchronization:** Developers pull the repository locally.
2. **Local Packaging:** Developers manually execute `mvn clean package` on their machines.
3. **Artifact Transfer:** The resulting `.jar` file and configuration files are manually copied to the target server/machine via SSH/FTP or manual file sharing.
4. **Manual Execution:** The application is manually launched in the foreground/background via `java -jar psoft-g1-0.0.1-SNAPSHOT.jar`.

*Note: There are no Dockerfiles, Docker Compose manifests, or infrastructure-as-code automation. Environments (Dev, Staging, Production) lack parity and isolation.*

---

## 2. Existing Build and Testing Practices

### 2.1. Workflow
- **Planning and Development:** Feature-driven and checklist-based using documentation files (`RestMapping.md` and `WorkPlanning.md`).
- **Build Lifecycle:** Code compilation occurs strictly on-demand on developers' local machines (`mvn clean compile` or IDE builds). The build configuration is sensitive to the installed JDK: it fails on JDK 25 due to Lombok annotation processing incompatibilities, compiling successfully only on JDK 17 and 21.
- **Test Lifecycle Separation:** The Maven build only configures the standard `maven-surefire-plugin` (fase `test`). The `maven-failsafe-plugin` is absent, meaning there is no isolated integration testing phase (`mvn verify`).
- **Branching and Review Gates:** Commits and pushes are performed directly without branch protection rules, pull request gates, or automated quality checks.
- **Automated Testing:** Executed manually before commits using the IDE runner or `mvn test`. The stack comprises Spring Boot Test, JUnit 5, Spring Security Test, and Mockito (`@WebMvcTest`).
- **Manual Validation:** Developers heavily rely on manual exploration of REST endpoints using Postman collections (`Psoft-G1.postman_collection.json`).
- **CI/CD Pipeline:** Completely nonexistent. No continuous integration server or pipeline (e.g., GitHub Actions, Jenkins) is configured in `.github/workflows` to prevent broken builds or regressions.

### 2.2. Process Diagram (As-Is)

![System As-Is Workflow](system-as-is-workflow.jpg)

---

## 3. Test Quantity, Coverage, and Effectiveness

The test suite is heavily concentrated on domain value object validations, with sparse coverage of application services and near-complete absence of controller, security, and integration testing.

| Metric | Baseline Value (As-Is) | Observations and Empirical Evidence |
| :--- | :--- | :--- |
| **Total Active Tests** | 102 | Distributed across 19 test classes. Manual execution achieves a 100% pass rate (0 failures). |
| **Incomplete Tests** | Yes | Critical security classes (e.g., `TestAuthApi`) and segments of lending services are commented out in source code. |
| **File Ratio** | 16.0% | 23 test classes compared to 144 production Java files. |
| **Line Coverage** | 33% (PIT Baseline) | Global coverage reporting (JaCoCo) is not integrated into `pom.xml`. An empirical PIT baseline run on the base project reveals only 345 of 1038 lines covered (33%) across tested classes. |
| **Mutation Score (Effectiveness)** | 22% (124/575 killed) | In the baseline mutation test, only 124 out of 575 generated mutations were killed. 399 mutations had zero test coverage, and test strength was 70%. |
| **Testing Blind Spots** | High | No automated tests exist for REST controllers, authorization/role checks, file upload endpoints, or reporting queries. |
| **Overall Effectiveness** | Low | Despite 102 passing unit tests, **78% of injected mutations survive**. Tests predominantly assert trivial getters or basic constraints, providing false confidence and poor fault-detection capability. |

---

## 4. Relevant Software Quality Indicators

### 4.1. Assessment by ISO/IEC 25010 Quality Characteristics

| Quality Characteristic | Current Status | Risk | Assessment and Impact |
| :--- | :--- | :--- | :--- |
| **Functional Suitability** | Partial | Medium | Core business rules for library management are implemented, but lacking automated validation across end-to-end user journeys. |
| **Reliability & Fault-Tolerance** | Low | **High** | With a 22% mutation score and lack of automated regression testing, logic bugs and breaking changes can easily reach target environments. |
| **Security** | Vulnerable | **High** | RSA private keys (`rsa.private.key`) and database credentials are committed in version control. Permissive CORS policies allow unrestricted cross-origin requests. |
| **Maintainability** | Moderate | Medium | Well-organized domain packages (*Package-by-Feature* / *Screaming Architecture*), but technical debt cannot be monitored due to the complete absence of static code analysis (SonarQube, SpotBugs, Checkstyle). |
| **Portability & Reproducibility** | Fragile | **High** | Lack of containerization (Docker) creates severe "works on my machine" issues. Strong dependency on local host paths (`uploads-psoft-g1/`, `~/psoft-g1`) and pre-existing H2 TCP processes. |

### 4.2. DevOps and DORA Process Baseline

| DORA Metric | Current Baseline (As-Is) | Root Cause |
| :--- | :--- | :--- |
| **Deployment Frequency** | Low / Sporadic | Manual build and deployment steps create significant friction. |
| **Lead Time for Changes** | High | Changes require manual compilation, manual Postman smoke tests, and manual artifact copy. |
| **Change Failure Rate** | High / Unknown | Absence of CI gates means broken code can be pushed to `main` without immediate detection. |
| **Mean Time to Recovery (MTTR)** | High | Lack of automated rollbacks, immutable container images, or structured deployment logging. |
