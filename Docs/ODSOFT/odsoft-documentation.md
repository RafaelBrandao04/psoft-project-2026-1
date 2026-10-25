# ODSOFT — Documentação Global de Organização do Desenvolvimento de Software e CI/CD

## 1. Enquadramento, Problemas e Objetivos do Projeto (P1)

### 1.1 Contexto
No âmbito do projeto partilhado do Mestrado em Engenharia Informática (ISEP), o serviço backend REST do **LMS (*Library Management System* — `psoft-g1`)** disponibiliza endpoints para a gestão de:
1. **Livros (*Books*)** e enriquecimento bibliográfico externo
2. **Géneros (*Genres*)**
3. **Autores (*Authors*)**
4. **Leitores (*Readers*)** e notificações multicanal
5. **Empréstimos (*Lendings*)** e políticas de empréstimo

### 1.2 Problemas Identificados no Processo Base (*System-as-is*)
Embora a aplicação base fornecesse a funcionalidade inicial, o processo de desenvolvimento, verificação e entrega apresentava limitações críticas de engenharia de software:
1. **Ausência de Automação e Repetibilidade:** Compilações, execuções de testes, empacotamento e implantações eram executados manualmente nas máquinas dos programadores, sem qualquer esteira de CI/CD configurada.
2. **Avaliação Inexistente de Qualidade de Código e Testes:** Ausência de análise estática de código (SAST), relatórios de cobertura de linhas/ramos (JaCoCo) e testes de mutação (PITest) integrados no ciclo de vida Maven.
3. **Deteção Tardia de Defeitos e Regressões:** Com apenas 102 testes misturados na fase `test`, sem fase dedicada de integração (`verify` / Failsafe) e com testes de segurança comentados (`TestAuthApi`), um defeito fatal de dupla encriptação BCrypt em `UserService` impedia qualquer login no sistema sem ser detetado. Na baseline, **78% dos mutantes gerados sobreviviam** (score de mutação de 22%).
4. **Fragilidade de Configuração e Implantação Multi-Ambiente:** O sistema dependia de um servidor H2 TCP externo pré-iniciado (`jdbc:h2:tcp://localhost/~/psoft-g1`), tinha o Maven Wrapper incompleto (sem `.mvn/wrapper/maven-wrapper.properties`), falhava no arranque em 2026 devido a datas fixas (`"2024/*"`) no `Bootstrapper` e não possuía isolamento entre ambientes de Desenvolvimento (`DEV`), Aceitação (`UAT`) e Produção (`PROD`).

### 1.3 Objetivos Centrais de P1 (Enunciado Oficial — Secção 1.3)
O Projeto 1 de ODSOFT estrutura-se em torno de seis objetivos fundamentais:
* **Goal 1.3.1 — *Assess the System-as-is*:** Avaliar e documentar o processo e sistema inicial (implementação, implantação, práticas de build/teste, quantidade/cobertura/eficácia de testes e indicadores de qualidade ISO/IEC 25010 e DORA).
* **Goal 1.3.2 — *Design the System-to-be*:** Desenhar o processo alvo de desenvolvimento e entrega contínua (*Continuous Delivery*) e fundamentar as decisões arquiteturais (ADRs).
* **Goal 1.3.3 — *Implement an Automated CI/CD Pipeline*:** Implementar uma esteira automatizada em Jenkins suportando compilação, empacotamento binário imutável, análise estática (SpotBugs), testes em múltiplos níveis (Surefire e Failsafe), cobertura (JaCoCo), mutação (PITest), publicação de relatórios e promoção entre ambientes.
* **Goal 1.3.4 — *Improve the Automated Testing Strategy*:** Estruturar os níveis e âmbitos de teste (SUT), aplicar sistematicamente *Test Doubles* (taxonomia de Meszaros/Fowler) e comprovar empiricamente a capacidade de deteção de defeitos e regressões.
* **Goal 1.3.5 — *Configurable and Repeatable Multi-Environment Deployment*:** Suportar a implantação configurável, isolada e repetível nos ambientes de **DEV**, **UAT (Staging)** e **PROD**, com validação automática de prontidão (*Readiness Smoke Testing*).
* **Goal 1.3.6 — *Evaluate and Improve the CI/CD Process*:** Avaliar a evolução do processo através de evidências e métricas empíricas recolhidas, identificando gargalos e justificando as melhorias introduzidas.

