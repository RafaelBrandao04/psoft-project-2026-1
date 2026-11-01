# ODSOFT — Documentação Global de Organização do Desenvolvimento de Software e CI/CD

## 1. Enquadramento, Problemas e Objetivos do Projeto (P1)

### 1.1 Contexto
No âmbito do projeto partilhado do Mestrado em Engenharia Informática (ISEP), o serviço backend REST do **LMS (*Library Management System* — `psoft-g1`)** disponibiliza endpoints para a gestão de **Livros** (com enriquecimento bibliográfico externo via Google Books, Open Library e CrossRef), **Géneros**, **Autores**, **Leitores** (com notificações multicanal) e **Empréstimos** (com políticas de empréstimo configuráveis).

### 1.2 Problemas Identificados no Processo Base (*System-as-is*)
1. **Ausência de Automação e Repetibilidade:** Compilações, execuções de testes, empacotamento e implantações eram executados manualmente nas máquinas dos programadores, sem esteira de CI/CD.
2. **Avaliação Inexistente de Qualidade de Código e Testes:** Ausência de análise estática de código (SAST), relatórios de cobertura de linhas/ramos (JaCoCo) e testes de mutação (PITest) integrados no ciclo de vida Maven.
3. **Deteção Tardia de Defeitos e Regressões:** Com apenas 102 testes misturados na fase `test`, sem fase dedicada de integração (`verify` / Failsafe) e com testes de segurança comentados (`TestAuthApi`), um defeito fatal de dupla encriptação BCrypt em `UserService` impedia qualquer login no sistema sem ser detetado. Na baseline, **78% dos mutantes gerados sobreviviam** (score de mutação de 22%).
4. **Fragilidade de Configuração e Implantação Multi-Ambiente:** O sistema dependia de um servidor H2 TCP externo pré-iniciado (`jdbc:h2:tcp://localhost/~/psoft-g1`), tinha o Maven Wrapper incompleto (sem `.mvn/wrapper/maven-wrapper.properties`), falhava no arranque em 2026 devido a datas fixas (`"2024/*"`) no `Bootstrapper` e não possuía isolamento entre ambientes de Desenvolvimento (`DEV`), Aceitação (`UAT`) e Produção (`PROD`).

### 1.3 Objetivos Centrais de P1 (Enunciado Oficial — Secção 1.3)
* **Goal 1.3.1 — *Assess the System-as-is*:** Avaliar e documentar o processo e sistema inicial (implementação, implantação, práticas de build/teste, quantidade/cobertura/eficácia de testes e indicadores ISO/IEC 25010 e DORA).
* **Goal 1.3.2 — *Design the System-to-be*:** Desenhar o processo alvo de entrega contínua (*Continuous Delivery*) e fundamentar as decisões arquiteturais (ADRs).
* **Goal 1.3.3 — *Implement an Automated CI/CD Pipeline*:** Implementar uma esteira automatizada em Jenkins suportando compilação, testes unitários e mutação (`test`), empacotamento binário imutável (`package`), testes de integração e análise estática (`verify`), publicação de cobertura JaCoCo e promoção entre ambientes.
* **Goal 1.3.4 — *Improve the Automated Testing Strategy*:** Estruturar os níveis e âmbitos de teste (SUT), aplicar *Test Doubles* e comprovar empiricamente a capacidade de deteção de defeitos e regressões.
* **Goal 1.3.5 — *Configurable and Repeatable Multi-Environment Deployment*:** Suportar a implantação configurável, isolada e repetível nos ambientes de **DEV**, **UAT (Staging)** e **PROD**, com validação automática de prontidão (*Readiness Smoke Testing*).
* **Goal 1.3.6 — *Evaluate and Improve the CI/CD Process*:** Avaliar a evolução do processo através de evidências e métricas empíricas recolhidas, identificando gargalos e justificando as melhorias introduzidas.

---

