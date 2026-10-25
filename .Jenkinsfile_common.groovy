// .Jenkinsfile_common.groovy
// Bridge module delegating to deploy/jenkins-deploy.groovy for full compatibility with professor's reference architecture

def deployModule = load 'deploy/jenkins-deploy.groovy'

def parseProperties(String path) {
    return deployModule.parseProperties(path)
}

def getEnvConfig(String targetEnv) {
    def cfg = deployModule.settings(targetEnv)
    return [
        port: cfg.appPort,
        dir: "/opt/psoft-g1/${targetEnv.toLowerCase()}",
        profile: targetEnv.toLowerCase()
    ]
}

def getSshConfig(String targetEnv = 'dev') {
    return deployModule.settings(targetEnv)
}

def deploy(String targetEnv) {
    return deployModule.call(targetEnv)
}

def recheck(String targetEnv) {
    return deployModule.recheck(targetEnv)
}

def call(String targetEnv) {
    return deployModule.call(targetEnv)
}

return this
