package com.qaprosoft.jenkins.pipeline.integration.zafira

import com.qaprosoft.jenkins.Logger
import com.qaprosoft.jenkins.pipeline.Configuration
import static com.qaprosoft.jenkins.Utils.*
import static com.qaprosoft.jenkins.pipeline.Executor.*

class ZafiraUpdater {

    private def context
    private ZafiraClient zc
    private Logger logger
    private def testRun

    public ZafiraUpdater(context) {
        this.context = context
        zc = new ZafiraClient(context)
        logger = new Logger(context)
    }

    /**
     * Unable to make a calls for this method at intermeddiate state without refactoring
     * we keep TestRun result using single call for now at the end only.
     * **/
    protected def getTestRun(uuid) {
        def run = testRun
        if(isParamEmpty(testRun)) {
            run = zc.getTestRunByCiRunId(uuid)
            if (isParamEmpty(run)) {
                logger.error("TestRun is not found in Zafira!")
            }
        }
        return run
    }

    public def queueZafiraTestRun(uuid) {
        if(isParamEmpty(Configuration.get("queue_registration")) || Configuration.get("queue_registration").toBoolean()) {
            if(isParamEmpty(Configuration.get('test_run_rules'))){
                def response = zc.queueZafiraTestRun(uuid)
                logger.info("Queued TestRun: " + formatJson(response))
            }
         }
    }

    public def smartRerun() {
        def response = zc.smartRerun()
        logger.info("Results : " + response.size())
    }

    public def abortTestRun(uuid, currentBuild) {
        currentBuild.result = BuildResult.FAILURE
        def failureReason = "undefined failure"

        String buildNumber = Configuration.get(Configuration.Parameter.BUILD_NUMBER)
        String jobBuildUrl = Configuration.get(Configuration.Parameter.JOB_URL) + buildNumber
        String jobName = Configuration.get(Configuration.Parameter.JOB_NAME)
        String env = Configuration.get("env")

        def bodyHeader = "Unable to execute tests due to the unrecognized failure: ${jobBuildUrl}\n"
        def subject = getFailureSubject(FailureCause.UNRECOGNIZED_FAILURE.value, jobName, env, buildNumber)
        def failureLog = ""

        if (currentBuild.rawBuild.log.contains("COMPILATION ERROR : ")) {
            bodyHeader = "Unable to execute tests due to the compilation failure. ${jobBuildUrl}\n"
            subject = getFailureSubject(FailureCause.COMPILATION_FAILURE.value, jobName, env, buildNumber)
            failureLog = getLogDetailsForEmail(currentBuild, "ERROR")
            failureReason = URLEncoder.encode("${FailureCause.COMPILATION_FAILURE.value}:\n" + failureLog, "UTF-8")
        } else  if (currentBuild.rawBuild.log.contains("Cancelling nested steps due to timeout")) {
            currentBuild.result = BuildResult.ABORTED
            bodyHeader = "Unable to continue tests due to the abort by timeout ${jobBuildUrl}\n"
            subject = getFailureSubject(FailureCause.TIMED_OUT.value, jobName, env, buildNumber)
            failureReason = "Aborted by timeout"
        } else  if (currentBuild.rawBuild.log.contains("Aborted by ")) {
            currentBuild.result = BuildResult.ABORTED
            bodyHeader = "Unable to continue tests due to the abort by " + getAbortCause(currentBuild) + " ${jobBuildUrl}\n"
            subject = getFailureSubject(FailureCause.ABORTED.value, jobName, env, buildNumber)
            failureReason = "Aborted by " + getAbortCause(currentBuild)
        } else if (currentBuild.rawBuild.log.contains("BUILD FAILURE")) {
            bodyHeader = "Unable to execute tests due to the build failure. ${jobBuildUrl}\n"
            subject = getFailureSubject(FailureCause.BUILD_FAILURE.value, jobName, env, buildNumber)
            failureLog = getLogDetailsForEmail(currentBuild, "ERROR")
            failureReason = URLEncoder.encode("${FailureCause.BUILD_FAILURE.value}:\n" + failureLog, "UTF-8")
        }
        def response = zc.abortTestRun(uuid, failureReason)
        if(!isParamEmpty(response)){
            if(response.status.equals(StatusMapper.ZafiraStatus.ABORTED.name())){
                sendFailureEmail(uuid, Configuration.get(Configuration.Parameter.ADMIN_EMAILS))
            } else {
                sendFailureEmail(uuid, Configuration.get("email_list"))
            }
        } else {
            logger.error("Unable to abort testrun! Probably run is not registered in Zafira.")
            //Explicitly send email via Jenkins (emailext) as nothing is registered in Zafira
            def body = "${bodyHeader}\nRebuild: ${jobBuildUrl}/rebuild/parameterized\nZafiraReport: ${jobBuildUrl}/ZafiraReport\n\nConsole: ${jobBuildUrl}/console\n${failureLog}"
            context.emailext getEmailParams(body, subject, Configuration.get(Configuration.Parameter.ADMIN_EMAILS))
        }
    }

    public def sendZafiraEmail(uuid, emailList) {
        def testRun = getTestRun(uuid)
        if (!isParamEmpty(emailList)) {
            zc.sendEmail(uuid, emailList, "all")
        }
        String failureEmailList = Configuration.get("failure_email_list")
        if (isFailure(testRun.status) && !isParamEmpty(failureEmailList)) {
            zc.sendEmail(uuid, failureEmailList, "failures")
        }
    }

    public void exportZafiraReport(uuid, workspace) {
        String zafiraReport = zc.exportZafiraReport(uuid)
        if(isParamEmpty(zafiraReport)){
            logger.error("UNABLE TO GET TESTRUN! Probably it is not registered in Zafira.")
            return
        }
        logger.debug(zafiraReport)
        context.writeFile file: "${workspace}/zafira/report.html", text: zafiraReport
     }

    public def sendFailureEmail(uuid, emailList) {
        def suiteOwner = true
        def suiteRunner = false
        if(Configuration.get("suiteOwner")){
            suiteOwner = Configuration.get("suiteOwner")
        }
        if(Configuration.get("suiteRunner")){
            suiteRunner = Configuration.get("suiteRunner")
        }
        return zc.sendFailureEmail(uuid, emailList, suiteOwner, suiteRunner)
    }

    public def setBuildResult(uuid, currentBuild) {
        def testRun = getTestRun(uuid)
        if(!isParamEmpty(testRun) && isFailure(testRun.status)){
            currentBuild.result = BuildResult.FAILURE
        }
    }

    public def sendSlackNotification(uuid, channels) {
        if (!isParamEmpty(channels)){
            return zc.sendSlackNotification(uuid, channels)
        }
    }

    public boolean isZafiraRerun(uuid){
        return !isParamEmpty(zc.getTestRunByCiRunId(uuid))
    }

    public def createLauncher(jobParameters, jobUrl, repo) {
        return zc.createLauncher(jobParameters, jobUrl, repo)
    }

    public def createJob(jobUrl){
        return zc.createJob(jobUrl)
    }

    public def registerTokenInZafira(userName, tokenValue){
        def jenkinsSettingsList = zc.getJenkinsSettings()
        jenkinsSettingsList.each {
            switch (it.name) {
                case "JENKINS_USER" :
                    it.value = userName
                    break
                case "JENKINS_API_TOKEN_OR_PASSWORD":
                    it.value = tokenValue
                    break
            }
        }
        zc.updateJenkinsConfig(jenkinsSettingsList)
    }
}