## 2. Mapa de Navegação da Documentação e Artefactos de CI/CD

A documentação e a infraestrutura de automação do projeto estão organizadas modularmente na pasta `Docs/ODSOFT/`, na raiz do repositório e no diretório operacional `deploy/`:

```text
psoft-project-2026-1/
├── .Jenkinsfile                        <-- Pipeline Job 1: lms-dev (CI + Quality Gates + Coverage + Deploy DEV + Trigger UAT)
├── .Jenkinsfile_uat                    <-- Pipeline Job 2: lms-uat (CopyArtifact + Deploy UAT + Stash/Input + Recheck)
├── .Jenkinsfile_prod                   <-- Pipeline Job 3: lms-prod (CopyArtifact + Audit Trail + Deploy PROD)
├── .Jenkinsfile_common.groovy          <-- Wrapper de compatibilidade para o módulo de deploy
├── spotbugs-exclude.xml                <-- Regras de filtragem SAST (SpotBugs) para código gerado (Lombok/MapStruct)
│
├── deploy/                             <-- INFRAESTRUTURA DE IMPLANTAÇÃO E SMOKE TESTING (Goal 1.3.5)
│   ├── README.md                       <-- Manual de preparação das VMs Ubuntu (DEI Cloud), chaves SSH e Jenkins
│   ├── jenkins-deploy.groovy           <-- Módulo Groovy partilhado de deploy remoto (SCP/SSH multi-OS e systemd)
│   ├── setup-environments.sh           <-- Provisionamento idempotente de utilizadores, diretórios e unidades systemd
│   ├── psoft-g1@.service               <-- Unidade systemd parametrizada com hardening de segurança
│   ├── psoft-g1.sudoers                <-- Regra sudoers de menor privilégio para a conta de deploy
│   └── smoke-test.sh                   <-- Smoke test contratual (POSIX sh + Python JSON) para /info e Actuator
│
└── Docs/ODSOFT/
    ├── odsoft-documentation.md         <-- ÍNDICE MESTRE & MATRIZ DE RASTREABILIDADE (Este Documento)
    ├── Enunciado - ODSOFT.pdf          <-- Enunciado Oficial da Unidade Curricular (2026-2027)
    ├── system-as-is/system-as-is.md    <-- 1.3.1 Diagnóstico do processo manual, métricas baseline, ISO 25010 e DORA
    ├── system-fixes/system-fixes.md    <-- Estabilização da baseline (Maven Wrapper, H2, Bootstrapper e 3 bugs P1 SpotBugs)
    ├── system-to-be/system-to-be.md    <-- 1.3.2 Topologia de 3 Jobs, 2 contextos Jenkins, Multi-VM (vs806/vs336) e ADRs 01–04
    ├── pipeline/
    │   ├── ci-build-and-code-analysis.md           <-- 1.3.3 Compilação, Repackage, Cópia Binária, SpotBugs e .Jenkinsfile
    │   ├── ci-testing-coverage-and-quality-gates.md <-- 1.3.3 Ordem de fases Maven, Surefire, PITest, Failsafe, JaCoCo e Gates
    │   └── ci-deployment-and-health-monitoring.md  <-- 1.3.3/1.3.5 Single-Build Immutable Artifact, Smoke Testing e Audit Trail
    ├── deployment/
    │   └── containerization-and-multi-environment.md <-- 1.3.5 Externalização Twelve-Factor (.env), Ambientes vs Destinos e Systemd
    ├── testing-strategy/
    │   └── testing-strategy.md         <-- 1.3.4 Níveis SUT, Test Doubles, 433 Testes, Cobertura, Mutação e Regressões
    └── process-evaluation/
        └── process-evaluation-and-improvement.md <-- 1.3.6 Comparação As-Is/To-Be, Evolução de Métricas, Gargalos e Justificação
```

---

## 3. Síntese por Objetivo e Documentos de Detalhe

