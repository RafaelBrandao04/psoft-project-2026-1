// .Jenkinsfile_common.groovy
// Bridge module delegating to deploy/jenkins-deploy.groovy

def getModule() {
    return load('deploy/jenkins-deploy.groovy')
}

def parseProperties(String path) {
    return getModule().parseProperties(path)
}

def getEnvConfig(String targetEnv) {
    def cfg = getModule().settings(targetEnv)
    return [
        port: cfg.appPort,
        dir: "/opt/psoft-g1/${targetEnv.toLowerCase()}",
        profile: targetEnv.toLowerCase()
    ]
}

def getSshConfig(String targetEnv = 'dev') {
    return getModule().settings(targetEnv)
}

def deploy(String targetEnv) {
    return getModule().call(targetEnv)
}

def recheck(String targetEnv) {
    return getModule().recheck(targetEnv)
}

def call(String targetEnv) {
    return getModule().call(targetEnv)
}

return this
