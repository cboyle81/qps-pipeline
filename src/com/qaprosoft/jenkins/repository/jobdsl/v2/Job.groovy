package com.qaprosoft.jenkins.repository.jobdsl.v2

import com.qaprosoft.selenium.grid.ProxyInfo;

class Job {
	protected String runJobPipelineScript = "@Library('QPS-Pipeline')\nimport com.qaprosoft.jenkins.repository.pipeline.v2.Runner;\nnew Runner(this).runJob()"
	protected String runCronPipelineScript = "@Library('QPS-Pipeline')\nimport com.qaprosoft.jenkins.repository.pipeline.v2.Runner;\nnew Runner(this).runCron()"

	protected def context
	protected def selenium
	
	public Job(context) {
		this.context = context
		selenium = context.binding.variables.SELENIUM_PROTOCOL + "://" + context.binding.variables.SELENIUM_HOST + ":" + context.binding.variables.SELENIUM_PORT 
	}
	
	
	public Job(context, runJobScript, runCronScript) {
		this(context)
		this.runJobPipelineScript = runJobScript
		this.runCronPipelineScript = runCronScript
	}

	void createPipeline(pipelineJob, org.testng.xml.XmlSuite currentSuite, String project, String sub_project, String suite, String suiteOwner, String zafira_project) {
		
		pipelineJob.with {
			description("project: ${project}; zafira_project: ${zafira_project}; owner: ${suiteOwner}")
			logRotator { numToKeep 100 }

			authenticationToken('ciStart')
			
			try {
				properties {
					durabilityHint { hint("PERFORMANCE_OPTIMIZED") }
				}
			} catch (Exception e) {
				context.println("Unable to specify performance_optimized mode!")
			}

			def scheduling = currentSuite.getParameter("scheduling")
			if (scheduling != null) {
				triggers { cron(scheduling) }
			}

			context.println("selenium: ${selenium}")
			/** Properties & Parameters Area **/
			parameters {
				choiceParam('env', getEnvironments(currentSuite), 'Environment to test against.')
				
				/** Requires Active Choices Plug-in v1.2+ **/
				/** Currently renders with error: https://issues.jenkins-ci.org/browse/JENKINS-42655 **/
				if (currentSuite.toXml().contains("jenkinsGroups")) {
					activeChoiceParam("groups") {
						description("Please select test group(s) to run")
						filterable()
						choiceType("MULTI_SELECT")
						groovyScript {
							script(this.listToString(currentSuite, "jenkinsGroups"))
							fallbackScript("return ['error']")
						}
					}
				}
				
				booleanParam('fork', false, "Reuse forked repository for ${project} project.")
				booleanParam('debug', false, 'Check to start tests in remote debug mode.')

				def defaultMobilePool = currentSuite.getParameter("jenkinsMobileDefaultPool")
				if (defaultMobilePool == null) {
					defaultMobilePool = "ANY"
				}

				def autoScreenshot = true
				if (currentSuite.getParameter("jenkinsAutoScreenshot") != null) {
					autoScreenshot = currentSuite.getParameter("jenkinsAutoScreenshot").toBoolean()
				}
				
				def keepAllScreenshots = true
				if (currentSuite.getParameter("jenkinsKeepAllScreenshots") != null) {
					keepAllScreenshots = currentSuite.getParameter("jenkinsKeepAllScreenshots").toBoolean()
				}
				
				def enableVNC = true
				if (currentSuite.getParameter("jenkinsEnableVNC") != null) {
					enableVNC = currentSuite.getParameter("jenkinsEnableVNC").toBoolean()
				}
				
				def enableVideo = false
				if (currentSuite.getParameter("jenkinsEnableVideo") != null) {
					enableVideo = currentSuite.getParameter("jenkinsEnableVideo").toBoolean()
				}
				
				def jobType = suite
				if (currentSuite.getParameter("jenkinsJobType") != null) {
					jobType = currentSuite.getParameter("jenkinsJobType")
				}
				context.println("jobType: " + jobType)

				if (!currentSuite.getParameter("useExternalBrowser").toString().contains("null")) {
					concurrentBuild(false)
				}

				switch(jobType.toLowerCase()) {
					case ~/^(?!.*web).*api.*$/:
					// API tests specific
						configure addHiddenParameter("keep_all_screenshots", '', 'false')
						configure addHiddenParameter('platform', '', 'API')
						break;
					case ~/^.*web.*$/:
					case ~/^.*gui.*$/:
					// WEB tests specific
						configure addExtensibleChoice('custom_capabilities', 'gc_CUSTOM_CAPABILITIES', "Set to NULL to run against Selenium Grid on Jenkin's Slave else, select an option for Browserstack.", 'NULL')
						configure addExtensibleChoice('browser', 'gc_BROWSER', 'Select a browser to run tests against.', 'chrome')
						booleanParam('auto_screenshot', autoScreenshot, 'Generate screenshots automatically during the test')
						booleanParam('keep_all_screenshots', keepAllScreenshots, 'Keep screenshots even if the tests pass')
						booleanParam('enableVNC', enableVNC, 'Enable VNC live sessions')
						booleanParam('enableVideo', enableVideo, 'Enable video recording')
						configure addHiddenParameter('platform', '', '*')
						break;
					case ~/^.*android.*$/:
						choiceParam('devicePool', ProxyInfo.getDevicesList(selenium, 'ANDROID'), "Select the Device a Test will run against.  ALL - Any available device, PHONE - Any available phone, TABLET - Any tablet")
                        //TODO: Check private repositories for parameter use and fix possible problems using custom pipeline
                        //stringParam('build', '.*', ".* - use fresh build artifact from S3 or local storage;\n2.2.0.3741.45 - exact version you would like to use")
						booleanParam('recoveryMode', false, 'Restart application between retries')
						booleanParam('auto_screenshot', autoScreenshot, 'Generate screenshots automatically during the test')
						booleanParam('keep_all_screenshots', keepAllScreenshots, 'Keep screenshots even if the tests pass')
						booleanParam('enableVNC', enableVNC, 'Enable VNC live sessions')
						booleanParam('enableVideo', enableVideo, 'Enable video recording')
						configure addHiddenParameter('DefaultPool', '', defaultMobilePool)
						configure addHiddenParameter('platform', '', 'ANDROID')
						break;
					case ~/^.*ios.*$/:
                        //TODO:  Need to adjust this for virtual as well.
                        choiceParam('devicePool', ProxyInfo.getDevicesList(selenium, 'iOS'), "Select the Device a Test will run against.  ALL - Any available device, PHONE - Any available phone, TABLET - Any tablet")
                        //TODO: Check private repositories for parameter use and fix possible problems using custom pipeline
                        //stringParam('build', '.*', ".* - use fresh build artifact from S3 or local storage;\n2.2.0.3741.45 - exact version you would like to use")
						booleanParam('recoveryMode', false, 'Restart application between retries')
						booleanParam('auto_screenshot', autoScreenshot, 'Generate screenshots automatically during the test')
						booleanParam('keep_all_screenshots', keepAllScreenshots, 'Keep screenshots even if the tests pass')
						booleanParam('enableVideo', enableVideo, 'Enable video recording')
						configure addHiddenParameter('DefaultPool', '', defaultMobilePool)
						configure addHiddenParameter('platform', '', 'iOS')
						break;
					default:
						booleanParam('auto_screenshot', false, 'Generate screenshots automatically during the test')
						booleanParam('keep_all_screenshots', false, 'Keep screenshots even if the tests pass')
						configure addHiddenParameter('platform', '', '*')
						break;
				}

				def nodeLabel = ""
				if (currentSuite.toXml().contains("jenkinsNodeLabel")) {
					nodeLabel = currentSuite.getParameter("jenkinsNodeLabel")
					configure addHiddenParameter('node_label', 'customized node label', nodeLabel)
				}

				def gitBranch = "master"
				if (currentSuite.getParameter("jenkinsDefaultGitBranch") != null) {
					gitBranch = currentSuite.getParameter("jenkinsDefaultGitBranch")
				}
				configure addExtensibleChoice('branch', "gc_GIT_BRANCH", "Select a GitHub Testing Repository Branch to run against", gitBranch)
				configure addHiddenParameter('project', '', project)
				configure addHiddenParameter('sub_project', '', sub_project)
				configure addHiddenParameter('zafira_project', '', zafira_project)
				configure addHiddenParameter('suite', '', suite)
				configure addHiddenParameter('ci_parent_url', '', '')
				configure addHiddenParameter('ci_parent_build', '', '')
				configure addExtensibleChoice('ci_run_id', '', 'import static java.util.UUID.randomUUID\nreturn [randomUUID()]')
				configure addExtensibleChoice('BuildPriority', "gc_BUILD_PRIORITY", "Priority of execution. Lower number means higher priority", "3")

				def threadCount = '1'
				if (currentSuite.toXml().contains("jenkinsDefaultThreadCount")) {
					threadCount = currentSuite.getParameter("jenkinsDefaultThreadCount")
				}
				stringParam('thread_count', threadCount, 'number of threads, number')


				stringParam('email_list', currentSuite.getParameter("jenkinsEmail").toString(), 'List of Users to be emailed after the test')
				if (currentSuite.toXml().contains("jenkinsFailedEmail")) {
					configure addHiddenParameter('failure_email_list', '', currentSuite.getParameter("jenkinsFailedEmail").toString())
				} else {
					configure addHiddenParameter('failure_email_list', '', '')
				}

				def retryCount = 0
				if (currentSuite.getParameter("jenkinsDefaultRetryCount") != null) {
					retryCount = currentSuite.getParameter("jenkinsDefaultRetryCount").toInteger()
				}
				
				if (retryCount != 0) {
					choiceParam('retry_count', [retryCount, 0, 1, 2, 3], 'Number of Times to Retry a Failed Test')
				} else {
					choiceParam('retry_count', [0, 1, 2, 3], 'Number of Times to Retry a Failed Test')
				}
				
				booleanParam('develop', false, 'Check to execute test without registration to Zafira')
				booleanParam('rerun_failures', false, 'During \"Rebuild\" pick it to execute only failed cases')
				def customFields = getCustomFields(currentSuite)
				configure addHiddenParameter('overrideFields', '' , customFields)

				def paramsMap = [:]
				paramsMap = currentSuite.getAllParameters()
				context.println("paramsMap: " + paramsMap)
				for (param in paramsMap) {
					// read each param and parse for generating custom project fields
					//	<parameter name="stringParam::name::desc" value="value" />
					//	<parameter name="stringParam::name" value="value" />
					context.println("param: " + param)
					def delimitor = "::"
					if (param.key.contains(delimitor)) {
						def (type, name, desc) = param.key.split(delimitor)
						switch(type.toLowerCase()) {
							case "hiddenparam":
								configure addHiddenParameter(name, desc, param.value)
								break;
							case "stringparam":
								stringParam(name, param.value, desc)
								break;
							case "choiceparam":
								choiceParam(name, Arrays.asList(param.value.split(',')), desc)
								break;
							case "booleanparam":
								booleanParam(name, param.value.toBoolean(), desc)
								break;
							default:
								break;
						}
					}
				}
				
				
				customPipelineParams(currentSuite, suiteOwner)
			}

			/** Git Stuff **/
			definition {
				cps {
					script(runJobPipelineScript)
					sandbox()
				}
			}

			properties {
				ownership { primaryOwnerId(suiteOwner) }
			}
		}
	}
	
