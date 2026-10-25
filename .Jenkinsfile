// .Jenkinsfile
// Pipeline for DEV Job (Continuous Integration + Deploy to DEV)
// Triggered on push via Webhook Relay (or GitHub / Bitbucket Webhook)

pipeline {
    agent any

    options {
        // Grant permission to downstream UAT jobs to copy archived artifacts
        copyArtifactPermission('lms-uat,lms-private-uat,LMS-private-uat,LMS-uat,*')
        disableConcurrentBuilds()
    }

    environment {
        JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8 -Djava.awt.headless=true'
        UAT_JOB_NAME = 'lms-uat'
    }

    stages {
        stage('Build') {
            steps {
                echo '>>> Stage 1: Build, Unit Tests, PIT Mutation Testing, Package & Integration Tests'
                timeout(time: 15, unit: 'MINUTES') {
                    script {
                        def commitArg = env.GIT_COMMIT ? "-Drelease.commit=${env.GIT_COMMIT}" : ""
                        if (isUnix()) {
                            sh "./mvnw -B -Pmutation clean verify -Dpitest.mutationThreshold=30 ${commitArg}"
                        } else {
                            bat "mvnw.cmd -B -Pmutation clean verify -Dpitest.mutationThreshold=30 ${commitArg}"
                        }
                    }
                }
            }
            post {
                always {
                    echo '>>> Archiving Test, Mutation & Quality Reports as Evidence...'
                    archiveArtifacts artifacts: 'target/pit-reports/**', allowEmptyArchive: true
                    junit testResults: 'target/surefire-reports/TEST-*.xml, target/failsafe-reports/TEST-*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Deploy DEV') {
            when {
                expression { env.GIT_BRANCH in ['origin/main', 'main'] || env.BRANCH_NAME == 'main' }
            }
            steps {
                echo '>>> Stage 2: Packaging and Deploying to DEV Environment'
                script {
                    // 1. Clean and prepare local artifact directory
                    dir('artifact') {
                        deleteDir()
                    }

                    // 2. Locate built JAR and copy/standardize as artifact/app.jar (binary mode /B to prevent EOF truncation on Windows)
                    if (isUnix()) {
                        sh '''
                            mkdir -p artifact
                            cp target/psoft-g1-*.jar artifact/app.jar
                        '''
                    } else {
                        bat '''
                            if not exist artifact mkdir artifact
                            copy /B /Y target\\psoft-g1-*.jar artifact\\app.jar
                        '''
                    }

                    // 3. Generate release.properties metadata file for traceability
                    def commitSha = env.GIT_COMMIT ?: 'UNKNOWN_COMMIT'
                    def propertiesContent = "job=${env.JOB_NAME}\nCI_JOB=${env.JOB_NAME}\nbuild=${env.BUILD_NUMBER}\nCI_BUILD=${env.BUILD_NUMBER}\ncommit=${commitSha}\nGIT_COMMIT=${commitSha}\n"
                    writeFile file: 'artifact/release.properties', text: propertiesContent, encoding: 'UTF-8'
                    echo "Created artifact/release.properties:\n${propertiesContent}"

                    // 4. Load deploy script and deploy to DEV on DEI VM vs806
                    def deploy = load 'deploy/jenkins-deploy.groovy'
                    deploy.call('dev')

                    // 5. Prepare validated shared folder for downstream UAT job
                    dir('validated') {
                        deleteDir()
                    }
                    if (isUnix()) {
                        sh '''
                            mkdir -p validated
                            cp artifact/app.jar validated/app.jar
                            cp artifact/release.properties validated/release.properties
                        '''
                    } else {
                        bat '''
                            if not exist validated mkdir validated
                            copy /B /Y artifact\\app.jar validated\\app.jar
                            copy /Y artifact\\release.properties validated\\release.properties
                        '''
                    }

                    // 6. Archive validated artifacts with fingerprinting
                    archiveArtifacts artifacts: 'validated/**', fingerprint: true, allowEmptyArchive: false

                    // 7. Trigger the downstream UAT Job asynchronously
                    echo ">>> Triggering UAT Job [${UAT_JOB_NAME}] with CI_BUILD=${env.BUILD_NUMBER} and CI_JOB=${env.JOB_NAME}..."
                    try {
                        build job: "${UAT_JOB_NAME}",
                              parameters: [
                                  string(name: 'CI_BUILD', value: "${env.BUILD_NUMBER}"),
                                  string(name: 'CI_JOB', value: "${env.JOB_NAME}")
                              ],
                              wait: false
                    } catch (Exception e) {
                        echo "WARNING: Could not trigger downstream job '${UAT_JOB_NAME}': ${e.message}."
                        echo "Ensure a Pipeline job named '${UAT_JOB_NAME}' exists and is parameterized with CI_BUILD and CI_JOB."
                    }
                }
            }
        }
    }

    post {
        always {
            echo "CI Pipeline finished with status: ${currentBuild.currentResult}"
        }
        success {
            echo "Pipeline SUCCESS: Build passed quality gates and deployed to DEV."
        }
        failure {
            echo "Pipeline FAILURE: Build or deployment failed."
        }
    }
}

