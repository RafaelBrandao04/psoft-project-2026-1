// deploy/jenkins-deploy.groovy
// Shared deployment module for DEV, UAT, and PROD supporting both Systemd and Cross-Platform Agents

def parseProperties(String path) {
    def props = [:]
    def content = readFile(file: path, encoding: 'UTF-8')
    content.split('\r?\n').each { line ->
        def trimmed = line.trim()
        if (trimmed && !trimmed.startsWith('#') && trimmed.contains('=')) {
            def parts = trimmed.split('=', 2)
            props[parts[0].trim()] = parts[1].trim()
        }
    }
    return props
}

def settings(String environment) {
    def envLower = environment.toLowerCase()
    if (!(envLower in ['dev', 'uat', 'prod'])) {
        error("Ambiente desconhecido: ${environment}. Suportados: dev, uat, prod")
    }

    // Suporta multi-VM institucional ou variáveis globais configuradas no Jenkins
    if (envLower == 'prod') {
        return [
            target: env.DEMO_PROD_SSH_TARGET ?: env.DEMO_SSH_TARGET ?: 'root@vsgate-ssh.dei.isep.ipp.pt',
            port: env.DEMO_PROD_SSH_PORT ?: env.DEMO_SSH_PORT ?: '10336',
            credentialsId: env.DEI_PROD_VM_CREDENTIALS_ID ?: 'dei-prod-vm-ssh-key',
            url: (env.DEMO_PROD_URL ?: 'http://localhost:8080').replaceAll('/$', ''),
            vmName: 'vs336 (PROD VM)',
            appPort: 8080
        ]
    } else {
        def defaultPort = (envLower == 'dev') ? 8081 : 8082
        def customUrl = (envLower == 'dev') ? env.DEMO_DEV_URL : env.DEMO_UAT_URL
        return [
            target: env.DEMO_DEV_SSH_TARGET ?: env.DEMO_SSH_TARGET ?: 'root@vsgate-ssh.dei.isep.ipp.pt',
            port: env.DEMO_DEV_SSH_PORT ?: env.DEMO_SSH_PORT ?: '10806',
            credentialsId: env.DEI_DEV_VM_CREDENTIALS_ID ?: 'dei-vm-ssh-key',
            url: (customUrl ?: "http://localhost:${defaultPort}").replaceAll('/$', ''),
            vmName: 'vs806 (DEV/UAT VM)',
            appPort: defaultPort
        ]
    }
}