---

## 2. Mapa de Navegação da Documentação e Artefactos de CI/CD

A documentação e a infraestrutura de automação do projeto estão organizadas modularmente na pasta `Docs/ODSOFT/`, na raiz do repositório e no diretório operacional `deploy/`:

```text
psoft-project-2026-1/
├── .Jenkinsfile                        <-- Pipeline Job 1: lms-dev (CI + Quality Gates + Deploy DEV + Trigger UAT)
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
    ├── odsoft-documentation.md         <-- ÍNDICE MESTRE / RELATÓRIO GLOBAL DE ODSOFT (Este Documento)
    ├── Enunciado - ODSOFT.pdf          <-- Enunciado Oficial da Unidade Curricular (2026-2027)
    │
    ├── System-as-is/                   <-- 1.3.1 DIAGNÓSTICO DO SISTEMA E PROCESSO INICIAL
    │   ├── system-as-is.md             <-- Análise do processo manual, métricas baseline, ISO 25010 e DORA
    │   └── system-as-is-workflow.jpg   <-- Diagrama do fluxo de trabalho manual inicial
    │
    ├── System-fixes/                   <-- ESTABILIZAÇÃO DA BASELINE E CORREÇÃO DE DEFEITOS SAST
    │   └── system-fixes.md             <-- Correções ao Maven Wrapper, H2, Bootstrapper, H2 Console e 3 bugs P1 SpotBugs
    │
    ├── System-to-be/                   <-- 1.3.2 ARQUITETURA ALVO DE CONTINUOUS DELIVERY E ADRs
    │   └── system-to-be.md             <-- Topologia de 3 Jobs, Multi-VM (vs806/vs336), Proveniência e ADRs 01 a 04
    │
    ├── Pipeline/                       <-- 1.3.3 IMPLEMENTAÇÃO DA ESTEIRA DE CI/CD E QUALITY GATES
    │   ├── ci-build-and-code-analysis.md           <-- Compilação, Repackage, Cópia Binária, SpotBugs e .Jenkinsfile
    │   └── ci-testing-coverage-and-quality-gates.md <-- Surefire, Failsafe, JaCoCo, Perfil PITest e Hard/Soft Gates
    │
    └── testing-strategy/               <-- 1.3.4 ESTRATÉGIA DE TESTES, COBERTURA E DETEÇÃO DE REGRESSÕES
        └── testing-strategy.md         <-- Níveis SUT, Test Doubles, 374 Testes, Injeção de Falhas e Mutantes Equivalentes
```

---

## 3. Síntese do System-as-is e Estabilização da Baseline (Goal 1.3.1)

Os detalhes exaustivos encontram-se documentados em:
* **[Análise Completa do System-as-is](./System-as-is/system-as-is.md)**
* **[Correções de Estabilização e SAST (System-fixes)](./System-fixes/system-fixes.md)**

### 3.1 Principais Limitações do Estado Inicial (*As-Is*)
* **Infraestrutura de Build Quebrada em Máquinas Limpas:** Os scripts `mvnw` e `mvnw.cmd` não possuíam a pasta `.mvn/wrapper/`, falhando em agentes de CI sem Maven pré-instalado.
* **Dependência de Servidor H2 Externo:** A configuração `jdbc:h2:tcp://localhost/~/psoft-g1` provocava falha imediata (`HibernateException: Unable to determine Dialect`) ao executar o JAR standalone.
* **Falha Temporal no Arranque (`IndexOutOfBoundsException`):** O `UserBootstrapper` gerava leitores com o ano corrente (`2026/*`), mas o `Bootstrapper` procurava leitores hardcoded de `"2024/*"`.
* **Fragilidade da Suite de Testes:** Apenas **102 testes ativos** (misturando âmbitos unitários e de integração), **33% de cobertura de linhas** nas classes testadas e **22% de score de mutação** (124/575 mutantes mortos; 78% de mutantes sobreviventes).

