# System Fixes and Baseline Stabilization

## 1. Execution and Environment Fixes

### 1.1. Maven Wrapper Infrastructure
- **Issue:** The local Maven wrapper scripts (`mvnw` / `mvnw.cmd`) lacked the `.mvn/wrapper/` directory and `maven-wrapper.properties` file, preventing builds on clean machines or CI runners without a globally pre-installed Maven runtime.
- **Remediation:** Regenerated the wrapper configuration via `mvn wrapper:wrapper`, pinning Maven to version `3.9.9` with self-contained bootstrap resolution.
- **Impact:** Restores build portability and reproducibility across heterogeneous developer environments and automated CI/CD runners.

### 1.2. Database and Standalone Execution
- **Issue:** Packaging succeeded (`mvn clean package`), but launching the standalone JAR (`java -jar target/psoft-g1-0.0.1-SNAPSHOT.jar`) crashed with `HibernateException: Unable to determine Dialect`. The default configuration in `application.properties` depended on an external H2 TCP server process (`jdbc:h2:tcp://localhost/~/psoft-g1;IGNORECASE=TRUE`). Unit tests (`mvn test`) did not detect this failure because Spring Boot Test automatically replaced the datasource with an in-memory instance.
- **Remediation:** Updated `application.properties` to default to an autonomous in-memory database:
`spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;IGNORECASE=TRUE`
- **Impact:** Eliminates the external database dependency, enabling immediate standalone execution and container portability.

### 1.3. Bootstrap Data Seeding Synchronization
- **Issue:** Post-startup initialization failed with `IndexOutOfBoundsException: Index 0 out of bounds for length 0` in `Bootstrapper.java`. While `UserBootstrapper.java` generated reader numbers dynamically using the current calendar year (`LocalDate.now().getYear()` -> `2026/1`, `2026/2`), `Bootstrapper.java` queried hardcoded strings for `"2024/*"`. In 2026, no readers were found and the lending generation crashed.
- **Remediation:** Updated reader lookups in `Bootstrapper.java` to query dynamically using the current calendar year with fallback to `2024`, and added defensive bounds checks before initializing lending entities.
- **Impact:** Ensures application startup stability and seed data consistency regardless of the execution year.

---

## 2. Mutation Testing Integration

- **Baseline Limitation:** Although baseline documentation cited mutation coverage metrics, mutation testing was not integrated into the Maven build toolchain. Configuration for Pitest was absent from `pom.xml`.
- **Configuration:** Integrated `pitest-maven` (v1.15.8) and `pitest-junit5-plugin` (v1.2.1) under `<build><plugins>` in `pom.xml`, configuring the target package `pt.psoft.g1.psoftg1.*`, multithreading (4 threads), and output formats (HTML and XML).
- **Execution:** Executed via `mvn test-compile pitest:mutationCoverage`.