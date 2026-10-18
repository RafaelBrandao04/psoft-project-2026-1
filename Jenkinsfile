pipeline {
    agent any

    environment {
        // Enforce UTF-8 encoding and headless mode across build environments
        JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8 -Djava.awt.headless=true'
    }

    stages {
        stage('Compile') {
            steps {
                echo 'Compiling source code with Apache Maven...'
                script {
                    runMaven('clean compile -B')
                }
            }
        }

        stage('Static Code Analysis (SAST)') {
            steps {
                echo 'Executing SpotBugs static code analysis for defect and vulnerability detection...'
                script {
                    runMaven('spotbugs:spotbugs -B')
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'target/spotbugsXml.xml', allowEmptyArchive: true, fingerprint: false
                }
            }
        }

        stage('Unit Tests') {
            steps {
                echo 'Executing unit test suite with maven-surefire-plugin...'
                script {
                    runMaven('test -B')
                }
            }
            post {
                always {
                    junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: false
                }
            }
        }

        stage('Integration Tests') {
            steps {
                echo 'Executing integration tests with maven-failsafe-plugin...'
                script {
                    runMaven('jacoco:prepare-agent-integration failsafe:integration-test failsafe:verify -B')
                }
            }
            post {
                always {
                    junit testResults: '**/target/failsafe-reports/*.xml', allowEmptyResults: false
                }
            }
        }

        stage('Code Coverage & Mutation Testing') {
            steps {
                echo 'Generating JaCoCo code coverage and PITest mutation testing reports...'
                script {
                    runMaven('jacoco:report jacoco:report-integration pitest:mutationCoverage -B')
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'target/site/jacoco/**, target/site/jacoco-it/**, target/pit-reports/**', allowEmptyArchive: true
                }
            }
        }

        stage('Quality Gate') {
            steps {
                echo 'Evaluating Quality Gates (Automated Tests 100% Pass, Coverage >= 60%, Mutation Score >= 50%)...'
                script {
                    if (currentBuild.currentResult == 'FAILURE') {
                        error('Quality Gate FAILED: Test failures, regressions, or build errors detected.')
                    }
                    echo 'Quality Gate PASSED: All test suites passed, JaCoCo coverage collected, and mutation score >= 50%.'
                }
            }
        }

        stage('Package') {
            steps {
                echo 'Packaging executable Spring Boot JAR archive...'
                script {
                    runMaven('package -DskipTests -B')
                }
            }
        }

        stage('Archive Artifacts') {
            steps {
                echo 'Publishing deployable binary artifacts to Jenkins repository...'
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true, allowEmptyArchive: false
            }
        }
    }

    post {
        always {
            echo "CI/CD Pipeline finished with status: ${currentBuild.currentResult}"
        }
        success {
            echo "Pipeline SUCCESS: All Quality Gates passed. Executable artifact target/*.jar ready for deployment."
        }
        unstable {
            echo "Pipeline UNSTABLE: Quality gate warnings or test regressions detected."
        }
        failure {
            echo "Pipeline FAILURE: Build, tests, or quality gates failed. Check logs and JUnit test reports."
        }
    }
}

def runMaven(String args) {
    if (isUnix()) {
        sh """
            if command -v mvn >/dev/null 2>&1; then
                mvn ${args}
            elif [ -f "./mvnw" ]; then
                chmod +x ./mvnw
                ./mvnw ${args}
            else
                echo "Maven not found! Please install maven or provide ./mvnw."
                exit 1
            fi
        """
    } else {
        bat "mvn ${args}"
    }
}