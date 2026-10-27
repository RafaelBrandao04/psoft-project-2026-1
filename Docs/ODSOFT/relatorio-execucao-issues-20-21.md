# Relatório de Execução e Fundamentação Técnica: Issues #20 e #21 (ODSOFT)

**Unidade Curricular:** Organização do Desenvolvimento de Software (ODSOFT) — 2026/2027  
**Projeto:** P1 — CI/CD Pipeline, Quality Assurance, and DevOps Evolution  
**Autor:** Gonçalo Neto (`Goncalo-Neto-isep`)  
**Data:** 26 de Outubro de 2026  
**Issues Concluídas:**
* **Issue #20:** `[ODSOFT] Support Multi-Environment Configuration and Repeatable Deployment Isolation`
* **Issue #21:** `[ODSOFT] Implement Automated Deployment & Health Monitoring in Pipeline`

---

## 1. Enquadramento e Alinhamento com o Enunciado Oficial

O enunciado oficial do Projeto 1 de ODSOFT (`Enunciado - ODSOFT.pdf`) estabelece no seu ponto **1.3 (Goals)** dois objetivos operacionais críticos que se encontravam pendentes na baseline do projeto:

1. **Goal 1.3.5 — *Support configurable and repeatable deployment of the application across development, staging, and production environments:***
   * A aplicação original carecia de isolamento de infraestrutura, registava fotos em caminhos relativos sem supervisão de serviço e dependia de propriedades codificadas no binário (*hardcoded*), violando os princípios de entrega contínua.
   * **A Issue #20 resolve integralmente este objetivo**, adotando a arquitetura de referência da unidade curricular: externalização completa de propriedades num único [`src/main/resources/application.properties`](../../src/main/resources/application.properties) com placeholders `${VAR:default}`, template padronizado [`.env.example`](../../.env.example), injeção de variáveis por ambiente via `/etc/psoft-g1/<env>.env` e isolamento ao nível do sistema operativo através de unidades parametrizadas `systemd` ([`deploy/psoft-g1@.service`](../../deploy/psoft-g1@.service)) provisionadas pelo script [`deploy/setup-environments.sh`](../../deploy/setup-environments.sh).
2. **Goal 1.3.3 — *Implement an automated CI/CD pipeline supporting: creation and management of deployable artifacts; deployment to different environments:***
   * A entrega de software não pode depender de processos manuais ou de compilações repetidas entre ambientes. O pipeline necessita de garantir a proveniência imutável do código e validar de forma autónoma a saúde operacional do serviço.
   * **A Issue #21 resolve integralmente este objetivo**, consolidando a esteira de deployment multi-job em Jenkins (`lms-dev`, `lms-uat`, `lms-prod`), a gestão e auditoria do binário imutável (`app.jar`) com *fingerprinting*, e a verificação automática de saúde pós-deploy (*Readiness Smoke Testing* via Spring Boot Actuator e `/info`).

---

## 2. Implementação da Issue #20: Configuração Multi-Ambiente e Isolamento Operacional

Abaixo detalha-se o conjunto de alterações implementadas, bem como a fundamentação técnica e arquitetural de cada decisão.

### 2.1. Externalização de Configuração (Twelve-Factor App)

A gestão de configuração foi remodelada para respeitar o **Factor III (*Store config in the environment*)** e o **Factor X (*Dev/prod parity*)** da metodologia *The Twelve-Factor App*, mantendo um único artefacto `app.jar` imutável entre ambientes.

#### 1. Ficheiro Unificado [`src/main/resources/application.properties`](../../src/main/resources/application.properties)
Todas as configurações com dependência do ambiente foram convertidas para placeholders parametrizáveis com valores padrão seguros para desenvolvimento local e execução de testes:
```properties
spring.application.name=psoft-g1
spring.config.import=optional:file:.env[.properties]
spring.profiles.active=${SPRING_PROFILES_ACTIVE:bootstrap}

server.port=${PORT:8080}
demo.environment=${DEMO_ENVIRONMENT:local}

spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;IGNORECASE=TRUE}
spring.datasource.driverClassName=${SPRING_DATASOURCE_DRIVER:org.h2.Driver}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:mysqluser}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:mysqlpass}

spring.h2.console.enabled=${SPRING_H2_CONSOLE_ENABLED:true}
file.upload-dir=${FILE_UPLOAD_DIR:uploads-psoft-g1}
my.ninjas-key=${NINJAS_API_KEY:a5nSlaa4JxIubY09H+NYuQ==cY9FegnFmAvYi6fN}
```