### 3.1 Diagnóstico do System-as-is e Estabilização da Baseline (Goal 1.3.1)
* **Documentos:** [`system-as-is.md`](./system-as-is/system-as-is.md) e [`system-fixes.md`](./system-fixes/system-fixes.md).
* **Resumo:** Regeneração do Maven Wrapper (`3.9.9`), transição para H2 autónomo em memória (`jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1`), sincronização dinâmica de anos no `Bootstrapper.java` e resolução dos **3 defeitos de Alta Prioridade (P1) do SpotBugs**:
  1. `DM_DEFAULT_ENCODING` em `ForbiddenNameServiceImpl.java` (`StandardCharsets.UTF_8` explícito);
  2. `DLS_DEAD_LOCAL_STORE` em `User.java` (remoção de variável local morta);
  3. `SE_BAD_FIELD` em `Name.java` / `FineView.java` (`Serializable` com `serialVersionUID = 1L`).

### 3.2 Arquitetura Alvo de Continuous Delivery e Topologia Multi-VM (Goal 1.3.2)
* **Documento:** [`system-to-be.md`](./system-to-be/system-to-be.md).
* **Decisões Arquiteturais (ADRs):**
  * **ADR-01 (2 Contextos Jenkins + Webhook Relay WSS + Multi-VM DEI):** Comparação empírica entre Jenkins no contentor LXD do DEI (`vs806:2226`) e Jenkins Local (`localhost:8080`) com túnel `WSS` Webhook Relay e deploy SSH/SCP para `vs806` (DEV `:8081` e UAT `:8082`) e `vs336` (PROD isolada `:8080`).
  * **ADR-02 (3 Pipelines em Cascata com *Single-Build Artifact* e *Stash*):** Separação em `lms-dev`, `lms-uat` e `lms-prod`. O job `lms-uat` liberta o agente (`agent none` + `stash`) durante a aprovação humana (`input` até 7 dias) e executa `deploy.recheck('uat')` após a aprovação.
  * **ADR-03 (Ordem de Fases Maven e Quality Gates):** Testes unitários (`Surefire`) e testes de mutação (`PITest` via perfil `-Pmutation`, threshold $\ge 30\%$) correm na fase `test` (antes do `package`), enquanto os testes de integração (`Failsafe`) e a análise estática (`SpotBugs check`) correm na fase `verify` (após o `package`), com publicação separada de cobertura JaCoCo (`unit-coverage` e `integration-coverage`).
  * **ADR-04 (Systemd Parametrizado e Smoke Test Contratual):** Gestão via `psoft-g1@<env>.service` e verificação pós-deploy via [`deploy/smoke-test.sh`](../../deploy/smoke-test.sh) validando `GET /info` e `GET /actuator/health/readiness`.

