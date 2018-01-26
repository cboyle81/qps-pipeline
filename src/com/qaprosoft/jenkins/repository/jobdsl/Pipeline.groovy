package com.qaprosoft.jenkins.repository.jobdsl

@Grab('org.testng:testng:6.3.1')

import groovy.io.FileType;
import org.testng.xml.Parser;
import org.testng.xml.XmlSuite;

createPipelines()

void createPipelines() {
    def list = []
    def listPipelines = []

    def currentBuild = Thread.currentThread().executable
    def workspace = currentBuild.getEnvVars()["WORKSPACE"]
    println "JENKINS_HOME: ${JENKINS_HOME}"
    println "WORKSPACE: ${WORKSPACE}"

    def dir = new File(workspace, "auto-apis/tests/src/test/resources/testng-suites")
    dir.eachFileRecurse (FileType.FILES) { file ->
        list << file
    }

    def jobFolder = "Pipeline"
    folder(jobFolder) {
        displayName(jobFolder)
    }

    list.each {
        def currentSuiteItem = it
        if (currentSuiteItem.name.endsWith('.xml')) {
            def xmlFile = new Parser(new File(currentSuiteItem.path).absolutePath)
            xmlFile.setLoadClasses(false)

            List<XmlSuite> suiteXml = xmlFile.parseToList()
            XmlSuite currentSuite = suiteXml.get(0)

            if (currentSuite.toXml().contains("jenkinsRegressionPipeline")) {
                String suiteName = currentSuiteItem.path
                String suiteFrontRemoval = "testng-suites/"
                suiteName = suiteName.substring(suiteName.lastIndexOf(suiteFrontRemoval) + suiteFrontRemoval.length(), suiteName.indexOf(".xml"))

                println "\n" + currentSuiteItem.path
                println "\n" + suiteName
                println "\n" + currentSuite.toXml() + "\n"
                println "jenkinsRegressionPipeline: " + currentSuite.getParameter("jenkinsRegressionPipeline").toString()
                if (currentSuite.getParameter("jenkinsRegressionPipeline").length() > 0) {
                    scanPipelines(currentSuite, listPipelines)
                }
            }
        }
    }
    println "\n"
    println "Pipeline List: " + listPipelines

    buildPipeline(jobFolder, listPipelines)
}

def scanPipelines(XmlSuite currentSuite, List listPipelines) {
    def pipelineNames = currentSuite.getParameter("jenkinsRegressionPipeline").toString().split(",")

    for (String pipelineName : pipelineNames) {
        scanEnvironments(currentSuite, pipelineName, listPipelines)
    }
}

def scanEnvironments(XmlSuite currentSuite, String pipelineName, List listPipelines) {
    def jobEnvironments = currentSuite.getParameter("jenkinsEnvironments").toString().split(",")
    for (String jobEnvironment : jobEnvironments) {
        def pipelineMap = [:]

        pipelineMap.put("name", pipelineName)
        pipelineMap.put("jobName", currentSuite.getParameter("jenkinsJobName").toString())
        pipelineMap.put("environment", jobEnvironment)
        pipelineMap.put("overrideFields", currentSuite.getParameter("overrideFields").toString())
        pipelineMap.put("scheduling", currentSuite.getParameter("jenkinsPipelineScheduling").toString())
        println "\n"
        println "${pipelineMap.values()}"
        listPipelines.add(pipelineMap);
    }
}

def buildPipeline(String jobFolder, List fullPipelineList) {

    while (fullPipelineList.size() > 0) {
        def grabbedPipeline = fullPipelineList.findAll { it.name == fullPipelineList.first().name.toString() }
        println "\n"
        println "grabbedPipeline: ${grabbedPipeline}"
        
        fullPipelineList.removeAll { it.name.toString().equalsIgnoreCase(grabbedPipeline.first().name.toString())}

        settingUpPipeline(jobFolder, grabbedPipeline)
    }
}

def settingUpPipeline(String jobFolder, List pipelineList) {
    println "\n"
    println "Creating Regression Pipeline: " + pipelineList.first().name

    def customFields = []
    def scheduling = ""

    for (Map pipelineItem : pipelineList) {
        println "\n"
        println "Pipeline Item: " + pipelineItem

        if (!pipelineItem.get("overrideFields").toString().contains("null")) {
            scanForCustomFields(pipelineItem, customFields)
        }

        if (scheduling.length() == 0 && !pipelineItem.get("scheduling").toString().contains("null") ) {
            scheduling = pipelineItem.get("scheduling").toString()
        }
    }

    Job.createRegressionPipeline(pipelineJob(jobFolder + "/" + pipelineList.first().name), pipelineList.first().name, customFields, scheduling)
}

def scanForCustomFields(Map pipelineItem, List existingCustomFieldsList) {
    def currentCustomFields = pipelineItem.get("overrideFields").toString().split(",")
    for (String customField : currentCustomFields) {
        def customFieldName = customField.split("=")
        if (!scanCurrentCustomList(existingCustomFieldsList, customFieldName[0].trim())) {
            println "Adding Custom Field To List: " + customField.trim()
            existingCustomFieldsList.add(customField.trim());
        }
    }
}

def scanCurrentCustomList(List existingCustomFieldsList, String fieldToSearchFor) {
    for (String field : existingCustomFieldsList) {
        println "Comparing Fields (" + field + ") and (" + fieldToSearchFor + ")"
        if (field.contains(fieldToSearchFor)) {
            return true
        }
    }
    return false
}