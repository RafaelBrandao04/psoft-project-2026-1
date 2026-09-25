# System-as-is and Baseline (Current System Analysis)

## 1. Implementation and Deployment

### 1.1. Technology Stack and Configuration
- **Framework and Language:** Java 17, Spring Boot 3.2.5. The project has 144 production Java files, organized modularly and following a layered architecture: *REST controller -> application service -> repository -> JPA entity*.
- **Dependency Management:** Maven. The local wrapper is incomplete (missing `.mvn/wrapper/maven-wrapper.properties`), which prevents building on a clean machine without a globally installed Maven.
- **Database:** H2 (*in-memory* for tests and *TCP/file mode* for local execution), configured via `application.properties`. Base credentials are exposed in the repository.
- **Authentication:** Based on JWT tokens (*stateless*), with reader, librarian, and administrator roles (passwords hashed with BCrypt). Depends on cryptographic keys (`rsa.private.key` and `rsa.public.key`) which are vulnerably stored in the root of the *resources* directory.
- **API Documentation:** OpenAPI and Swagger UI are enabled.
- **Uploads and Storage:** Storage is local (`uploads-psoft-g1` directory). There is a system inconsistency: domain rules require a 20KB limit for photos, but the application configuration allows *multipart requests* up to 200MB. Purely local storage prevents the application's horizontal scalability.
- **Runtime Configurations (Security/Testing):** The `bootstrap` profile is active by default (injecting initial test data on startup). CORS policies are excessively permissive (allowing all origins, methods, and headers).

### 1.2. Current Deployment Process (Manual)
The current process for deploying a new version is not automated and requires the following manual steps:
1. Repository synchronization by the developer.
2. Building the executable artifact through the `mvn clean package` command.
3. Manual transfer of the final artifact (`.jar`) and respective keys/configurations to the target environment.
4. Manual execution via server (`java -jar`).

*Note: There is no Dockerfile, Docker Compose file, or infrastructure automation. The process is strictly local and not automatically reproducible.*

## 2. Existing Build and Testing Practices

### 2.1. Workflow
- **Planning and Development:** Feature/use-case oriented process. Planning documents (e.g., `RestMapping.md` and `WorkPlanning.md`) are used as delivery checklists.
- **Build:** The code is compiled only upon developer request, through local commands (`mvn clean compile` or IDE). The build was observed to be JDK version sensitive (fails on JDK 25 due to Lombok, but works correctly on JDK 17 and 21).
- **Automated Testing:** Executed manually before commits, using the IDE or the terminal (`mvn test`). The testing stack uses Spring Boot Test, JUnit, Spring Security Test, and Mockito (with `@WebMvcTest`).
- **Manual Validation:** There is a strong reliance on manual validation and exploration of endpoints through the execution of Postman collections.
- **Automated Validation (CI/CD):** Completely nonexistent. There are no automated blocks (e.g., *Github Actions* or *Jenkins*) for commits with failing tests or compilation issues.

### 2.2. Process Diagram (As-Is)

![System As-Is Workflow](system-as-is-workflow.jpg)

## 3. Test Quantity, Coverage, and Effectiveness

Current tests focus predominantly on domain business rules (*Value Objects*) and limited persistence/simulation operations (such as in lending services).

| Metric | Baseline Value | Observations |
| :--- | :--- | :--- |
| **Total Active Tests** | 102 | Distributed across 19 executed classes. The latest execution passed successfully (0 failures). |
| **Incomplete Tests** | Yes | Important classes (e.g., `TestAuthApi` and parts of lending services) are commented out, evidencing an experimental or unfinished test base. |
| **File Ratio** | 16.0% | 23 test files were observed compared to 144 production code files. |
| **Coverage** | Unavailable | Total absence of coverage reports (e.g., JaCoCo is not configured). |
| **Testing Blind Spots** | High | Lack of practical tests for most HTTP REST controllers, authorization/endpoint validation, photo upload flows, and various services (books, readers, reports). |
| **Overall Effectiveness**| Moderate/Low | Effectiveness is moderate for validating isolated domain rules, but too low to ensure correct end-to-end functionality. The absence of Mutation Testing makes it impossible to validate the resilience of the tests against false positives. |

## 4. Relevant Software Quality Indicators

The analysis reveals mixed indicators regarding software and process quality, highlighting the following:

| Indicator | Status | Assessment and Impact |
| :--- | :--- | :--- |
| **Organization and Domain**| Positive | Very good modular organization separated by business areas. Use of optimistic locking (`@Version` from JPA) and validations directly in the constructor of *Value Objects*. |
| **Continuous Integration** | Nonexistent | **High Risk:** Regressions are not detected early and faulty commits can easily break the main branch. |
| **Continuous Deployment** | Nonexistent | **Medium Risk:** Deliveries are manual, slow, repetitive, and prone to human error. |
| **Static Code Analysis** | Nonexistent | **Medium Risk:** Inability to automatically measure technical debt, as there is no integration with tools like Checkstyle, SpotBugs, or SonarQube. |
| **Environment Reproducibility**| Low | **High Risk:** High dependency on the developer's local configuration. Absence of containers (Docker) to guarantee OS and dependency consistency across Dev, Staging, and Production. |
| **Secrets Management** | Vulnerable | **High Risk:** Key cryptographic files (e.g., `rsa.private.key`) and standard credentials are exposed in version control instead of being injected via environment variables. |
| **Documentation vs Code** | Medium Risk | The REST mapping in the documents has potential inconsistencies with the code and duplicate endpoint declarations, increasing the risk of regressions if the codebase changes and documentation doesn't follow. |