### 3.2 Correções de Base e Remediação de Defeitos Críticos (SpotBugs P1)
Para viabilizar a automação contínua, foram aplicadas correções cirúrgicas à baseline:
1. **Regeneração do Maven Wrapper (`3.9.9`)** e transição para **H2 autónomo em memória** (`jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1`) por omissão.
2. **Sincronização dinâmica de anos no `Bootstrapper.java`** e abertura controlada do `/h2-console/**` com `sameOrigin()` em `SecurityConfig.java`.
3. **Resolução dos 3 Defeitos de Prioridade 1 (High Priority) detetados pelo SpotBugs:**
   * **`DM_DEFAULT_ENCODING` (`ForbiddenNameServiceImpl.java`):** Substituição de `new InputStreamReader(is)` por `new InputStreamReader(is, StandardCharsets.UTF_8)`, eliminando corrupção de caracteres entre agentes Windows (`Cp1252`) e Linux (`UTF-8`).
   * **`DLS_DEAD_LOCAL_STORE` (`User.java`):** Remoção de atribuição local morta na validação de `Password`.
   * **`SE_BAD_FIELD` (`Name.java` / `FineView.java`):** Implementação de `Serializable` com `serialVersionUID = 1L` no Value Object `Name` (embutido em `User implements UserDetails`), prevenindo `NotSerializableException` em sessões HTTP distribuídas.

---

## 4. Arquitetura Alvo de Continuous Delivery e Topologia Multi-VM (Goal 1.3.2)

A especificação completa da arquitetura alvo e as decisões de desenho encontram-se em:
* **[Especificação Completa do System-to-be e ADRs](./System-to-be/system-to-be.md)**

### 4.1 Visão Geral da Arquitetura de Entrega Contínua
Seguindo as boas práticas de *Continuous Delivery* (Humble & Farley) e a arquitetura de referência institucional:

```mermaid
flowchart LR
    subgraph Localhost["Workstation Local (localhost)"]
        IDE["Developer IDE"]
        JENKINS["Jenkins Controller (:8080)<br/>+ Webhook Relay Plugin<br/>+ Copy Artifact Plugin"]
    end

    subgraph Cloud["GitHub & Relay Cloud"]
        GH["GitHub Repo<br/>(psoft-project-2026-1)"]
        WHR["Webhook Relay<br/>(Bucket: jenkins-plugin)"]
    end

    subgraph DEICloud["DEI Virtual Servers Private Cloud (vsgate-ssh.dei.isep.ipp.pt)"]
        subgraph VM1["VM 1: vs806 (SSH :10806) — DEV + UAT"]
            DEV["LMS @ DEV (:8081)<br/>/opt/psoft-g1/dev<br/>psoft-g1@dev.service"]
            UAT["LMS @ UAT (:8082)<br/>/opt/psoft-g1/uat<br/>psoft-g1@uat.service"]
        end
        subgraph VM2["VM 2: vs336 (SSH :10336) — PROD Isolada"]
            PROD["LMS @ PROD (:8080)<br/>/opt/psoft-g1/prod<br/>psoft-g1@prod.service"]
        end
    end

    IDE -->|"1. git push main"| GH
    GH -->|"2. Webhook POST"| WHR
    JENKINS -.->|"Subscrição WSS"| WHR
    WHR -->|"3. Evento Push"| JENKINS

    JENKINS -->|"4. Job lms-dev:<br/>Build + Tests + PIT + Deploy DEV"| DEV
    JENKINS -->|"5. Job lms-uat:<br/>CopyArtifact + Deploy UAT + Input Gate"| UAT
    JENKINS -->|"6. Job lms-prod:<br/>CopyArtifact + Audit + Deploy PROD"| PROD
```

