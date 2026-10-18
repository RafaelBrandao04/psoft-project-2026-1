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
                    if (isUnix()) {
                        sh 'mvn clean compile -B'
                    } else {
                        bat 'mvn clean compile -B'
                    }
                }
            }
        }

        stage('Static Code Analysis (SAST)') {
            steps {
                echo 'Executing SpotBugs static code analysis for defect and vulnerability detection...'
                script {
                    if (isUnix()) {
                        sh 'mvn spotbugs:spotbugs -B'
                    } else {
                        bat 'mvn spotbugs:spotbugs -B'
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'target/spotbugsXml.xml', allowEmptyArchive: true, fingerprint: false
                }
            }
        }

        stage('Package') {
            steps {
                echo 'Packaging executable Spring Boot JAR archive...'
                script {
                    if (isUnix()) {
                        sh 'mvn package -DskipTests -B'
                    } else {
                        bat 'mvn package -DskipTests -B'
                    }
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
            echo "Pipeline SUCCESS: Executable artifact target/*.jar ready for deployment."
        }
        unstable {
            echo "Pipeline UNSTABLE: Quality gate warnings detected."
        }
        failure {
            echo "Pipeline FAILURE: Build or static analysis failed."
        }
    }
}