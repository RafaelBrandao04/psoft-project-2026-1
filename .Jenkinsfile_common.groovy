// .Jenkinsfile_common.groovy
// Shared deployment and verification module for DEV, UAT, and PROD environments
// DEI Virtual Machine: vs806 (vsgate-ssh.dei.isep.ipp.pt:10806)

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

def getEnvConfig(String targetEnv) {
    def configs = [
        'dev': [
            port: 8081,
            dir: '/opt/lms/dev',
            profile: 'dev,bootstrap'
        ],
        'uat': [
            port: 8082,
            dir: '/opt/lms/uat',
            profile: 'staging'
        ],
        'prod': [
            port: 8080,
            dir: '/opt/lms/prod',
            profile: 'prod'
        ]
    ]

    def config = configs[targetEnv.toLowerCase()]
    if (!config) {
        error "Unknown environment: ${targetEnv}. Supported environments: dev, uat, prod"
    }
    return config
}

def getSshConfig() {
    return [
        host: env.DEI_VM_HOST ?: 'vsgate-ssh.dei.isep.ipp.pt',
        port: env.DEI_VM_PORT ?: '10806',
        user: env.DEI_VM_USER ?: 'root',
        credentialsId: env.DEI_VM_CREDENTIALS_ID ?: 'dei-vm-ssh-key'
    ]
}

def executeRemoteCommand(String cmd) {
    def ssh = getSshConfig()
    withCredentials([sshUserPrivateKey(credentialsId: ssh.credentialsId, keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
        if (isUnix()) {
            def sshOpts = "-i \"${SSH_KEY}\" -p ${ssh.port} -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=10 -o BatchMode=yes"
            sh "ssh ${sshOpts} ${SSH_USER}@${ssh.host} \"${cmd}\""
        } else {
            bat """@echo off
set "WIN_KEY=%SSH_KEY:/=\\%"
icacls "%WIN_KEY%" /inheritance:r /grant:r "*S-1-5-18:(F)" /grant:r "*S-1-5-32-544:(F)" >nul
icacls "%WIN_KEY%" /remove "*S-1-5-32-545" >nul
ssh -i "%WIN_KEY%" -p ${ssh.port} -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=10 -o BatchMode=yes %SSH_USER%@${ssh.host} "${cmd}"
"""
        }
    }
}

def copyToRemote(String localPath, String remotePath) {
    def ssh = getSshConfig()
    withCredentials([sshUserPrivateKey(credentialsId: ssh.credentialsId, keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
        if (isUnix()) {
            def scpOpts = "-i \"${SSH_KEY}\" -P ${ssh.port} -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=10 -o BatchMode=yes"
            sh "scp ${scpOpts} ${localPath} ${SSH_USER}@${ssh.host}:${remotePath}"
        } else {
            def winLocalPath = localPath.replace('/', '\\')
            bat """@echo off
set "WIN_KEY=%SSH_KEY:/=\\%"
icacls "%WIN_KEY%" /inheritance:r /grant:r "*S-1-5-18:(F)" /grant:r "*S-1-5-32-544:(F)" >nul
icacls "%WIN_KEY%" /remove "*S-1-5-32-545" >nul
scp -i "%WIN_KEY%" -P ${ssh.port} -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=10 -o BatchMode=yes "${winLocalPath}" %SSH_USER%@${ssh.host}:${remotePath}
"""
        }
    }
}

def stopService(String targetEnv) {
    def config = getEnvConfig(targetEnv)
    echo "Stopping existing service for ${targetEnv.toUpperCase()} on port ${config.port}..."
    // Use regex bracket [s]erver.port so pkill -f does not match and kill its own parent SSH subshell
    executeRemoteCommand("pkill -f '[s]erver.port=${config.port}' || true")
    // Short wait to release socket
    executeRemoteCommand("sleep 2")
}

def startService(String targetEnv) {
    def config = getEnvConfig(targetEnv)
    echo "Starting service for ${targetEnv.toUpperCase()} on port ${config.port}..."
    executeRemoteCommand("mkdir -p ${config.dir}")
    def runCmd = "nohup java -jar ${config.dir}/app.jar --server.port=${config.port} --spring.profiles.active=${config.profile} > ${config.dir}/app.log 2>&1 < /dev/null &"
    executeRemoteCommand(runCmd)
    executeRemoteCommand("sleep 3")
}

def smokeTest(String targetEnv) {
    def config = getEnvConfig(targetEnv)
    echo "Executing smoke test / readiness verification for ${targetEnv.toUpperCase()} on port ${config.port}..."
    
    // Poll endpoints on the VM with retry loop (20 retries with 3s sleep = 60s max wait)
    // Checks OpenAPI docs (/api-docs) or Actuator health (/actuator/health)
    def testCmd = "for i in \$(seq 1 20); do if curl -s -f http://localhost:${config.port}/api-docs >/dev/null 2>&1 || curl -s -f http://localhost:${config.port}/actuator/health >/dev/null 2>&1; then echo 'Smoke test PASSED: ${targetEnv.toUpperCase()} service is UP and healthy on port ${config.port}'; exit 0; fi; echo 'Waiting for ${targetEnv.toUpperCase()} service on port ${config.port} (attempt '\$i'/20)...'; sleep 3; done; echo 'Smoke test FAILED: ${targetEnv.toUpperCase()} service did not respond on port ${config.port}'; tail -n 30 ${config.dir}/app.log; exit 1"
    executeRemoteCommand(testCmd)
}

def deploy(String targetEnv) {
    def config = getEnvConfig(targetEnv)
    echo "=========================================================="
    echo ">>> Initiating Deployment to [${targetEnv.toUpperCase()}] on DEI VM vs806"
    echo ">>> Target Port: ${config.port} | Path: ${config.dir}"
    echo "=========================================================="

    // 1. Ensure target directory exists on remote VM
    executeRemoteCommand("mkdir -p ${config.dir}")

    // 2. Stop running process on target port
    stopService(targetEnv)

    // 3. Securely copy fresh application artifact to remote VM
    echo "Transferring artifact/app.jar to remote VM: ${config.dir}/app.jar..."
    copyToRemote("artifact/app.jar", "${config.dir}/app.jar")

    // 4. Start new service instance
    startService(targetEnv)

    // 5. Run smoke tests to guarantee service is healthy
    smokeTest(targetEnv)

    echo ">>> Deployment to [${targetEnv.toUpperCase()}] COMPLETED SUCCESSFULLY!"
}

def recheck(String targetEnv) {
    def config = getEnvConfig(targetEnv)
    echo ">>> Rechecking ${targetEnv.toUpperCase()} service health prior to promotion..."
    smokeTest(targetEnv)
}

return this