### 4.2 Síntese das Decisões Arquiteturais de CI/CD (ADRs)
* **ADR-01 (Jenkins Local + Webhook Relay WSS + Deploy Remoto Multi-VM):** Execução pesada de compilação Maven e testes de mutação PITest ocorre no controlador Jenkins local (`localhost:8080`), evitando o bloqueio por exaustão de CPU/RAM dos contentores LXD do DEI. A receção de webhooks ocorre via ligação WebSocket segura de saída (`WSS`), sem necessidade de *port forwarding* nem exposição pública do Jenkins. A implantação ocorre remotamente via SSH/SCP nas VMs institucionais do DEI (`vs806` para DEV/UAT e `vs336` dedicada a PROD).
* **ADR-02 (Separação em 3 Pipelines em Cascata com *Single-Build Artifact* e *Stash*):** Divisão em 3 jobs (`lms-dev`, `lms-uat`, `lms-prod`). Apenas `lms-dev` compila e empacota o binário (`app.jar`). O job `lms-uat` utiliza `stash` e `agent none` durante a etapa `input` (até 7 dias), libertando 100% dos executores do Jenkins durante o *tempo humano* de aprovação.
* **ADR-03 (Quality Gates Automatizados de Análise Estática, Cobertura e Mutação):** Integração de SpotBugs (0 defeitos P1), JaCoCo e perfil Maven `-Pmutation` com threshold de mutação $\ge 30\%$ na fase `verify`.
* **ADR-04 (Gestão de Serviços via Systemd Parametrizado e Smoke Testing Contratual):** Substituição de processos ad-hoc por unidades `psoft-g1@<env>.service` com isolamento de sistema de ficheiros e validação pós-implantação via [`deploy/smoke-test.sh`](../../deploy/smoke-test.sh) verificando `GET /info` (aplicação, ambiente e commit SHA) e `GET /actuator/health/readiness` (`status="UP"`).

---

## 5. Implementação da Esteira CI/CD e Quality Gates (Goal 1.3.3)

A documentação técnica detalhada da pipeline encontra-se em:
* **[Pipeline: Build, Empacotamento e Análise Estática de Código](./Pipeline/ci-build-and-code-analysis.md)**
* **[Pipeline: Testes Automatizados, Cobertura, Mutação e Quality Gates](./Pipeline/ci-testing-coverage-and-quality-gates.md)**

### 5.1 Os 3 Pipelines Declarativos e Rastreabilidade de Proveniência
1. **[`.Jenkinsfile`](../../.Jenkinsfile) (`lms-dev`)**:
   * Executa `mvnw -B -Pmutation clean verify -Dpitest.mutationThreshold=30 -Drelease.commit="${env.GIT_COMMIT}"`.
   * Regista os relatórios JUnit (Surefire + Failsafe) e arquiva os relatórios HTML/XML do PITest no bloco `post { always { ... } }`.
   * Copia o binário em modo estritamente binário (`copy /B /Y` no Windows para evitar truncamento no byte `0x1A` ASCII EOF) para `artifact/app.jar` e gera `artifact/release.properties` (`CI_JOB`, `CI_BUILD`, `GIT_COMMIT`).
   * Implanta em **DEV** (`vs806:8081`), valida com o smoke test contratual, arquiva `validated/**` com *fingerprinting* criptográfico e dispara assincronamente `lms-uat`.
2. **[`.Jenkinsfile_uat`](../../.Jenkinsfile_uat) (`lms-uat`)**:
   * Recupera `validated/**` de `lms-dev` via `copyArtifacts` (sem recompilar), valida a correspondência de `CI_BUILD` e implanta em **UAT** (`vs806:8082`).
   * Executa `stash name: 'uat-artifacts'` e liberta o agente (`agent none`) enquanto aguarda aprovação humana (`input` com botão *"Aceitar e promover"*).
   * Após aprovação, aloca agente, faz `unstash`, executa uma **reverificação de saúde (`deploy.recheck('uat')`)** para garantir que o serviço UAT permanece vivo após o período de espera humana, gera `acceptance.properties` (`ACCEPTED_BY`, `approval_timestamp`), arquiva `accepted/**` e dispara `lms-prod`.
3. **[`.Jenkinsfile_prod`](../../.Jenkinsfile_prod) (`lms-prod`)**:
   * Recupera `accepted/**` de `lms-uat`, imprime o **PRODUCTION AUDIT TRAIL** completo na consola do Jenkins, implanta na máquina virtual isolada de **PROD** (`vs336:8080`), valida o smoke test contratual, gera `production.properties` e arquiva o pacote final auditado em `final_artifact/**`.

### 5.2 Matriz de Quality Gates Aplicados