#### 2. Ficheiro Template [`.env.example`](../../.env.example) e Suporte Local Automático
Criado na raiz do repositório, documenta todas as variáveis de ambiente suportadas pelo sistema (portas, strings de conexão H2, credenciais, caminhos de chaves JWT, provedores bibliográficos, canais de notificação e políticas de empréstimo). Graças à diretiva `spring.config.import=optional:file:.env[.properties]`, qualquer programador pode criar um ficheiro `.env` local (por exemplo, `PORT=8085` para evitar colisão com o servidor Jenkins local na porta `8080`) sem afetar o repositório nem exigir que os restantes elementos da equipa criem um `.env`.

### 2.2. Isolamento Multi-Ambiente com `systemd` e Utilizadores Não-Privilegiados

Em vez de sobrecarregar as máquinas virtuais do DEI (que possuem limites estritos de RAM/CPU) com um daemon Docker, o isolamento e a repetibilidade entre **DEV**, **UAT** e **PROD** seguem a arquitetura de referência com unidades parametrizadas [`deploy/psoft-g1@.service`](../../deploy/psoft-g1@.service) e aprovisionamento automatizado via [`deploy/setup-environments.sh`](../../deploy/setup-environments.sh):

1. **Princípio do Menor Privilégio (Utilizadores Não-Root):** Cada instância corre sob um utilizador de sistema Linux dedicado e sem shell (`psoft-g1-dev`, `psoft-g1-uat`, `psoft-g1-prod`).
2. **Injeção de Ambiente (`EnvironmentFile`):** O `systemd` carrega automaticamente `/etc/psoft-g1/<env>.env` (`DEMO_ENVIRONMENT` e `PORT`) antes de invocar o mesmo binário `/opt/psoft-g1/<env>/app.jar`.
3. **Hardening de Sistema Operativo:** As diretivas `ProtectSystem=strict`, `ProtectHome=true`, `PrivateTmp=true`, `NoNewPrivileges=true` e `ReadWritePaths=/opt/psoft-g1/%i` isolam o sistema de ficheiros de cada instância.

---

## 3. Implementação da Issue #21: Deployment Automatizado e Health Monitoring na Pipeline

A Issue #21 garante que o artefacto gerado é imutável e auditável, e que o pipeline valida automaticamente a saúde da aplicação antes de promover qualquer versão.

### 3.1. Gestão de Artefactos de Implantação e Trilha de Auditoria

Em estreita conformidade com o padrão **Single-Build Immutable Artifact Pattern** (ADR-02 do projeto):

1. **Construção Única:** O fat JAR executável `app.jar` (~72 MB) é compilado e empacotado **exclusivamente uma vez** no estágio de CI do job DEV (`lms-dev`).
2. **Transferência Segura no Windows:** A cópia local utiliza modo estritamente binário (`copy /B /Y target\psoft-g1-*.jar artifact\app.jar`), eliminando o risco de truncamento no byte `0x1A` (ASCII EOF) no Windows.
3. **Criptografia e Rastreabilidade (*Fingerprinting*):** O Jenkins calcula o *fingerprint* SHA-256 do ficheiro `app.jar` arquivado em `validated/**`. O job UAT (`lms-uat`) e o job PROD (`lms-prod`) utilizam o plugin `copyArtifacts` para descarregar este ficheiro binário exato, sem qualquer recompilação em downstream.
4. **Metadados de Proveniência:** A pipeline gera e arquiva ficheiros de metadados em cada estágio:
   * `release.properties` (DEV): Grava `CI_JOB`, `CI_BUILD`, `GIT_COMMIT` e `commit`.
   * `acceptance.properties` (UAT): Grava o utilizador autorizado que aprovou a promoção (`ACCEPTED_BY`), a build UAT e o carimbo de data/hora ISO 8601 (`approval_timestamp`).
   * `production.properties` (PROD): Regista o carimbo de implantação final em produção e os parâmetros de auditoria cruzados.

---

### 3.2. Contrato de Health Monitoring e Smoke Testing Pós-Deploy

O processo de deployment executa o script contratual [`deploy/smoke-test.sh`](../../deploy/smoke-test.sh), que atua como um **Hard Quality Gate** intransponível:

