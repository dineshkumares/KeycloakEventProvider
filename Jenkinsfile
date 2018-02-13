@Library('semantic_releasing')_

node {
    def mvnHome = tool 'M3'
    env.PATH = "${mvnHome}/bin/:${env.PATH}"
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
        sh "mvn clean package"
        junit allowEmptyResults: true, testResults: '**/target/surefire-reports/TEST-*.xml'
    }

    stage('build image & git tag & docker push') {
        env.VERSION = semanticReleasing()
        currentBuild.displayName = env.VERSION

        sh "mvn versions:set -DnewVersion=${env.VERSION}"
        sh "git config user.email \"jenkins@khinkali.ch\""
        sh "git config user.name \"Jenkins\""
        sh "git tag -a ${env.VERSION} -m \"${env.VERSION}\""
        withCredentials([usernamePassword(credentialsId: 'github', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
            sh "git push https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/khinkali/KeycloakEventProvider.git --tags"
        }

        sh "docker build -t khinkali/sink:${env.VERSION} ."
        withCredentials([usernamePassword(credentialsId: 'dockerhub', passwordVariable: 'DOCKER_PASSWORD', usernameVariable: 'DOCKER_USERNAME')]) {
            sh "docker login --username ${DOCKER_USERNAME} --password ${DOCKER_PASSWORD}"
        }
        sh "docker push khinkali/KeycloakEventProvider:${env.VERSION}"
    }

    stage('deploy to test') {
        sh "sed -i -e 's/        image: khinkali\\/keycloak:0.0.1/        image: khinkali\\/keycloak:${env.VERSION}/' startup.yml"
        sh "kubectl --kubeconfig /tmp/admin.conf apply -f startup.yml"
    }

    stage('deploy to prod') {
        input(message: 'manuel user tests ok?' )
        withCredentials([usernamePassword(credentialsId: 'github-api-token', passwordVariable: 'GITHUB_TOKEN', usernameVariable: 'GIT_USERNAME')]) {
            gitHubRelease(env.VERSION, 'khinkali', 'sink', GITHUB_TOKEN)
        }
        sh "sed -i -e 's/  namespace: test/  namespace: default/' startup.yml"
        sh "sed -i -e 's/    nodePort: 31081/    nodePort: 30190/' startup.yml"
        sh "kubectl --kubeconfig /tmp/admin.conf apply -f startup.yml"
    }
}