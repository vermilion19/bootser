pipeline {
    agent any

    parameters {
        choice(
            name: 'DEPLOY_TARGET',
            choices: ['all', 'waiting-service', 'restaurant-service', 'auth-service', 'notification-service', 'promotion-service', 'gateway-service', 'discovery-service'],
            description: '배포할 서비스를 선택하세요'
        )
        choice(
            name: 'ACTION',
            choices: ['deploy', 'restart', 'stop', 'logs'],
            description: '실행할 작업을 선택하세요'
        )
        booleanParam(
            name: 'REBUILD_IMAGE',
            defaultValue: true,
            description: '이미지를 다시 빌드할지 여부'
        )
    }

    environment {
        COMPOSE_FILE = 'dockers/services/docker-compose.yml'
        PROJECT_DIR = '/workspace/bootser'
    }

    stages {
        stage('Checkout') {
            steps {
                echo "Using workspace: ${PROJECT_DIR}"
                sh "cd ${PROJECT_DIR} && git pull origin main || true"
            }
        }

        stage('Build Gradle') {
            when {
                expression { params.ACTION == 'deploy' && params.REBUILD_IMAGE }
            }
            steps {
                script {
                    if (params.DEPLOY_TARGET == 'all') {
                        sh "cd ${PROJECT_DIR} && ./gradlew clean build -x test"
                    } else {
                        def servicePath = getServicePath(params.DEPLOY_TARGET)
                        sh "cd ${PROJECT_DIR} && ./gradlew :${servicePath}:clean :${servicePath}:build -x test"
                    }
                }
            }
        }

        stage('Build Docker Image') {
            when {
                expression { params.ACTION == 'deploy' && params.REBUILD_IMAGE }
            }
            steps {
                script {
                    dir(PROJECT_DIR) {
                        if (params.DEPLOY_TARGET == 'all') {
                            sh "docker compose -f ${COMPOSE_FILE} build"
                        } else {
                            sh "docker compose -f ${COMPOSE_FILE} build ${params.DEPLOY_TARGET}"
                        }
                    }
                }
            }
        }

        stage('Deploy') {
            when {
                expression { params.ACTION == 'deploy' }
            }
            steps {
                script {
                    dir(PROJECT_DIR) {
                        if (params.DEPLOY_TARGET == 'all') {
                            sh "docker compose -f ${COMPOSE_FILE} up -d"
                        } else {
                            sh "docker compose -f ${COMPOSE_FILE} up -d ${params.DEPLOY_TARGET}"
                        }
                    }
                }
            }
        }

        stage('Restart') {
            when {
                expression { params.ACTION == 'restart' }
            }
            steps {
                script {
                    dir(PROJECT_DIR) {
                        if (params.DEPLOY_TARGET == 'all') {
                            sh "docker compose -f ${COMPOSE_FILE} restart"
                        } else {
                            sh "docker compose -f ${COMPOSE_FILE} restart ${params.DEPLOY_TARGET}"
                        }
                    }
                }
            }
        }

        stage('Stop') {
            when {
                expression { params.ACTION == 'stop' }
            }
            steps {
                script {
                    dir(PROJECT_DIR) {
                        if (params.DEPLOY_TARGET == 'all') {
                            sh "docker compose -f ${COMPOSE_FILE} down"
                        } else {
                            sh "docker compose -f ${COMPOSE_FILE} stop ${params.DEPLOY_TARGET}"
                        }
                    }
                }
            }
        }

        stage('Show Logs') {
            when {
                expression { params.ACTION == 'logs' }
            }
            steps {
                script {
                    dir(PROJECT_DIR) {
                        if (params.DEPLOY_TARGET == 'all') {
                            sh "docker compose -f ${COMPOSE_FILE} logs --tail=100"
                        } else {
                            sh "docker compose -f ${COMPOSE_FILE} logs --tail=100 ${params.DEPLOY_TARGET}"
                        }
                    }
                }
            }
        }

        stage('Health Check') {
            when {
                expression { params.ACTION == 'deploy' || params.ACTION == 'restart' }
            }
            steps {
                script {
                    sleep(time: 30, unit: 'SECONDS')
                    dir(PROJECT_DIR) {
                        sh "docker compose -f ${COMPOSE_FILE} ps"
                    }
                }
            }
        }
    }

    post {
        success {
            echo "Pipeline completed successfully!"
        }
        failure {
            echo "Pipeline failed!"
        }
        always {
            script {
                dir(PROJECT_DIR) {
                    sh "docker compose -f ${COMPOSE_FILE} ps || true"
                }
            }
        }
    }
}

def getServicePath(serviceName) {
    def serviceMap = [
        'waiting-service': 'apps:waiting-service',
        'restaurant-service': 'apps:restaurant-service',
        'auth-service': 'apps:auth-service',
        'notification-service': 'apps:notification-service',
        'promotion-service': 'apps:promotion-service',
        'gateway-service': 'infrastructure:gateway-service',
        'discovery-service': 'infrastructure:discovery-service'
    ]
    return serviceMap[serviceName] ?: serviceName
}
