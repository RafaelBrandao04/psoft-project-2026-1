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

### 1.4. H2 Console Security and Frame Options Accessibility
- **Issue:** Accessing the H2 database web console (`http://localhost:8080/h2-console`) during local execution returned `401 Unauthorized`. In `SecurityConfig.java`, while Swagger and public API paths were permitted, the H2 console servlet path (`/h2-console/**`) lacked explicit permit rules, falling through to `.anyRequest().authenticated()`. Additionally, Spring Security's default `X-Frame-Options: DENY` header blocked rendering the console's UI iframes.
- **Remediation:** In `SecurityConfig.java`:
  1. Configured `.requestMatchers(AntPathRequestMatcher.antMatcher("/h2-console/**")).permitAll()` to allow development access without triggering Spring MVC path introspection conflicts.
  2. Configured `headers.frameOptions(frameOptions -> frameOptions.sameOrigin())` to enable iframe rendering for the H2 web console.
- **Impact:** Enables developer access to the in-memory database UI for data inspection and debugging while preserving strict security across all protected REST endpoints.

---

## 2. Mutation Testing Integration

- **Baseline Limitation:** Although baseline documentation cited mutation coverage metrics, mutation testing was not integrated into the Maven build toolchain. Configuration for Pitest was absent from `pom.xml`.
- **Configuration:** Integrated `pitest-maven` (v1.15.8) and `pitest-junit5-plugin` (v1.2.1) under `<build><plugins>` in `pom.xml`, configuring the target package `pt.psoft.g1.psoftg1.*`, multithreading (4 threads), and output formats (HTML and XML).
- **Execution:** Executed via `mvn test-compile pitest:mutationCoverage`.

---

## 3. Static Analysis Defect Fixes (SpotBugs SAST)

During the integration of automated static code analysis (`spotbugs-maven-plugin:4.8.3.1`) into the build pipeline, bytecode analysis identified 3 real High-Priority (Priority 1) defects in the baseline source code:

### 3.1. Character Encoding Vulnerability (`DM_DEFAULT_ENCODING`)
- **Location:** `src/main/java/pt/psoft/g1/psoftg1/shared/services/ForbiddenNameServiceImpl.java` (line 21).
- **Issue:** Instantiating `new InputStreamReader(resource.getInputStream())` relied implicitly on the host operating system's default character encoding. On Windows developer machines or runners operating under `Cp1252` or `Windows-1252`, non-ASCII Portuguese forbidden names loaded from text resources were corrupted, behaving inconsistently with Linux CI runners operating in `UTF-8`.
- **Remediation:** Explicitly declared `StandardCharsets.UTF_8`:
  ```java
  try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)))
  ```
- **Impact:** Eliminates environmental encoding divergence and guarantees consistent unicode handling across all operating systems.

### 3.2. Dead Local Store (`DLS_DEAD_LOCAL_STORE`)
- **Location:** `src/main/java/pt/psoft/g1/psoftg1/usermanagement/model/User.java` (line 166).
- **Issue:** In method `setPassword(String)`, the code allocated a `new Password(password)` strictly to validate the password's format constraints, but stored it in an unread local variable `Password passwordCheck = new Password(password);`.
- **Remediation:** Replaced the dead assignment with direct validation invocation:
  ```java
  public void setPassword(final String password) {
      new Password(password);
      final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
      this.password = passwordEncoder.encode(password);
  }
  ```
- **Impact:** Removes useless memory allocation and eliminates static analysis warnings without altering domain validation logic.

### 3.3. Serialization Integrity Flaw (`SE_BAD_FIELD`)
- **Location:** `src/main/java/pt/psoft/g1/psoftg1/shared/model/Name.java` and `pt/psoft/g1/psoftg1/lendingmanagement/api/FineView.java`.
- **Issue:** 
  1. The `User` entity implements Spring Security `UserDetails`, which extends `java.io.Serializable`. However, the embedded value object `Name` (`private Name name;`) did not implement `Serializable`. If user authentication tokens, HTTP sessions, or security contexts are serialized (e.g. across distributed session caches or cluster nodes), a runtime `java.io.NotSerializableException` was triggered.
  2. `FineView` was marked `implements Serializable` while its composite child `LendingView` was not, causing inconsistent DTO serialization.
- **Remediation:** 
  1. Added `implements java.io.Serializable` and explicit `private static final long serialVersionUID = 1L;` to `Name.java`.
  2. Removed redundant `implements Serializable` marker from `FineView.java` to align with the rest of the REST view DTOs.
- **Impact:** Guarantees Spring Security session serialization safety and runtime stability.
