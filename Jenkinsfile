//"Jenkins Pipeline is a suite of plugins which supports implementing and integrating continuous delivery pipelines into Jenkins. Pipeline provides an extensible set of tools for modeling delivery pipelines "as code" via the Pipeline DSL."
//More information can be found on the Jenkins Documentation page https://jenkins.io/doc/
library 'github-utils-shared-library@master'

pipeline {
    agent { label 'linux-large' }
    options {
        buildDiscarder(logRotator(numToKeepStr: '5'))
        disableConcurrentBuilds()
        timestamps()
    }
    triggers {
        /*
          Restrict nightly builds to lts-1704 branch, all others will be built on change only.
          Note: The BRANCH_NAME will only work with a multi-branch job using the github-branch-source
        */
        cron(BRANCH_NAME == "lts-1704" ? "H H(6-8) * * *" : "")
    }
    environment {
        LARGE_MVN_OPTS = '-Xmx8192M -Xss128M '
        DISABLE_DOWNLOAD_PROGRESS_OPTS = '-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn '
        LINUX_MVN_RANDOM = '-Djava.security.egd=file:/dev/./urandom'
        GITHUB_USERNAME = 'connexta'
        GITHUB_REPONAME = 'ddf-lts'
    }
    stages {
        stage('Setup') {
            steps {
                retry(3) {
                    checkout scm
                }
                slackSend color: 'good', message: "STARTED: ${JOB_NAME} ${BUILD_NUMBER} ${BUILD_URL}"
                withCredentials([usernameColonPassword(credentialsId: 'cxbot', variable: 'GITHUB_TOKEN')]) {
                    postCommentIfPR("Internal build has been started. Your results will be available at completion. See build progress in [legacy Jenkins UI](${BUILD_URL}) or in [Blue Ocean UI](${BUILD_URL}display/redirect).", "${GITHUB_USERNAME}", "${GITHUB_REPONAME}", "${GITHUB_TOKEN}")
                    script {
                        //Check if the current HEAD is a Merge commit https://stackoverflow.com/questions/3824050/telling-if-a-git-commit-is-a-merge-revert-commit/3824122#3824122
                        try  {
                            sh(script: 'if [ `git cat-file -p HEAD | head -n 3 | grep parent | wc -l` -gt 1 ]; then exit 1; else exit 0; fi')
                            //No error was thrown -> we called exit 0 -> HEAD is not a merge commit/doesn't have multiple parents
                            env.PR_COMMIT = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
                        } catch (err) {
                            //An error was thrown -> we called exit 1 -> HEAD is a merge commit/has multiple parents
                            env.PR_COMMIT = sh(returnStdout: true, script: 'git rev-parse HEAD~1').trim()
                        }
                        //Clear existing status checks
                        def jsonBlob = getGithubStatusJsonBlob("pending", "${BUILD_URL}display/redirect", "Full Build In Progress...", "CX Jenkins/Full Build")
                        postStatusToHash("${jsonBlob}", "${GITHUB_USERNAME}", "${GITHUB_REPONAME}", "${env.PR_COMMIT}", "${GITHUB_TOKEN}")
                    }
                }
                //Replace URLs in yarn.lock files with respective references cached in nexus3. See https://trello.com/c/FzHhuDoJ
                //Note: this is only affecting the primary linux-large agent. Not the linux-small agent that's initialized in the NSP stage in parallel with OWASP
                sh "${WORKSPACE}/replace-yarn-lock-urls.sh"
            }
        }
        // For the time being, the full build will be run against all branches, both PR and non-PR
        stage('Full Build') {
            //Uncomment this if enabling incremental builds
            //when { expression { env.CHANGE_ID == null } }
            steps {
                timeout(time: 3, unit: 'HOURS') {
                    withMaven(maven: 'M35', globalMavenSettingsConfig: 'lte-global-maven-settings', mavenOpts: '${LARGE_MVN_OPTS} ${LINUX_MVN_RANDOM}', options: [artifactsPublisher(disabled: true)]) {
                        sh 'mvn clean install -e $DISABLE_DOWNLOAD_PROGRESS_OPTS -DkeepRuntimeFolder=true'
                    }
                }
            }
            //On a failure, archive artifacts that may be useful in debugging the failure
            post {
                success {
                    withCredentials([usernameColonPassword(credentialsId: 'cxbot', variable: 'GITHUB_TOKEN')]) {
                        script {
                            def jsonBlob = getGithubStatusJsonBlob("success", "${BUILD_URL}display/redirect", "Full Build Succeeded!", "CX Jenkins/Full Build")
                            postStatusToHash("${jsonBlob}", "${GITHUB_USERNAME}", "${GITHUB_REPONAME}", "${env.PR_COMMIT}", "${GITHUB_TOKEN}")
                        }
                    }
                }
                failure {
                    catchError{ junit '**/target/surefire-reports/*.xml' }
                    catchError{ junit '**/target/failsafe-reports/*.xml' }
                    catchError{ zip zipFile: 'PaxExamRuntimeFolder.zip', archive: true, glob: '**/target/exam/**/*' }
                    withCredentials([usernameColonPassword(credentialsId: 'cxbot', variable: 'GITHUB_TOKEN')]) {
                        script {
                            def jsonBlob = getGithubStatusJsonBlob("failure", "${BUILD_URL}display/redirect", "Full Build Failed!", "CX Jenkins/Full Build")
                            postStatusToHash("${jsonBlob}", "${GITHUB_USERNAME}", "${GITHUB_REPONAME}", "${env.PR_COMMIT}", "${GITHUB_TOKEN}")
                        }
                    }
                }
            }
        }
        /*
          Deploy stage will only be executed for deployable branches. These include lts-1704 and any patch branch matching M.m.x format (i.e. 2.10.x, 2.9.x, etc...).
          It will also only deploy in the presence of an environment variable JENKINS_ENV = 'prod'. This can be passed in globally from the jenkins master node settings.
        */
        stage('Deploy') {
            when {
              allOf {
                expression { env.CHANGE_ID == null }
                expression { env.BRANCH_NAME ==~ /((?:\d*\.)?\d.x|lts-1704)/ }
                environment name: 'JENKINS_ENV', value: 'prod'
              }
            }
            steps {
                withMaven(maven: 'M3', jdk: 'jdk8-latest', globalMavenSettingsConfig: 'lte-global-maven-settings', mavenOpts: '${LINUX_MVN_RANDOM}') {
                    sh 'mvn deploy -B -Djacoco.skip=true -DskipStatic=true -DskipTests=true -DretryFailedDeploymentCount=10 $DISABLE_DOWNLOAD_PROGRESS_OPTS'
                }
            }
        }
    }

    post {
        success {
            slackSend color: 'good', message: "SUCCESS: ${JOB_NAME} ${BUILD_NUMBER}"
            withCredentials([usernameColonPassword(credentialsId: 'cxbot', variable: 'GITHUB_TOKEN')]) {
                postCommentIfPR("Build success! See the job results in [legacy Jenkins UI](${BUILD_URL}) or in [Blue Ocean UI](${BUILD_URL}display/redirect).", "${GITHUB_USERNAME}", "${GITHUB_REPONAME}", "${GITHUB_TOKEN}")
            }
        }
        failure {
            slackSend color: '#ea0017', message: "FAILURE: ${JOB_NAME} ${BUILD_NUMBER}. See the results here: ${BUILD_URL}"
            withCredentials([usernameColonPassword(credentialsId: 'cxbot', variable: 'GITHUB_TOKEN')]) {
                postCommentIfPR("Build failure. See the job results in [legacy Jenkins UI](${BUILD_URL}) or in [Blue Ocean UI](${BUILD_URL}display/redirect).", "${GITHUB_USERNAME}", "${GITHUB_REPONAME}", "${GITHUB_TOKEN}")
            }
        }
        unstable {
            slackSend color: '#ffb600', message: "UNSTABLE: ${JOB_NAME} ${BUILD_NUMBER}. See the results here: ${BUILD_URL}"
            withCredentials([usernameColonPassword(credentialsId: 'cxbot', variable: 'GITHUB_TOKEN')]) {
                postCommentIfPR("Build unstable. See the job results in [legacy Jenkins UI](${BUILD_URL}) or in [Blue Ocean UI](${BUILD_URL}display/redirect).", "${GITHUB_USERNAME}", "${GITHUB_REPONAME}", "${GITHUB_TOKEN}")
            }
        }
    }
}
