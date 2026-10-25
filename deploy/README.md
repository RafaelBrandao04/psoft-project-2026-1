# Preparação dos Ambientes DEV, UAT e PROD (LMS - psoft-g1)

Este guia descreve a arquitetura de implantação contínua e a preparação dos servidores virtuais Ubuntu no DEI Virtual Servers Private Cloud (`vsgate-ssh.dei.isep.ipp.pt`), com base na arquitetura de referência oficial do projeto exemplo de CI/CD com Jenkins.

---

## 1. Topologia de Ambientes e Isolamento Multi-VM

O sistema é implantado em duas máquinas virtuais do DEI para garantir isolamento físico entre desenvolvimento e produção:

| Ambiente | Máquina Virtual | Porta Externa SSH | Porta Privada Aplicação | Diretório de Trabalho | Unidade Systemd | Utilizador do Serviço |
|---|---|---|---|---|---|---|
| **DEV** | `vs806` | `10806` | `8081` | `/opt/psoft-g1/dev` | `psoft-g1@dev.service` | `psoft-g1-dev` |
| **UAT** | `vs806` | `10806` | `8082` | `/opt/psoft-g1/uat` | `psoft-g1@uat.service` | `psoft-g1-uat` |
| **PROD** | `vs336` | `10336` | `8080` | `/opt/psoft-g1/prod` | `psoft-g1@prod.service` | `psoft-g1-prod` |

---

## 2. Preparação do Servidor Ubuntu (Passo a Passo)

### 2.1. Na Máquina de DEV / UAT (`vs806` — Porta SSH 10806)

1. A partir do teu terminal local, copia a pasta `deploy` para a VM `vs806`:
   ```bash
   scp -r -P 10806 deploy root@vsgate-ssh.dei.isep.ipp.pt:~/psoft-g1-setup
   ssh -p 10806 root@vsgate-ssh.dei.isep.ipp.pt
   ```

2. Dentro da VM como `root`:
   ```bash
   cd ~/psoft-g1-setup
   chmod +x setup-environments.sh smoke-test.sh
   # Configura DEV na porta 8081, UAT na porta 8082 e PROD na porta 8080
   sh setup-environments.sh 8081 8082 8080
   ```

### 2.2. Na Máquina de PROD (`vs336` — Porta SSH 10336)

1. Copia a pasta `deploy` para a VM `vs336`:
   ```bash
   scp -r -P 10336 deploy root@vsgate-ssh.dei.isep.ipp.pt:~/psoft-g1-setup
   ssh -p 10336 root@vsgate-ssh.dei.isep.ipp.pt
   ```

2. Dentro da VM como `root`:
   ```bash
   cd ~/psoft-g1-setup
   chmod +x setup-environments.sh smoke-test.sh
   sh setup-environments.sh 8081 8082 8080
   ```

---

## 3. Componentes Criados pelo Setup

O script `setup-environments.sh` realiza automaticamente:
1. **Utilizador `deploy`**: Utilizador dedicado para transferências SCP e execução remota de pipelines.
2. **Utilizadores de Serviço de Menor Privilégio**: `psoft-g1-dev`, `psoft-g1-uat`, `psoft-g1-prod` (sem login de shell `/usr/sbin/nologin`).
3. **Unidade Systemd Parametrizada**: `/etc/systemd/system/psoft-g1@.service` com isolamento estrito (`ProtectSystem=strict`, `ProtectHome=true`, `PrivateTmp=true`, `NoNewPrivileges=true`).
4. **Ficheiros de Ambiente**:
   - `/etc/psoft-g1/dev.env` (`DEMO_ENVIRONMENT=dev`, `PORT=8081`)
   - `/etc/psoft-g1/uat.env` (`DEMO_ENVIRONMENT=uat`, `PORT=8082`)
   - `/etc/psoft-g1/prod.env` (`DEMO_ENVIRONMENT=prod`, `PORT=8080`)
5. **Regras Sudoers Limitadas**: `/etc/sudoers.d/psoft-g1` permitindo ao utilizador `deploy` apenas iniciar/parar/consultar o estado de `psoft-g1@*.service` sem palavra-passe.

---

## 4. Configuração no Jenkins (Variáveis Globais)

No Jenkins em `Gerir o Jenkins` -> `System` -> `Global properties` -> `Environment variables`:

| Variável | Valor Configurado | Descrição |
|---|---|---|
| `DEMO_SSH_TARGET` | `root@vsgate-ssh.dei.isep.ipp.pt` | Gateway SSH institucional do DEI |
| `DEMO_SSH_PORT` | `10806` | Porta SSH externa da VM `vs806` (DEV/UAT) |
| `DEMO_PROD_SSH_PORT` | `10336` | Porta SSH externa da VM `vs336` (PROD) |
| `DEMO_DEV_URL` | `http://localhost:8081` | Endpoint para smoke test de DEV |
| `DEMO_UAT_URL` | `http://localhost:8082` | Endpoint para smoke test de UAT |
| `DEMO_PROD_URL` | `http://localhost:8080` | Endpoint para smoke test de PROD |

---

## 5. Smoke Testing e Validação de Prontidão

O script [`deploy/smoke-test.sh`](smoke-test.sh) valida ponta-a-ponta a aplicação após cada implantação:
1. `GET /info` (HTTP 200) com JSON:
   ```json
   {
     "application": "psoft-g1",
     "version": "0.0.1-SNAPSHOT",
     "commit": "<GIT_COMMIT_SHA>",
     "environment": "dev|uat|prod"
   }
   ```
2. `GET /actuator/health/readiness` (ou `/actuator/health`) (HTTP 200) com:
   ```json
   {
     "status": "UP"
   }
   ```
3. Se o serviço não responder dentro de 30 tentativas (com 2s de intervalo), o smoke test falha e o pipeline aborta com os logs do serviço para diagnóstico.

