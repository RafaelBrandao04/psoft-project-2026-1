# ODSOFT — Project 1: User Stories

**Course:** Software Development Organization (ODSOFT) — 2026/2027  
**Project:** P1 — CI/CD Pipeline, Quality Assurance, and DevOps Evolution  
**Reference Document:** ODSOFT 2026-2027 Project 1 for Students v1.0 (Section 1.3 Goals)  

---

## Traceability Matrix: Goals to User Stories

| Project 1 Goal | User Story ID | User Story Title |
| :--- | :--- | :--- |
| **Goal 1:** Assess development process & system-as-is | **US-OD01** | Assessment and Baseline Documentation of System-as-Is |
| **Goal 2:** Design target process & system-to-be | **US-OD02** | Design and Architecture of Target Development & Deployment Process |
| **Goal 3.1:** Building and packaging the software | **US-OD03** | Automated Build and Packaging Stage |
| **Goal 3.2:** Static code analysis | **US-OD04** | Static Code Analysis and Quality Gate Integration |
| **Goal 3.3:** Automated testing at appropriate levels | **US-OD05** | Multi-Level Automated Testing Execution (Unit & Integration) |
| **Goal 3.4:** Test coverage and mutation testing | **US-OD06** | Automated Test Coverage (JaCoCo) and Mutation Testing (PIT) |
| **Goal 3.5:** Reporting of build, quality, & test results | **US-OD07** | Pipeline Feedback and Quality Reporting |
| **Goal 3.6:** Creation & management of deployable artifacts | **US-OD08** | Containerization and Artifact Management (Docker) |
| **Goal 3.7 & Goal 5:** Deployment to different environments | **US-OD09** | Automated Deployment across Dev, Staging, and Production |
| **Goal 4:** Improve automated testing strategy | **US-OD10** | Testing Strategy Expansion for Defect & Regression Detection |
| **Goal 5:** Configurable & repeatable deployment | **US-OD11** | Environment Parity and Secure Configuration Management |
| **Goal 6:** Evaluate & improve CI/CD process | **US-OD12** | CI/CD Process Evaluation, Bottleneck Analysis, and Metric Comparison |

---

## Epic 1: Process Assessment and Architecture Design

### US-OD01: Assessment and Baseline Documentation of System-as-Is
* **As a:** DevOps Engineer / Software Architect  
* **I want to:** Assess and document the current development process, deployment model, build practices, testing metrics, and software quality indicators of the base system (`psoft-g1`)  
* **So that:** The team establishes an empirical baseline of existing limitations, vulnerabilities, and inefficiencies against which future improvements are measured.  

**Acceptance Criteria:**
1. Document the implementation stack (Java 17, Spring Boot, modular monolith), configuration, local filesystem storage, exposed secrets, and manual deployment steps.
2. Analyze the current developer workflow, lack of CI/CD, manual test triggers, and compile sensitivity (JDK 17/21 vs JDK 25).
3. Quantify baseline test metrics: total active tests (102), file ratio (16%), line coverage (33% via PIT baseline), mutation score (22% killed), and test blind spots.
4. Evaluate quality indicators based on ISO/IEC 25010 characteristics and establish baseline DORA metrics (Deployment Frequency, Lead Time, Failure Rate, MTTR).

---

### US-OD02: Design of Target Development and Deployment Process (System-to-Be)
* **As a:** DevOps Engineer  
* **I want to:** Design the target development and deployment process (System-to-Be) and document the architectural decisions supporting its design  
* **So that:** The team has a well-defined blueprint for implementing an automated, repeatable, and robust CI/CD pipeline.  

**Acceptance Criteria:**
1. Create a visual workflow diagram of the target CI/CD pipeline detailing all stages (Checkout, Compile, Test, Static Analysis, Mutation, Package, Artifact Creation, Deploy to Dev/Staging/Prod, Smoke Tests).
2. Document architectural rationale for tool selection (e.g., GitHub Actions, SonarQube, JaCoCo, Pitest, Docker).
3. Define quality gates, branching strategy (e.g., GitFlow or GitHub Flow), and promotion criteria between environments.

---

## Epic 2: Automated CI/CD Pipeline Implementation

### US-OD03: Automated Build and Packaging Stage
* **As a:** Developer  
* **I want:** The CI/CD pipeline to automatically check out code, compile, and package the software into an executable JAR artifact upon every push and pull request  
* **So that:** Compilation errors and broken dependencies are immediately detected without relying on local developer environments.  

**Acceptance Criteria:**
1. Pipeline triggers automatically on pushes and pull requests to target branches.
2. Executes clean build using a standard JDK 17 environment.
3. Packages the application (`mvn clean package -DskipTests`) into an executable `.jar` file.
4. Fails the build immediately if compilation or packaging fails.

---

### US-OD04: Static Code Analysis and Quality Gate Integration
* **As a:** Tech Lead / QA Engineer  
* **I want:** The pipeline to automatically execute static code analysis using SonarQube on every build  
* **So that:** Code smells, bugs, security vulnerabilities, and code duplication are identified early and prevented from reaching production.  

**Acceptance Criteria:**
1. Integrate SonarQube/SonarCloud scanner into the CI pipeline.
2. Analyze code against predefined quality profiles (duplications, security hotspots, maintainability rating).
3. Enforce a Quality Gate: if the code fails minimum reliability, security, or coverage thresholds, the pipeline execution fails and halts further stages.

---