	protected void customPipelineParams(org.testng.xml.XmlSuite currentSuite, String suiteOwner) {
		//do nothing here
	}

	protected Closure addExtensibleChoice(choiceName, globalName, desc, choice) {
		return { node ->
			node / 'properties' / 'hudson.model.ParametersDefinitionProperty' / 'parameterDefinitions' << 'jp.ikedam.jenkins.plugins.extensible__choice__parameter.ExtensibleChoiceParameterDefinition'(plugin: 'extensible-choice-parameter@1.3.3') {
				name choiceName
				description desc
				editable true
				choiceListProvider(class: 'jp.ikedam.jenkins.plugins.extensible_choice_parameter.GlobalTextareaChoiceListProvider') {
					whenToAdd 'Triggered'
					name globalName
					defaultChoice choice
				}
			}
		}
	}

	protected Closure addExtensibleChoice(choiceName, desc, code) {
		return { node ->
			node / 'properties' / 'hudson.model.ParametersDefinitionProperty' / 'parameterDefinitions' << 'jp.ikedam.jenkins.plugins.extensible__choice__parameter.ExtensibleChoiceParameterDefinition'(plugin: 'extensible-choice-parameter@1.3.3') {
				name choiceName
				description desc
				editable true
				choiceListProvider(class: 'jp.ikedam.jenkins.plugins.extensible_choice_parameter.SystemGroovyChoiceListProvider') {
					groovyScript {
						script code
						sandbox true
						usePrefinedVariables false
					}
				}
			}
		}
	}

