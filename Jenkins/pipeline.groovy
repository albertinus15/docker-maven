import java.text.SimpleDateFormat

def getCommitHashAndDateTime() {
    def commitHash = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
    def date = new Date()
    def formatter = new SimpleDateFormat("yyyy-MM-dd")
    def formattedDate = formatter.format(date)
    return "${commitHash}-${formattedDate}"
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
        }
        
        stage('Build') {
            steps {
                sh 'mvn package -f MyWebApp/pom.xml'
            }
        }

        stage('Publish Artifact') {
            steps {
                withMaven(globalMavenSettingsConfig: 'setting-maven', jdk: '', maven: 'maven3', mavenSettingsConfig: '', traceability: true) {
                    sh 'mvn deploy -f MyWebApp/pom.xml'
                }
            }
        }
        
        stage('Build and Tag Docker Image') {
            steps {
                script {
                    def image_tag = getCommitHashAndDateTime()
                    sh "docker build -t maven-app:${image_tag} -f MyWebApp/Dockerfile ."
                    sh "docker tag maven-app:${image_tag} harbor.ntx-technology.com/my-app/maven-app:${image_tag}"
                }
            }
        }

        stage('Trivy Scan') {
            steps {
                script{
                    def image_tag = getCommitHashAndDateTime()
                    sh "trivy image harbor.ntx-technology.com/my-app/maven-app:${image_tag} > trivy-report.txt"
                    sh "cat trivy-report.txt"
                }
                
            }
        }        

        stage('Push to Docker Registry') {
            steps {
                script {
                    def image_tag = getCommitHashAndDateTime()
                    withDockerRegistry(credentialsId: 'local-registry', toolName: 'docker', url: 'https://harbor.ntx-technology.com') {
                        sh "docker push harbor.ntx-technology.com/my-app/maven-app:${image_tag}"
                    }
                }
            }
        }
    }
}