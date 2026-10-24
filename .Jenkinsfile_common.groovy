// .Jenkinsfile_common.groovy
// Shared deployment and verification module for DEV, UAT, and PROD environments
// DEI Virtual Machine: vs806 (vsgate-ssh.dei.isep.ipp.pt:10806)

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
        def sshOpts = "-i \"${SSH_KEY}\" -p ${ssh.port} -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=10"
        if (isUnix()) {
            sh "ssh ${sshOpts} ${SSH_USER}@${ssh.host} \"${cmd}\""
        } else {
            bat "ssh ${sshOpts} ${SSH_USER}@${ssh.host} \"${cmd}\""
        }
    }
}

def copyToRemote(String localPath, String remotePath) {
    def ssh = getSshConfig()
    withCredentials([sshUserPrivateKey(credentialsId: ssh.credentialsId, keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
        def scpOpts = "-i \"${SSH_KEY}\" -P ${ssh.port} -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=10"
        if (isUnix()) {
            sh "scp ${scpOpts} ${localPath} ${SSH_USER}@${ssh.host}:${remotePath}"
        } else {
            bat "scp ${scpOpts} ${localPath} ${SSH_USER}@${ssh.host}:${remotePath}"
        }
    }
}

def stopService(String targetEnv) {
    def config = getEnvConfig(targetEnv)
    echo "Stopping existing service for ${targetEnv.toUpperCase()} on port ${config.port}..."
    // Gracefully stop or kill process using the port, without failing if no process is running
    executeRemoteCommand("fuser -k ${config.port}/tcp || pkill -f 'server.port=${config.port}' || true")
    // Short wait to release socket
    executeRemoteCommand("sleep 2")
}

def startService(String targetEnv) {
    def config = getEnvConfig(targetEnv)
    echo "Starting service for ${targetEnv.toUpperCase()} on port ${config.port}..."
    executeRemoteCommand("mkdir -p ${config.dir}")
    def runCmd = "nohup java -jar ${config.dir}/app.jar --server.port=${config.port} --spring.profiles.active=${config.profile} > ${config.dir}/app.log 2>&1 &"
    executeRemoteCommand(runCmd)
    executeRemoteCommand("sleep 3")
}

def smokeTest(String targetEnv) {
    def config = getEnvConfig(targetEnv)
    echo "Executing smoke test / readiness verification for ${targetEnv.toUpperCase()} on port ${config.port}..."
    
    // Poll actuator health or books endpoint on the VM with retry loop
    def testCmd = """
        MAX_RETRIES=15
        COUNT=0
        until curl -s -f http://localhost:${config.port}/actuator/health || [ \$COUNT -eq \$MAX_RETRIES ]; do
            echo "Waiting for ${targetEnv.toUpperCase()} service on port ${config.port} (attempt \$((COUNT+1))/\$MAX_RETRIES)..."
            sleep 3
            COUNT=\$((COUNT+1))
        done
        if [ \$COUNT -eq \$MAX_RETRIES ]; then
            echo "Smoke test FAILED: ${targetEnv.toUpperCase()} service did not respond with HTTP 200 on port ${config.port}."
            tail -n 30 ${config.dir}/app.log || true
            exit 1
        fi
        echo "Smoke test PASSED: ${targetEnv.toUpperCase()} service is UP and healthy on port ${config.port}!"
    """
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

