package com.qaprosoft.jenkins.jobdsl.factory.job.hook

import com.qaprosoft.jenkins.jobdsl.factory.job.JobFactory
import groovy.transform.InheritConstructors

@InheritConstructors
public class PullRequestJobFactoryTrigger extends JobFactory {

    def host
    def organization
    def repo
    def scmRepoUrl
	def branch

    public PullRequestJobFactoryTrigger(folder, jobName, jobDesc, host, organization, repo, scmRepoUrl, branch) {
        this.folder = folder
        this.name = jobName
        this.description = jobDesc
        this.host = host
        this.organization = organization
        this.repo = repo
        this.scmRepoUrl = scmRepoUrl
		this.branch = branch
    }

    def create() {
        def freestyleJob = super.create()
        freestyleJob.with {
            concurrentBuild(true)
            parameters {
				//[VD] do not remove empty declaration otherwise params can't be specified dynamically
            }

            scm {
                git {
                    remote {
                        github(this.organization + '/' + this.repo)
						credentials("${organization}-${repo}")
                        refspec('+refs/pull/*:refs/remotes/origin/pr/*')
                    }
                    branch(this.branch)
                }
            }

            triggers {
                ghprbTrigger {
                    gitHubAuthId(getGitHubAuthId(this.folder))
                    adminlist('')
                    useGitHubHooks(true)
                    triggerPhrase('')
                    autoCloseFailedPullRequests(false)
                    skipBuildPhrase('.*\\[skip\\W+ci\\].*')
                    displayBuildErrorsOnDownstreamBuilds(false)
                    cron('H/5 * * * *')
                    whitelist('')
                    orgslist(organization)
                    blackListLabels('')
                    whiteListLabels('')
                    allowMembersOfWhitelistedOrgsAsAdmin(false)
                    permitAll(true)
                    buildDescTemplate('')
                    blackListCommitAuthor('')
                    includedRegions('')
                    excludedRegions('')
                    onlyTriggerPhrase(false)
                    commentFilePath('')
                    msgSuccess('')
                    msgFailure('')
                    commitStatusContext('')
                }
            }

            steps {
                downstreamParameterized {
                    trigger('onPullRequest-' + this.repo) {
                        block{
                            buildStepFailure('FAILURE')
                            failure('FAILURE')
                            unstable('UNSTABLE')
                        }
                        parameters {
                            currentBuild()
                        }
                    }
                }
            }
        }
        return freestyleJob
    }

    protected def getGitHubAuthId(project) {
        return "https://api.github.com : ${project}-token"
    }
}