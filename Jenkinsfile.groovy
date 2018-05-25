@Library('semantic_releasing') _

podTemplate(label: 'mypod', containers: [
        containerTemplate(name: 'docker', image: 'docker', ttyEnabled: true, command: 'cat'),
        containerTemplate(name: 'kubectl', image: 'lachlanevenson/k8s-kubectl:v1.8.0', command: 'cat', ttyEnabled: true),
        containerTemplate(name: 'curl', image: 'khinkali/jenkinstemplate:0.0.3', command: 'cat', ttyEnabled: true),
        containerTemplate(name: 'maven', image: 'maven:3.5.2-jdk-8', command: 'cat', ttyEnabled: true)
],
        volumes: [
                hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock'),
        ]) {
    node('mypod') {
        properties([
                buildDiscarder(
                        logRotator(artifactDaysToKeepStr: '',
                                artifactNumToKeepStr: '',
                                daysToKeepStr: '',
                                numToKeepStr: '30'
                        )
                ),
                pipelineTriggers([])
        ])

        stage('checkout & unit tests & build') {
            git url: "https://github.com/khinkali/KeycloakEventProvider"
            withCredentials([usernamePassword(credentialsId: 'nexus', passwordVariable: 'NEXUS_PASSWORD', usernameVariable: 'NEXUS_USERNAME')]) {
                container('maven') {
                    sh 'mvn -s settings.xml clean package'
                }
            }
            junit allowEmptyResults: true, testResults: '**/target/surefire-reports/TEST-*.xml'
        }

        stage('build image & git tag & docker push') {
            env.VERSION = semanticReleasing()
            currentBuild.displayName = env.VERSION

            container('maven') {
                sh "mvn versions:set -DnewVersion=${env.VERSION}"
            }
            sh "git config user.email \"jenkins@khinkali.ch\""
            sh "git config user.name \"Jenkins\""
            sh "git tag -a ${env.VERSION} -m \"${env.VERSION}\""
            withCredentials([usernamePassword(credentialsId: 'github', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
                sh "git push https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/khinkali/KeycloakEventProvider.git --tags"
            }

            container('docker') {
                sh "docker build -t khinkali/keycloak:${env.VERSION} ."
                withCredentials([usernamePassword(credentialsId: 'dockerhub', passwordVariable: 'DOCKER_PASSWORD', usernameVariable: 'DOCKER_USERNAME')]) {
                    sh "docker login --username ${DOCKER_USERNAME} --password ${DOCKER_PASSWORD}"
                }
                sh "docker push khinkali/keycloak:${env.VERSION}"
            }
        }

        stage('deploy to test') {
            sh "sed -i -e 's/        image: khinkali\\/keycloak:todo/        image: khinkali\\/keycloak:${env.VERSION}/' startup.yml"
            container('kubectl') {
                sh "kubectl apply -f startup.yml"
            }
        }

        stage('deploy to prod') {
            input(message: 'manuel user tests ok?')
        }

        stage('deploy to prod') {
            withCredentials([usernamePassword(credentialsId: 'github-api-token', passwordVariable: 'GITHUB_TOKEN', usernameVariable: 'GIT_USERNAME')]) {
                container('curl') {
                    gitHubRelease(env.VERSION, 'khinkali', 'KeycloakEventProvider', GITHUB_TOKEN)
                }
            }
            sh "sed -i -e 's/  namespace: test/  namespace: default/' startup.yml"
            sh "sed -i -e 's/    nodePort: 31190/    nodePort: 30190/' startup.yml"
            container('kubectl') {
                sh "kubectl apply -f startup.yml"
            }
        }
    }
}