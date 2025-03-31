import java.text.SimpleDateFormat

def getCommitHashAndDateTime() {
    def commitHash = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
    def date = new Date()
    def formatter = new SimpleDateFormat("yyyyMMdd")
    def formattedDate = formatter.format(date)
    return "${commitHash}${formattedDate}"
}

pipeline {
    agent any
    
    tools {
        maven 'maven3'
    }
    
    environment {
        SCANNER_HOME = tool 'sonar-scanner'
    }

    stages {
        stage('Git Checkout') {
            steps {
                git branch: 'main', url: 'https://github.com/albertinus15/docker-maven.git'
            }
        }
    
        stage('Compile') {
            steps {
                sh 'mvn compile -f MyWebApp/pom.xml'
            }
        }
    
        stage('Unit Test') {
            steps {
                sh 'mvn test -f MyWebApp/pom.xml'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'MyWebApp/target/surefire-reports/*.xml'
                }
            }
        }
    
        stage('Sonar Analysis') {
            steps {
                withSonarQubeEnv('sonar') {
                    sh '''
                        $SCANNER_HOME/bin/sonar-scanner -Dsonar.projectName=taskmaster -Dsonar.projectKey=taskmaster \
                        -Dsonar.java.binaries=target
                    '''
                }
            }
            post {
                always {
                    echo "SonarQube Analysis completed"
                }
            }
        }
        
        stage('Build') {
            steps {
                sh 'mvn package -f MyWebApp/pom.xml'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'MyWebApp/target/*.war', fingerprint: true
                }
            }
        }

        stage('Publish Artifact') {
            steps {
                withMaven(globalMavenSettingsConfig: 'setting-maven', maven: 'maven3', mavenSettingsConfig: '', traceability: true) {
                    sh 'mvn deploy -f MyWebApp/pom.xml'
                }
            }
        }
        
        stage('Build and Tag Docker Image') {
            steps {
                script {
                    def image_tag = getCommitHashAndDateTime()
                    sh "docker build -t myapp_maven:${image_tag} -f MyWebApp/Dockerfile ."
                    sh "docker tag myapp_maven:${image_tag} 156.67.219.189:8082/repository/repo/myapp_maven:${image_tag}"
                }
            }
        }

        stage('Trivy Scan') {
            steps {
                script{
                    def image_tag = getCommitHashAndDateTime()
                    // Menghasilkan output dalam format normal (teks)
                    sh "trivy image 156.67.219.189:8082/repository/repo/myapp_maven:${image_tag} > trivy-report.txt"
                    
                    // Menghasilkan output dalam format JSON untuk diolah menjadi HTML
                    sh "trivy image -f json 156.67.219.189:8082/repository/repo/myapp_maven:${image_tag} > trivy-report.json || true"
                    
                    // Membuat file HTML sederhana dengan hasil JSON
                    sh '''
                        echo '<html><head><title>Trivy Vulnerability Report</title>' > trivy-report.html
                        echo '<style>body{font-family:Arial,sans-serif;margin:20px;} h1{color:#333;} .critical{background-color:#ff5252;} .high{background-color:#ff7e7e;} .medium{background-color:#ffcb2e;} .low{background-color:#ffe082;}</style>' >> trivy-report.html
                        echo '</head><body><h1>Trivy Vulnerability Report</h1><pre>' >> trivy-report.html
                        cat trivy-report.txt >> trivy-report.html
                        echo '</pre></body></html>' >> trivy-report.html
                    '''
                    
                    // Publikasi hasil scan sebagai HTML
                    publishHTML([
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: '.',
                        reportFiles: 'trivy-report.html',
                        reportName: 'Trivy Vulnerability Report'
                    ])
                    
                    echo "Trivy scan completed. See HTML report for details."
                    sh "cat trivy-report.txt"
                }
            }
        }        

        stage('Push to Docker Registry') {
            steps {
                script {
                    def image_tag = getCommitHashAndDateTime()
                    withDockerRegistry(credentialsId: 'nexus', url: 'http://156.67.219.189:8082/repository/repo/') {
                        sh "docker push 156.67.219.189:8082/repository/repo/myapp_maven:${image_tag}"
                    }
                }
            }
            post {
                success {
                    echo "Docker image pushed successfully to repository"
                }
            }
        }
    }
    
    post {
        always {
            echo "Pipeline execution completed"
            // Membersihkan workspace jika diperlukan
            // cleanWs()
        }
        success {
            echo "Pipeline executed successfully"
        }
        failure {
            echo "Pipeline execution failed"
        }
    }
}