| Etapa / Componente | Tipo de Gate | Critério / Limiar Exigido | Ação em Caso de Falha |
| :--- | :---: | :--- | :--- |
| **Compilação & Processamento de Anotações** | **Hard Gate** | 0 erros de compilação (Java 17, Lombok, MapStruct) | Aborta imediatamente a build (`FAILURE`). |
| **Análise Estática de Código (SpotBugs)** | **Hard Gate** | 0 defeitos de Alta Prioridade (P1) | Impede empacotamento e promoção. |
| **Testes Unitários (Maven Surefire)** | **Hard Gate** | **100% aprovação (330/330 testes)** | Aborta a build (`FAILURE`) e publica relatório JUnit. |
| **Testes de Integração (Maven Failsafe)** | **Hard Gate** | **100% aprovação (44/44 testes)** | Corre *teardown* completo, publica JUnit e falha na fase `verify`. |
| **Testes de Mutação (PITest `-Pmutation`)** | **Hard Gate** | Threshold de mutação $\ge 30\%$ (**Test Strength atual: 84%**) | Maven aborta a fase `verify` se o score cair abaixo do limiar. |
| **Empacotamento e Integridade Binária** | **Hard Gate** | `app.jar` íntegro (~72 MB) + `release.properties` | Aborta antes do deploy se o ficheiro faltar ou estiver corrompido. |
| **Readiness Smoke Test (`smoke-test.sh`)** | **Hard Gate** | HTTP 200 em `/info` (`psoft-g1`, `env`, `commit`) e `/actuator/health/readiness` (`UP`) em $\le 60\text{s}$ | Aborta o deploy/promoção e exibe logs do `systemd` (`journalctl`). |

---

## 6. Estratégia de Testes Automatizados, Cobertura e Mutação (Goal 1.3.4)

O desenho completo da estratégia de testes, classificação multi-dimensional e provas de deteção de defeitos encontram-se em:
* **[Estratégia de Testes Automatizados, Avaliação de Qualidade e Deteção de Defeitos](./testing-strategy/testing-strategy.md)**

### 6.1 Evolução e Segregação da Suite de Testes (374 Testes Automatizados)
A suite de testes foi expandida de **102 testes misturados** para **374 testes automatizados (100% de aprovação)** estritamente segregados por ciclo de vida:
* **Nível 1 — Testes Unitários Isolados (`maven-surefire-plugin`, fase `test`):** **330 testes** executados em ~12 segundos em memória (sem contexto Spring nem base de dados), cobrindo:
  * *Value Objects e Utilitários* (54 testes) e *Raízes de Agregado / Entidades de Domínio* (72 testes);
  * *Serviços Aplicacionais Core* (`LendingServiceImplTest`, `BookServiceImplTest`, `AuthorServiceImplTest`, `UserServiceTest`, `ReaderServiceImplTest`, `GenreServiceImplTest`);
  * *Gateways e Adaptadores Bibliográficos* (`OpenLibraryAdapterTest`, `GoogleBooksAdapterTest`, `CompositeBookInfoProviderTest`);
  * *Notificações Multicanal e Eventos de Domínio* (`LendingNotificationListenerTest`, `Email/Sms/WebhookNotificationSenderTest`, `CompositeNotificationSenderTest`, `LendingEventsTest`);
  * *Controladores e Configuração* (`AuthorControllerTest`, `InfoControllerTest`, `BibliographicConfigTest`, `NotificationConfigTest`).
* **Nível 2 — Testes de Integração (`maven-failsafe-plugin`, fase `verify`):** **44 testes** executados contra base de dados H2 em memória (`Fake`), validando autenticação JWT (`AuthApiIntegrationTest`), controlo de acessos RBAC por perfil (`READER` vs `LIBRARIAN`), bloqueio otimista e concorrência (`LendingServiceImplIT`), consultas JPA (`LendingRepositoryIntegrationTest`), enriquecimento bibliográfico externo (`BookExternalInfoIT`) e despacho transacional de notificações com isolamento de falhas (`LendingNotificationIntegrationIT`).