#### Contrato 1: Endpoint de Identidade e Commit (`GET /info`)
Alimentado pelo [`InfoController.java`](../../src/main/java/pt/psoft/g1/psoftg1/configuration/InfoController.java) e pelas `BuildProperties` do Spring Boot Maven Plugin, devolve:
```json
{
  "application": "psoft-g1",
  "version": "0.0.1-SNAPSHOT",
  "commit": "681267329b68d9e0489b165b79c3d285cc912906",
  "environment": "dev"
}
```
O script valida que o nome da aplicação é `"psoft-g1"`, o ambiente é o esperado (`dev`, `uat` ou `prod`) e o hash do commit Git corresponde exatamente à build que disparou o pipeline.

#### Contrato 2: Endpoint de Prontidão Operacional (`GET /actuator/health/readiness`)
Exposto pelo Spring Boot Actuator, valida se os subsistemas internos estão prontos para receber tráfego (`"status": "UP"`).

#### Semântica de Falha e Diagnóstico Automático
* **Janela de Polling:** Executa até **30 tentativas** com intervalos de **2 segundos** (tolerância máxima de 60 segundos para arranque da JVM).
* **Ação em Caso de Falha:** Se a aplicação não responder HTTP 200 com os JSONs corretos em 60 segundos:
  1. O script termina com código de saída `1`;
  2. O módulo [`deploy/jenkins-deploy.groovy`](../../deploy/jenkins-deploy.groovy) interceta a exceção e invoca `journalctl -u psoft-g1@<env>.service -n 30` para a consola do Jenkins;
  3. A build é abortada com o estado `FAILURE`, bloqueando qualquer promoção para UAT ou Produção.

---

### 3.3. Topologia Multi-VM e Supervisão com Systemd

| Ambiente | Máquina Virtual | Porta SSH Externa | Porta Aplicação | Diretório Remoto | Ficheiro de Ambiente | Utilizador de Serviço |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **DEV** | `vs806` | `10806` | `8081` | `/opt/psoft-g1/dev` | `/etc/psoft-g1/dev.env` | `psoft-g1-dev` |
| **UAT** | `vs806` | `10806` | `8082` | `/opt/psoft-g1/uat` | `/etc/psoft-g1/uat.env` | `psoft-g1-uat` |
| **PROD** | `vs336` | `10336` | `8080` | `/opt/psoft-g1/prod` | `/etc/psoft-g1/prod.env` | `psoft-g1-prod` |

---

## 4. Matriz de Ficheiros de Infraestrutura e Documentação

| Ficheiro | Issue | Descrição Sumária |
| :--- | :---: | :--- |
| [`.env.example`](../../.env.example) | **#20** | Template completo de variáveis de ambiente e documentação de parâmetros. |
| [`src/main/resources/application.properties`](../../src/main/resources/application.properties) | **#20** | Externalização de propriedades para variáveis de ambiente com fallbacks `${VAR:default}` e import opcional de `.env`. |
| [`deploy/psoft-g1@.service`](../../deploy/psoft-g1@.service) | **#20, #21** | Unidade parametrizada `systemd` com utilizador não-privilegiado e hardening do SO. |
| [`deploy/setup-environments.sh`](../../deploy/setup-environments.sh) | **#20, #21** | Aprovisionamento idempotente de utilizadores, pastas `/opt/psoft-g1/<env>` e `/etc/psoft-g1/<env>.env`. |
| [`deploy/jenkins-deploy.groovy`](../../deploy/jenkins-deploy.groovy) | **#21** | Módulo partilhado de deployment multi-VM via SSH/SCP, gestão `systemd` e verificação. |
| [`deploy/smoke-test.sh`](../../deploy/smoke-test.sh) | **#21** | Validação contratual pós-deploy (`GET /info` + `GET /actuator/health/readiness`). |
| [`Docs/ODSOFT/deployment/containerization-and-multi-environment.md`](deployment/containerization-and-multi-environment.md) | **#20** | Documentação técnica da configuração multi-ambiente e isolamento `systemd`. |
| [`Docs/ODSOFT/Pipeline/ci-deployment-and-health-monitoring.md`](Pipeline/ci-deployment-and-health-monitoring.md) | **#21** | Documentação técnica do pipeline de deploy, proveniência e smoke testing. |
| [`Docs/ODSOFT/relatorio-execucao-issues-20-21.md`](relatorio-execucao-issues-20-21.md) | **#20, #21** | Este relatório executivo e técnico detalhado. |