	protected Closure addHiddenParameter(paramName, paramDesc, paramValue) {
		return { node ->
			node / 'properties' / 'hudson.model.ParametersDefinitionProperty' / 'parameterDefinitions' << 'com.wangyin.parameter.WHideParameterDefinition'(plugin: 'hidden-parameter@0.0.4') {
				name paramName
				description paramDesc
				defaultValue paramValue
			}
		}
	}

	public void createRegressionPipeline(pipelineJob, org.testng.xml.XmlSuite currentSuite, String project, String sub_project) {

		pipelineJob.with {

			description("project: ${project}; type: cron")
			logRotator { numToKeep 100 }

			authenticationToken('ciStart')

			parameters {
				choiceParam('env', getEnvironments(currentSuite), 'Environment to test against.')
				configure addHiddenParameter('project', '', project)
				configure addHiddenParameter('sub_project', '', sub_project)
				configure addHiddenParameter('ci_parent_url', '', '')
				configure addHiddenParameter('ci_parent_build', '', '')

				configure addExtensibleChoice('branch', "gc_GIT_BRANCH", "Select a GitHub Testing Repository Branch to run against", "master")

				stringParam('email_list', '', 'List of Users to be emailed after the test. If empty then populate from jenkinsEmail suite property')
				configure addExtensibleChoice('BuildPriority', "gc_BUILD_PRIORITY", "Priority of execution. Lower number means higher priority", "3")
				choiceParam('retry_count', [0, 1, 2, 3], 'Number of Times to Retry a Failed Test')
			}
			definition {
				cps {
					script(runCronPipelineScript)
					sandbox()
				}
			}
		}
	}