### US-OD05: Multi-Level Automated Testing Execution (Unit & Integration)
* **As a:** Software Engineer  
* **I want:** The pipeline to separate and execute unit tests and integration tests at appropriate lifecycle phases  
* **So that:** Fast feedback is provided on unit-level logic, followed by thorough validation of subsystem interactions without conflating test types.  

**Acceptance Criteria:**
1. Execute unit tests using `maven-surefire-plugin` during the `test` phase.
2. Execute integration tests using `maven-failsafe-plugin` during the `integration-test` / `verify` phase.
3. Ensure failures in either test phase break the pipeline and publish detailed failure reports.

---

### US-OD06: Automated Test Coverage and Mutation Testing
* **As a:** QA Engineer  
* **I want:** The pipeline to measure line/branch coverage with JaCoCo and assess test suite effectiveness with mutation testing (Pitest)  
* **So that:** We objectively verify code coverage and evaluate the tests' ability to catch synthetic faults and prevent regressions.  

**Acceptance Criteria:**
1. Configure JaCoCo plugin in Maven to generate XML and HTML code coverage reports.
2. Configure PIT (Pitest) to execute mutation tests on target packages and calculate mutation kill percentage.
3. Track and export coverage and mutation metrics to SonarQube and pipeline build summaries.

---

### US-OD07: Pipeline Feedback and Quality Reporting
* **As a:** Development Team Member  
* **I want:** The CI/CD pipeline to generate consolidated reports and notifications of build, test, and quality results  
* **So that:** The team receives immediate, actionable feedback on the status and health of the codebase.  

**Acceptance Criteria:**
1. Publish test execution summaries (passed, failed, skipped) and code coverage percentages in the pipeline run summary.
2. Link SonarQube analysis dashboards directly to pull requests or commit statuses.
3. Provide descriptive logs and error messages whenever a stage fails.

---

### US-OD08: Containerization and Artifact Management
* **As a:** Release Engineer  
* **I want:** The pipeline to build a standardized, immutable Docker container image of the application and tag it appropriately  
* **So that:** Deployable artifacts can be stored, verified, and executed consistently across any host without environmental discrepancy.  

**Acceptance Criteria:**
1. Create an optimized multi-stage or slim `Dockerfile` based on an official OpenJDK 17 runtime image.
2. Build and tag the Docker image with commit SHA and/or semantic version.
3. Ensure configuration and keys can be provided dynamically at container runtime rather than baked into the image.

---

### US-OD09: Automated Deployment across Environments with Smoke Tests
* **As a:** Operations Engineer  
* **I want:** The pipeline to deploy the containerized application sequentially across Development, Staging, and Production environments with automated verification  
* **So that:** Deployments are repeatable, hands-free, and validated for operational readiness.  

**Acceptance Criteria:**
1. Support deployment to dedicated environments (Dev, Staging, Production) on designated ports/networks.
2. Execute automated post-deployment smoke tests (e.g., verifying `GET /swagger-ui/index.html` or health endpoints).
3. Ensure deployment halts immediately if smoke tests fail, preventing faulty releases in downstream environments.

---

## Epic 3: Testing Strategy Improvement

### US-OD10: Testing Strategy Expansion for Defect & Regression Detection
* **As a:** Developer / QA Engineer  
* **I want to:** Expand the automated test suite with opaque-box and transparent-box tests across domain, service, controller, and gateway layers  
* **So that:** Critical blind spots are eliminated, test effectiveness is enhanced, and the mutation score significantly increases from the 22% baseline.  

**Acceptance Criteria:**
1. Add unit and integration tests covering REST controllers, authentication filters, file uploads, and external adapters.
2. Implement boundary and domain tests targeting uncovered business rules.
3. Increase total unit tests substantially (target: > 400 tests).
4. Demonstrate a significant increase in overall line coverage (target: > 70%) and mutation score (target: > 50%).

---

## Epic 4: Configurable and Repeatable Environment Deployment

### US-OD11: Environment Parity and Secure Configuration Management
* **As a:** Security / DevOps Engineer  
* **I want to:** Externalize all environment configurations, database endpoints, and secrets into environment variables and secure configuration mechanisms  
* **So that:** Cryptographic keys and database credentials are removed from version control and environments can be provisioned repeatably without modifying code.  

**Acceptance Criteria:**
1. Remove plain-text secrets (`rsa.private.key`, DB passwords) from `src/main/resources`.
2. Configure Spring Boot to read secrets and operational properties via environment variables or secret vaults.
3. Provide Docker Compose manifests to provision required supporting services (e.g., database, cache) with network isolation.

---

## Epic 5: CI/CD Process Evaluation and Continuous Improvement

### US-OD12: CI/CD Process Evaluation, Bottleneck Analysis, and Metric Comparison
* **As a:** DevOps Lead / Student  
* **I want to:** Collect empirical evidence from the implemented CI/CD pipeline, compare initial vs. improved metrics, identify bottlenecks, and justify all process improvements  
* **So that:** The project quantitatively demonstrates the value, performance, and efficiency of the evolved delivery process.  

**Acceptance Criteria:**
1. Measure execution times for each pipeline stage and total pipeline duration.
2. Identify bottlenecks (e.g., mutation testing overhead, sequential stages) and document attempted optimizations (parallelization, thread tuning, caching).
3. Produce a quantitative comparative table between As-Is and To-Be processes across:
   - DORA metrics (Deployment Frequency, Lead Time, Change Failure Rate, MTTR).
   - Test metrics (Total tests: 102 vs. 500+; Line coverage: 33% vs. 70%+; Mutation score: 22% vs. 50%+).
4. Document the justification and lessons learned for all architectural and tooling decisions introduced throughout the project.