def executeRemote(Map config, String cmd) {
    withCredentials([sshUserPrivateKey(credentialsId: config.credentialsId, keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
        def targetUserHost = config.target.contains('@') ? config.target : "${SSH_USER}@${config.target}"
        if (isUnix()) {
            def sshOpts = "-i \"${SSH_KEY}\" -p ${config.port} -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=10 -o BatchMode=yes"
            sh "ssh ${sshOpts} ${targetUserHost} \"${cmd}\""
        } else {
            bat """@echo off
set "WIN_KEY=%SSH_KEY:/=\\%"
icacls "%WIN_KEY%" /inheritance:r /grant:r "*S-1-5-18:(F)" /grant:r "*S-1-5-32-544:(F)" >nul
icacls "%WIN_KEY%" /remove "*S-1-5-32-545" >nul
ssh -i "%WIN_KEY%" -p ${config.port} -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=10 -o BatchMode=yes ${targetUserHost} "${cmd}"
"""
        }
    }
}

def copyRemote(Map config, String localFile, String remoteFile) {
    withCredentials([sshUserPrivateKey(credentialsId: config.credentialsId, keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
        def targetUserHost = config.target.contains('@') ? config.target : "${SSH_USER}@${config.target}"
        if (isUnix()) {
            def scpOpts = "-i \"${SSH_KEY}\" -P ${config.port} -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=10 -o BatchMode=yes"
            sh "scp ${scpOpts} \"${localFile}\" ${targetUserHost}:\"${remoteFile}\""
        } else {
            def winLocalFile = localFile.replace('/', '\\')
            bat """@echo off
set "WIN_KEY=%SSH_KEY:/=\\%"
icacls "%WIN_KEY%" /inheritance:r /grant:r "*S-1-5-18:(F)" /grant:r "*S-1-5-32-544:(F)" >nul
icacls "%WIN_KEY%" /remove "*S-1-5-32-545" >nul
scp -i "%WIN_KEY%" -P ${config.port} -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=10 -o BatchMode=yes "${winLocalFile}" ${targetUserHost}:"${remoteFile}"
"""
        }
    }
}

def call(String environment) {
    def envLower = environment.toLowerCase()
    def config = settings(envLower)
    def deployment = "${env.JOB_NAME}#${env.BUILD_NUMBER}"
    
    echo "=========================================================="
    echo ">>> Deploying to [${envLower.toUpperCase()}] on ${config.vmName}"
    echo ">>> Target: ${config.target}:${config.port} | App Port: ${config.appPort}"
    echo "=========================================================="

    writeFile file: 'artifact/deployment.txt.new', text: "${deployment}\n"

    // 1. Garante que os diretórios de destino existem na máquina remota
    executeRemote(config, "mkdir -p /opt/psoft-g1/${envLower}")

    // 2. Garante a instalação do runtime (Java 17, curl e python3) se ainda não existirem
    executeRemote(config, "which java >/dev/null 2>&1 && which curl >/dev/null 2>&1 && which python3 >/dev/null 2>&1 || (DEBIAN_FRONTEND=noninteractive apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-17-jre-headless curl python3)")

    // 3. Transfere os ficheiros de forma atómica
    copyRemote(config, "artifact/app.jar", "/opt/psoft-g1/${envLower}/app.jar.new")
    copyRemote(config, "artifact/deployment.txt.new", "/opt/psoft-g1/${envLower}/deployment.txt.new")
    if (fileExists("deploy/smoke-test.sh")) {
        copyRemote(config, "deploy/smoke-test.sh", "/opt/psoft-g1/${envLower}/smoke-test.sh")
    }

    // 4. Cria e transfere script de reinício remoto (evita conflitos de caracteres/newlines no cmd.exe em Windows)
    def deployScript = """#!/bin/sh
set -eu
cd "/opt/psoft-g1/${envLower}"
test -s app.jar.new
if systemctl list-unit-files 2>/dev/null | grep -q 'psoft-g1@'; then
    systemctl stop "psoft-g1@${envLower}.service" || true
    mv -f app.jar.new app.jar
    chmod 644 app.jar
    systemctl start "psoft-g1@${envLower}.service"
else
    pkill -f '[s]erver.port=${config.appPort}' || pkill -f '[D]EMO_ENVIRONMENT=${envLower}' || true
    sleep 2
    mv -f app.jar.new app.jar
    chmod 644 app.jar
    nohup java -jar "/opt/psoft-g1/${envLower}/app.jar" --server.port=${config.appPort} --demo.environment=${envLower} > "/opt/psoft-g1/${envLower}/app.log" 2>&1 < /dev/null &
fi
mv -f deployment.txt.new deployed-build.txt
"""
    writeFile file: 'artifact/deploy-remote.sh', text: deployScript, encoding: 'UTF-8'
    copyRemote(config, "artifact/deploy-remote.sh", "/opt/psoft-g1/${envLower}/deploy-remote.sh")
    executeRemote(config, "sed -i 's/\\r\$//' /opt/psoft-g1/${envLower}/deploy-remote.sh && chmod +x /opt/psoft-g1/${envLower}/deploy-remote.sh && /bin/sh /opt/psoft-g1/${envLower}/deploy-remote.sh")

    // 5. Verifica a instalação e executa o smoke test
    verify(envLower, deployment)
    return deployment
}

def verify(String environment, String deployment) {
    def envLower = environment.toLowerCase()
    def config = settings(envLower)

    echo ">>> Running verification & smoke test for [${envLower.toUpperCase()}] on ${config.vmName}..."

    // Valida que o deployed-build.txt no servidor corresponde à instalação pretendida
    def actual = ""
    withCredentials([sshUserPrivateKey(credentialsId: config.credentialsId, keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
        def targetUserHost = config.target.contains('@') ? config.target : "${SSH_USER}@${config.target}"
        if (isUnix()) {
            actual = sh(script: "ssh -i \"${SSH_KEY}\" -p ${config.port} -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=10 -o BatchMode=yes ${targetUserHost} \"cat /opt/psoft-g1/${envLower}/deployed-build.txt\"", returnStdout: true).trim()
        } else {
            actual = bat(script: """@echo off
set "WIN_KEY=%SSH_KEY:/=\\%"
icacls "%WIN_KEY%" /inheritance:r /grant:r "*S-1-5-18:(F)" /grant:r "*S-1-5-32-544:(F)" >nul
icacls "%WIN_KEY%" /remove "*S-1-5-32-545" >nul
ssh -i "%WIN_KEY%" -p ${config.port} -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=10 -o BatchMode=yes ${targetUserHost} "cat /opt/psoft-g1/${envLower}/deployed-build.txt"
""", returnStdout: true).trim()
            // Remove echo artifacts
            def lines = actual.split('\r?\n')
            actual = lines ? lines[lines.length - 1].trim() : ""
        }
    }

    if (actual && actual != deployment) {
        echo "Aviso: deployed-build.txt indica '${actual}', esperado '${deployment}'"
    }

    // Determina o commit esperado para validação
    def expectedCommit = "local"
    if (fileExists('artifact/release.properties')) {
        def releaseProps = parseProperties('artifact/release.properties')
        expectedCommit = releaseProps['commit'] ?: releaseProps['GIT_COMMIT'] ?: 'local'
    }

    // Executa o script de smoke test oficial no servidor, com sanitização de CRLF e diagnóstico de logs em caso de falha
    def smokeTestCmd = "sed -i 's/\\r\$//' /opt/psoft-g1/${envLower}/smoke-test.sh && chmod +x /opt/psoft-g1/${envLower}/smoke-test.sh && /bin/sh /opt/psoft-g1/${envLower}/smoke-test.sh http://localhost:${config.appPort} ${envLower} ${expectedCommit}"
    try {
        executeRemote(config, smokeTestCmd)
    } catch (Exception e) {
        executeRemote(config, "journalctl -u psoft-g1@${envLower}.service -n 30 --no-pager 2>/dev/null || if [ -f /opt/psoft-g1/${envLower}/app.log ]; then tail -n 30 /opt/psoft-g1/${envLower}/app.log; fi")
        throw e
    }
    echo ">>> Deployment and smoke test for [${envLower.toUpperCase()}] COMPLETED SUCCESSFULLY!"
}

// Métodos de compatibilidade com .Jenkinsfile_common.groovy e scripts do professor
def verify(String environment) {
    verify(environment, "${env.JOB_NAME}#${env.BUILD_NUMBER}")
}

def deploy(String environment) {
    call(environment)
}

def recheck(String environment) {
    def envLower = environment.toLowerCase()
    def config = settings(envLower)
    echo ">>> Rechecking ${envLower.toUpperCase()} health on port ${config.appPort}..."
    verify(envLower, "${env.JOB_NAME}#${env.BUILD_NUMBER}")
}

return this