### 3.3 Implementação do Pipeline, Testes, Cobertura e Mutação (Goals 1.3.3 & 1.3.4)
* **Documentos:** [`ci-build-and-code-analysis.md`](./pipeline/ci-build-and-code-analysis.md), [`ci-testing-coverage-and-quality-gates.md`](./pipeline/ci-testing-coverage-and-quality-gates.md), [`ci-deployment-and-health-monitoring.md`](./pipeline/ci-deployment-and-health-monitoring.md) e [`testing-strategy.md`](./testing-strategy/testing-strategy.md).
* **Resultados Verificados (Build #38):**
  * **433 Testes Automatizados (100% pass):** **386 testes unitários Surefire** (55 classes, ~14s) + **47 testes de integração Failsafe** (12 classes, ~45s).
  * **Cobertura JaCoCo (Jenkins Coverage Plugin):** Unitários (`unit-coverage`) com **49.05% linhas (`2.295/4.679`)** e **24.64% ramos (`965/3.917`)**; Integração (`integration-coverage`) com **33.15% linhas (`1.551/4.679`)** e **9.62% ramos (`377/3.917`)**; módulos *core* e código novo/refatorado entre **75% e 100%**.
  * **Mutação PITest (fase `test`):** **1.078 mutações mortas em 3.798 geradas (28%)**, **61% de cobertura de linhas nas classes mutadas (`2.168/3.552`)** e **82% de *Test Strength* global** (78%–100% nos pacotes *core*).
  * **Deteção Empírica de Defeitos:** Correção do bug fatal de duplo hashing BCrypt em `UserService` (`AuthApiIntegrationTest`), prova de captura de regressão de fronteira (`count >= 3` $\rightarrow$ `count > 3`), contenção de concorrência com `@Version` (`LendingServiceImplIT`) e isolamento de falhas em notificações (`LendingNotificationIntegrationIT`).

### 3.4 Implantação Multi-Ambiente e Avaliação Crítica do Processo (Goals 1.3.5 & 1.3.6)
* **Documentos:** [`containerization-and-multi-environment.md`](./deployment/containerization-and-multi-environment.md), [`deploy/README.md`](../../deploy/README.md) e [`process-evaluation-and-improvement.md`](./process-evaluation/process-evaluation-and-improvement.md).
* **Destaques:** Distinção formal entre **3 Ambientes Lógicos** (`dev`, `uat`, `prod` + `local`) e **2 Destinos Remotos de Deployment** (`vs806:10806` e `vs336:10336`), análise da evolução temporal das métricas (`C3.3`), diagnóstico dos 5 gargalos operacionais e fundamentação da separação entre *Deployment* técnico e *Release* de negócio.

---

## 4. Matriz Comparativa Global de Evolução (*System-as-is* vs. *System-to-be* — Build #38)

| Dimensão / Indicador | Estado Inicial (*System-as-is*) | Estado Alvo Implementado (*System-to-be* — Build #38) | Melhoria / Impacto |
| :--- | :--- | :--- | :--- |
| **Orquestração de CI/CD** | Inexistente (compilação e testes manuais) | **3 Pipelines Declarativos em Cascata** (`lms-dev`, `lms-uat`, `lms-prod`) | Automação ponta-a-ponta desde o `git push` até Produção |
| **Gatilho de Integração (Trigger)** | Nenhum | **Webhook Relay (túnel `WSS`)** para Jenkins Local (`localhost:8080`) | Disparo automático em < 5s sem expor portas públicas |
| **Gestão de Binários** | Recompilado manualmente em cada máquina | **Single-Build Immutable Artifact (`app.jar`)** com *fingerprinting* SHA-256 | Garantia de que o binário testado é exatamente o promovido a PROD |
| **Rastreabilidade e Auditoria** | Nenhuma | `release.properties` $\rightarrow$ `acceptance.properties` $\rightarrow$ `production.properties` + `/info` | Trilha completa de Commit, Build, Aprovador e Timestamp |
| **Análise Estática (SAST)** | Nenhuma (125 avisos, 3 bugs P1 ativos) | **SpotBugs (`4.8.3.1`)** vinculado à fase `verify` com `spotbugs-exclude.xml` | **0 defeitos P1** (correção de encoding UTF-8, serialização e dead store) |
| **Total de Testes Automatizados** | 102 testes (misturados em `mvn test`) | **433 testes** (**386 unitários Surefire** + **47 integração Failsafe**) | **+324.5% testes** com separação estrita (`test` vs `verify`) |
| **Cobertura de Código (JaCoCo)** | Não configurada (~33% estimada pelo PIT) | **JaCoCo (`0.8.11`)** com 2 dashboards Jenkins (`49.05%` Unit / `33.15%` IT; **75%–100% core**) | Treemaps separados para testes unitários e de integração |
| **Testes de Mutação (PITest)** | 124 / 575 mortas (22%), 70% *strength* | **1.078 / 3.798 mortas na fase `test`** (`61%` line cov), **82% *Test Strength*** | **+769.4% mutantes eliminados**; 100% dos pacotes core $\ge 50\%$ |
| **Gestão da Aprovação Humana** | Nenhuma | Etapa `input` em UAT com **`stash`/`unstash` e `agent none`** + `deploy.recheck('uat')` | Zero consumo de executores Jenkins durante espera humana (até 7 dias) |
| **Topologia e Hardening de Deploy** | Execução manual local sem isolamento | **Multi-VM DEI Cloud** (`vs806` DEV/UAT + `vs336` PROD) com **systemd `psoft-g1@.service`** | Isolamento físico de Produção, utilizadores sem login e `ProtectSystem=strict` |
| **Validação Pós-Deploy (Smoke Test)** | Verificação manual ad-hoc via Postman | **Smoke Test Contratual (`smoke-test.sh`)** em `/info` e `/actuator/health/readiness` | Validação automática de aplicação, ambiente, commit SHA e estado `UP` |

---

## 5. Matriz Global de Rastreabilidade e Consistência (Critérios C4.1 & C4.2)

Para assegurar a consistência verificável na mesma versão da solução (`0.0.1-SNAPSHOT` / branch `main`, Build #38) e a rastreabilidade ponta-a-ponta desde os objetivos até às conclusões empíricas, a tabela seguinte relaciona cada subcritério de avaliação com as decisões arquiteturais, ficheiros de configuração/código, commits/issues, execuções de pipeline e evidências recolhidas:

| Critério / Objetivo | Decisão de Engenharia (ADR) | Ficheiros de Pipeline / Configuração / Código | Commits & Issues de Referência | Relatórios & Evidências Verificáveis (Build #38) | Impacto & Limitações Reconhecidas |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **C1.1 — Jenkins em 2 Contextos de Execução** (Goal 1.3.2 / 1.3.3) | **ADR-01:** Comparação entre Jenkins no contentor LXD do DEI (`vs806:2226`) e Jenkins Local (`localhost:8080`) com túnel `WSS` Webhook Relay. | [`.Jenkinsfile`](../../.Jenkinsfile) (`isUnix()` vs `bat`), [`system-to-be.md` (Secção 1.4)](./system-to-be/system-to-be.md) | `afb5304`, `da37b97`, `ffbfa87`, `b8cf1a9` (`#17`, `#18`) | Execução reproduzível em Linux (`sh`) e Windows (`bat`); Build #38 concluída em `05m 01s`. | O contentor LXD do DEI sofre de *CPU/RAM throttling* sob 3.798 mutações PITest; o controlador local exige que a workstation esteja ativa. |
| **C1.2 — Integração SCM & Disparo Automático** (Goal 1.3.3) | **ADR-01:** Pipeline-as-Code no Git + subscrição WebSocket (`WSS`) via plugin Webhook Relay (`jenkins-plugin`). | [`.Jenkinsfile`](../../.Jenkinsfile), [`pom.xml`](../../pom.xml) (`build-info`, `-Drelease.commit`), [`InfoController.java`](../../src/main/java/pt/psoft/g1/psoftg1/configuration/InfoController.java) | `14403b1`, `ffbfa87` (`#17`) | Disparo automático em `< 5s` após `git push main`; hash Git injetado em `release.properties` e `/info`. | O disparo de deploy está restrito à branch `main` (`when { expression { ... } }`) para proteger os ambientes partilhados. |
| **C1.3 — Build, SAST, Packaging & Testes no Pipeline** (Goal 1.3.3) | **ADR-03:** Unitários (`Surefire`) + Mutação (`PITest`) na fase `test` $\rightarrow$ `package` $\rightarrow$ Integração (`Failsafe`) + `SpotBugs check` na fase `verify` + `recordCoverage`. | [`pom.xml`](../../pom.xml), [`spotbugs-exclude.xml`](../../spotbugs-exclude.xml), [`.Jenkinsfile`](../../.Jenkinsfile) | `14403b1`, `00add7d`, `5c547b5`, `bfc7bfa`, `a37b9e8`, `a61f881`, `b8cf1a9` (`#17`, `#18`, `#26`) | **386/386 Surefire**, **PITest 1.078/3.798 (82% strength)**, **47/47 Failsafe**, **0 erros P1 SpotBugs**, **JaCoCo 49.05% Unit / 33.15% IT**. | O PITest corre exclusivamente sobre os testes unitários na fase `test` para evitar multiplicar o arranque do contexto Spring/H2 por 3.798 mutantes. |
| **C1.4 & C1.5 — Destinos Distintos & Ambientes DEV, UAT, PROD** (Goal 1.3.5) | **ADR-02 & ADR-04:** 3 ambientes lógicos (`dev`, `uat`, `prod`) distribuídos por 2 destinos físicos (`vs806:10806` e `vs336:10336`) com `systemd` parametrizado e `stash`/`agent none`. | [`.Jenkinsfile_uat`](../../.Jenkinsfile_uat), [`.Jenkinsfile_prod`](../../.Jenkinsfile_prod), [`deploy/jenkins-deploy.groovy`](../../deploy/jenkins-deploy.groovy), [`deploy/psoft-g1@.service`](../../deploy/psoft-g1@.service), [`deploy/setup-environments.sh`](../../deploy/setup-environments.sh), [`deploy/smoke-test.sh`](../../deploy/smoke-test.sh) | `da37b97`, `857326c`, `ffbfa87`, `7444584` (`#17`, `#20`, `#21`) | `release.properties` $\rightarrow$ `acceptance.properties` $\rightarrow$ `production.properties`; validação HTTP 200 em `/info` e `/actuator/health/readiness`. | Persistência atual baseada em H2 in-memory por ambiente; transição para PostgreSQL externo + Flyway mapeada no roadmap. |
| **C2.1 a C2.5 — Estratégia e Suite Multi-Nível de Testes (433 Testes)** (Goal 1.3.4) | Segregação Surefire (`*Test.java`) vs Failsafe (`*IT.java` / `*IntegrationTest.java`), Continuum de Test Doubles e análise de mutantes equivalentes. | `src/test/java/**` (55 classes unitárias + 12 classes de integração), [`testing-strategy.md`](./testing-strategy/testing-strategy.md) | `6157559`, `6261bb7`, `bfc7bfa`, `cebc73b`, `1d5673b`, `a37b9e8`, `a61f881`, `6f81027`, `cd79010` (`#19`, `#25`, `#26`) | Subida de **102 para 433 testes (+324.5%)**; deteção do bug fatal de duplo BCrypt em `UserService`; **1.078/3.798 mutantes mortos (82% strength)**. | Testes de sistema (`BookExternalInfoIT`, `LendingNotificationIntegrationIT`) usam mocks nas fronteiras de rede externa para garantir determinismo offline. |
| **C3.1 a C3.3 — Diagnóstico As-Is, Desenho To-Be e Melhoria Mensurável** (Goals 1.3.1, 1.3.2, 1.3.6) | Eliminação dos 3 antipadrões de release de Humble & Farley; separação entre Deployment técnico e Release de negócio. | [`system-as-is.md`](./system-as-is/system-as-is.md), [`system-fixes.md`](./system-fixes/system-fixes.md), [`system-to-be.md`](./system-to-be/system-to-be.md), [`process-evaluation-and-improvement.md`](./process-evaluation/process-evaluation-and-improvement.md) | `267bedd`, `acc05d7`, `e7e69f7`, `f7957da`, `7929e37`, `6f81027`, `b8cf1a9` | Comparação quantitativa antes/depois (DORA, ISO/IEC 25010, tempos unitários ~14s, subida de mutantes mortos de 124 para 1.078 e cobertura analisada de 33% para 61%). | Medições recolhidas na mesma máquina de referência (Windows 11 / JDK 17–21); variações de rede institucional afetam o tempo de SCP (~10–25s). |