	protected List<String> getEnvironments(currentSuite) {
		def envList = getGenericSplit(currentSuite, "jenkinsEnvironments")

		if (envList.isEmpty()) {
			envList.add("DEMO")
			envList.add("STAG")
		}

		return envList
	}

	protected String getCustomFields(currentSuite) {
		def overrideFields = getGenericSplit(currentSuite, "overrideFields")
		def prepCustomFields = ""

		if (!overrideFields.isEmpty()) {
			for (String customField : overrideFields) {
				prepCustomFields = prepCustomFields + " -D" + customField
			}
		}

		return prepCustomFields
	}
	
	protected String listToString(currentSuite, parameterName) {
		def list = getGenericSplit(currentSuite, parameterName)
		def prepList = 'return ['

		if (!list.isEmpty()) {
			for (String l : list) {
				prepList = prepList + '"' + l + '", '
			}
			prepList = prepList.take(prepList.length() - 2)
		}

		prepList = prepList + ']'

		return prepList
	}

	protected List<String> getGenericSplit(currentSuite, parameterName) {
		String genericField = currentSuite.getParameter(parameterName)
		def genericFields = []

		if (genericField != null) {
			if (!genericField.contains(", ")) {
				genericFields = genericField.split(",")
			} else {
				genericFields = genericField.split(", ")
			}
		}
		return genericFields
	}



}