### 6.2 Aplicação do Continuum de Test Doubles e Deteção Empírica de Defeitos
1. **Taxonomia de Test Doubles (Meszaros / Fowler):** Implementação documentada com código real dos 5 tipos de duplos: **Dummy** (preenchimento de parâmetros em `BookTest`), **Stub** (respostas indiretas pré-programadas em `BookServiceImplTest`), **Fake** (base de dados H2 in-memory nas suites Failsafe), **Spy** (`ArgumentCaptor<Lending>` em `LendingServiceImplTest`) e **Mock** (verificação comportamental `verify(..., never())` em `LendingServiceImplTest`).
2. **Deteção de Defeito Fatal Real (Duplo Hashing BCrypt):** A criação de `AuthApiIntegrationTest` revelou que `UserService.java` codificava em BCrypt uma password que já tinha sido codificada no construtor de `Reader`/`Librarian`, tornando todos os logins impossíveis (`401 Unauthorized`).
3. **Experiências de Injeção de Falhas, Concorrência e Mutantes Equivalentes:**
   * Demonstração de captura imediata de regressão de fronteira (`count >= 3` $\rightarrow$ `count > 3` em `LendingServiceImpl`).
   * Prova de deteção de condições de corrida com `@Version` (`StaleObjectStateException`) em `LendingServiceImplIT`.
   * Análise qualitativa de **Mutantes Equivalentes** (bytecode sintético de `hashCode`/`equals` do Lombok, guardas defensivas redundantes e proxies JPA).

---

## 7. Implantação Configurável, Hardening Systemd e Smoke Testing (Goal 1.3.5)

O guia operacional detalhado para configuração das máquinas virtuais Ubuntu do DEI e do Jenkins encontra-se em:
* **[Manual de Preparação e Operação de Ambientes (`deploy/README.md`)](../../deploy/README.md)**

### 7.1 Isolamento Multi-VM e Serviços Systemd Parametrizados
Os três ambientes são provisionados pelo script idempotente [`deploy/setup-environments.sh`](../../deploy/setup-environments.sh) usando o template [`deploy/psoft-g1@.service`](../../deploy/psoft-g1@.service):

| Ambiente | VM DEI | Porta SSH | Porta App | Diretório Remoto | Ficheiro de Ambiente | Unidade Systemd | Utilizador OS |
| :---: | :---: | :---: | :---: | :--- | :--- | :--- | :--- |
| **DEV** | `vs806` | `10806` | `8081` | `/opt/psoft-g1/dev` | `/etc/psoft-g1/dev.env` | `psoft-g1@dev.service` | `psoft-g1-dev` |
| **UAT** | `vs806` | `10806` | `8082` | `/opt/psoft-g1/uat` | `/etc/psoft-g1/uat.env` | `psoft-g1@uat.service` | `psoft-g1-uat` |
| **PROD** | `vs336` | `10336` | `8080` | `/opt/psoft-g1/prod` | `/etc/psoft-g1/prod.env` | `psoft-g1@prod.service` | `psoft-g1-prod` |

### 7.2 Contrato de Observabilidade e Smoke Test (`deploy/smoke-test.sh`)
Cada implantação executa [`deploy/smoke-test.sh`](../../deploy/smoke-test.sh) (até 30 tentativas com intervalos de 2 segundos), exigindo a validação JSON simultânea de dois endpoints:
1. **Identidade, Ambiente e Rastreabilidade de Commit (`GET /info`):** Exposto por [`InfoController.java`](../../src/main/java/pt/psoft/g1/psoftg1/configuration/InfoController.java) e alimentado por `META-INF/build-info.properties` (`spring-boot-maven-plugin:build-info`), confirmando que `"application" == "psoft-g1"`, `"environment"` coincide com o ambiente alvo (`dev`, `uat` ou `prod`) e `"commit"` corresponde exatamente ao hash Git da build promovida.
2. **Prontidão Operacional (`GET /actuator/health/readiness`):** Exposto pelo Spring Boot Actuator, confirmando `"status" == "UP"`.

---

## 8. Sinergia ARQSOFT $\leftrightarrow$ ODSOFT e Evolução Global (*As-Is* vs. *To-Be*) (Goal 1.3.6)

