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
    
        stage('Sonar Anlysis') {
            steps {
                withSonarQubeEnv('sonar') {
                    sh ''' $SCANNER_HOME/bin/sonar-scanner -Dsonar.projectName=taskmaster -Dsonar.projectKey=taskmaster \
                    -Dsonar.java.binaries=target '''
                }
            }
        }
        
        stage('Build') {
            steps {
                sh 'mvn package -f MyWebApp/pom.xml'
            }
        }        
    }
}