### 8.1 Sinergia entre Decisões Arquiteturais (ARQSOFT) e CI/CD (ODSOFT)
Por partilharem o mesmo código-fonte e repositório, as decisões arquiteturais de **[ARQSOFT](../ARQSOFT/arqsoft-documentation.md)** e a esteira de **ODSOFT** reforçam-se mutuamente:
1. **Configurabilidade em *Setup Time* $\leftrightarrow$ *Single-Build Immutable Artifact*:** A seleção externa de fornecedores bibliográficos (`application.bibliographic.providers`) e de canais de notificação (`notification.channels`) via propriedades/variáveis de ambiente permite que **exatamente o mesmo binário `app.jar`** seja promovido de `DEV` para `UAT` e `PROD` sem recompilação.
2. **Padrões *Adapter*, *Gateway*, *Composite* e *Observer* $\leftrightarrow$ Testes Herméticos no Pipeline:** O desacoplamento das APIs externas (Google Books, Open Library, servidores SMTP/SMS/Webhook) através de interfaces e injeção de dependências permite que as **374 execuções de teste no Jenkins** corram de forma 100% determinística e offline através de *Test Doubles*, sem falhas por indisponibilidade de redes externas.
3. **Isolamento de Falhas (`@TransactionalEventListener`) $\leftrightarrow$ Estabilidade em Produção:** Os testes de integração (`LendingNotificationIntegrationIT`) validam na fase `verify` do pipeline que falhas em canais de notificação externos nunca provocam *rollback* das transações de empréstimo.

### 8.2 Matriz Comparativa Global de Evolução (*System-as-is* vs. *System-to-be*)

| Dimensão / Indicador | Estado Inicial (*System-as-is*) | Estado Alvo Implementado (*System-to-be*) | Melhoria / Impacto |
| :--- | :--- | :--- | :--- |
| **Orquestração de CI/CD** | Inexistente (compilação e testes manuais) | **3 Pipelines Declarativos em Cascata** (`lms-dev`, `lms-uat`, `lms-prod`) | Automação ponta-a-ponta desde o `git push` até Produção |
| **Gatilho de Integração (Trigger)** | Nenhum | **Webhook Relay (túnel seguro WSS)** para Jenkins Local (`localhost:8080`) | Feedback imediato sem expor portas HTTP públicas |
| **Gestão de Binários** | Recompilado manualmente em cada máquina | **Single-Build Immutable Artifact (`app.jar`)** com *fingerprinting* | Garantia matemática de que o binário testado é o que entra em PROD |
| **Rastreabilidade e Auditoria** | Nenhuma | `release.properties` + `acceptance.properties` + `production.properties` + `/info` | Trilha de auditoria completa (Commit, Build, Aprovador, Timestamp) |
| **Análise Estática (SAST)** | Nenhuma (125 avisos, 3 bugs P1 ativos) | **SpotBugs (`4.8.3.1`)** integrado com filtro `spotbugs-exclude.xml` | **0 defeitos P1** (correção de encoding UTF-8, serialização e dead store) |
| **Total de Testes Automatizados** | 102 testes (misturados em `mvn test`) | **374 testes** (**330 unitários Surefire** + **44 integração Failsafe**) | **+266.7% testes** com separação estrita de fases (`test` vs `verify`) |
| **Cobertura de Código (JaCoCo)** | Não configurada (~33% estimada pelo PIT) | **JaCoCo (`0.8.11`)** unitário e integração (69%–100% nos módulos core) | 100% dos serviços e modelos core cumprem a meta $\ge 60\%$ |
| **Testes de Mutação (PITest)** | 22% mutações mortas (124/575), 70% *strength* | **662 mutações mortas em 2.999 geradas**, **84% *Test Strength* global** | **+433.9% mutantes eliminados**; 100% dos pacotes core $\ge 50\%$ |
| **Gestão da Aprovação Humana** | Nenhuma | Etapa `input` em UAT com **`stash`/`unstash` e `agent none`** + *Recheck* | Zero consumo de executores Jenkins durante espera humana (até 7 dias) |
| **Topologia e Hardening de Deploy** | Execução manual local sem isolamento | **Multi-VM DEI Cloud** (`vs806` DEV/UAT + `vs336` PROD) com **systemd `psoft-g1@.service`** | Isolamento físico de Produção, utilizadores sem login e `ProtectSystem=strict` |
| **Validação Pós-Deploy (Smoke Test)** | Verificação manual ad-hoc via Postman | **Smoke Test Contratual (`smoke-test.sh`)** em `/info` e `/actuator/health/readiness` | Validação automática de versão, ambiente, commit SHA e estado `UP` |

