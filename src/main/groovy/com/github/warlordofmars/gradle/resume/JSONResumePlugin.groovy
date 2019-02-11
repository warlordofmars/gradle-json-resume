package com.github.warlordofmars.gradle.resume


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Delete

import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import java.util.TimeZone
import java.io.ByteArrayOutputStream

import com.github.warlordofmars.gradle.resume.JSONResumeExtension


class JSONResumePlugin implements Plugin<Project> {

    JSONResumeExtension mExtension

    void apply(Project project) {

        mExtension = project.extensions.create('resume', JSONResumeExtension)

        project.plugins.apply('com.github.warlordofmars.gradle.prerequisites')
        project.rootProject.plugins.apply('com.github.warlordofmars.gradle.customtest')

        project.afterEvaluate {

            
                
            project.ext.tests = [

                ensureStringsTest: [mExtension.resumeSource, 'Expected Strings Found in Deployed HTML Resume'],
                resumeDeployedWebTest: [mExtension.resumeSource, 'Deployed Resumes Are Web Accessible'],
                resumeDeployedGoogleTest: [mExtension.resumeSource, 'Resume Deployed to Google Drive'],
                resumeDeployedAppleTest: [mExtension.resumeSource, 'Resume Deployed to iCloud Drive'],
                
                ensureStringsProdTest: [mExtension.resumeSource, 'Expected Strings Found in Deployed HTML Resume in Production'],
                resumeDeployedWebProdTest: [mExtension.resumeSource, 'Deployed Resumes Are Web Accessible in Production'],
                resumeDeployedGoogleProdTest: [mExtension.resumeSource, 'Resume Deployed to Google Drive in Production'],
                resumeDeployedAppleProdTest: [mExtension.resumeSource, 'Resume Deployed to iCloud Drive in Production'],
                
                resumeContentAnalysis: [mExtension.resumeSource, 'Resume Content Analysis'],
                resumeUrlCheckTest: [mExtension.resumeSource, 'Resume URLs Validation'],
                spellCheckTest: [mExtension.resumeSource, 'Resume Spell Check'],
                jsonSchemaValidationTest: [mExtension.resumeSource, 'JSON Resume Schema Validation'],
                jsonSyntaxValidationTest: [mExtension.resumeSource, 'JSON Syntax Validation']

            ]


            project.ext.prerequisites << [
                'hackmyresume': 'Install via \'npm install -g hackmyresume\'',
                'aspell': 'Install via \'brew install aspell\'',
                'lp': 'This is a built-in that should already be there.  Why isn\'t it there?',
                'aws': 'Install via \'brew install awscli\'',
                'wkhtmltopdf': 'Install via \'brew install wkhtmltopdf\'',
                'gdrive': 'Install via \'brew install gdrive\'',
            ]


            def iCloudLocalDir = "${System.env.HOME}/Library/Mobile Documents/com~apple~CloudDocs"
            def TASK_GROUP = 'JSON Resume'

            project.task('validateJson') {
                description 'Confirms resume source is valid JSON'
                group TASK_GROUP
                inputs.file(mExtension.resumeSource)
                dependsOn project.rootProject.registerTests
                doLast {
                    try {
                        new JsonSlurper().parseText(project.file(mExtension.resumeSource).text)
                        project.rootProject.jsonSyntaxValidationTest.success("Valid JSON!")
                    } catch(Exception e) {
                        project.rootProject.jsonSyntaxValidationTest.failure('JSON Syntax Error', e.toString())
                    }
                }
            }

            project.task('validateResume') {
                description 'Confirms JSON Resume Scheme in resume source'
                group TASK_GROUP
                inputs.file(mExtension.resumeSource)
                dependsOn project.validateJson, project.checkPrerequisites, project.rootProject.registerTests
                doFirst {
                    def output = new ByteArrayOutputStream()
                    project.exec {
                        commandLine 'hackmyresume', 'validate', mExtension.resumeSource
                        standardOutput output
                    }
                    if (output.toString().contains('INVALID')) {
                        project.rootProject.jsonSchemaValidationTest.failure('Validation Vailed', output.toString())
                    } else {
                        project.rootProject.jsonSchemaValidationTest.success(output.toString())
                    }
                }
            }

            project.task('spellCheck') {
                description 'Runs resume source through spell check, ignoring configurable list of words'
                group TASK_GROUP
                inputs.file(mExtension.resumeSource)
                mustRunAfter project.validateResume
                dependsOn project.checkPrerequisites, project.rootProject.registerTests
                doFirst {
                    def out = new ByteArrayOutputStream()
                    project.exec {
                        commandLine 'aspell', 'list', '-t', '--home-dir=.', "--personal=${mExtension.spellCheckIgnoreList}"
                        standardInput new ByteArrayInputStream(project.file(mExtension.resumeSource).text.getBytes())
                        standardOutput out
                    }
                    def errors = out.toString().readLines()
                    if (errors.size() > 0) {
                        project.rootProject.spellCheckTest.failure('Mis-spelled Word', "There were ${errors.size()} words misspelled in ${mExtension.resumeSource}:\n\n${errors}")
                    }
                    project.rootProject.spellCheckTest.success("No spelling errors detected in ${mExtension.resumeSource}")
                }
            }

            project.task('checkUrls') {
                description 'Finds all URLs mentioned anywhere in resume source, and checks to make sure they are valid URLs and are currently responding'
                group TASK_GROUP
                inputs.file(mExtension.resumeSource)
                mustRunAfter project.validateResume
                dependsOn project.rootProject.registerTests
                doFirst {
                    def URLs = project.file(mExtension.resumeSource).text.findAll('http[s]?://[a-zA-Z0-9./-]+')
                    URLs.each { detectedURL ->
                        try {
                            def test = detectedURL.toURL().text
                            println "Found the URL ${detectedURL} in ${mExtension.resumeSource} and it is currently a live link."
                            
                        } catch(Exception e) {
                            project.rootProject.resumeUrlCheckTest.failure('Bad URL Found', "Could not connect to the URL: ${detectedURL} \nFound in ${mExtension.resumeSource}\n\n${e.toString()}")
                        }
                    }
                    project.rootProject.resumeUrlCheckTest.success("All URLs found in ${mExtension.resumeSource} are valid URLs and are currently responding:\n\n${URLs.join('\n')}")
                }
            }

            project.task('buildResume') {
                description 'Generates new JSON resume from source, dynamically populating version and other metadata variables in the JSON'
                group TASK_GROUP
                inputs.file(mExtension.resumeSource)
                outputs.file("${project.buildDir}/${mExtension.resumeSource}")
                mustRunAfter project.checkUrls, project.spellCheck
                doLast {
                    def resumeFile = project.file(mExtension.resumeSource)
                    def resumeLastModified = new Date(resumeFile.lastModified()).format('YYYY-MM-DD\'T\'hh:mm:ss\'Z\'', TimeZone.getTimeZone('GMT'))
                    def resumeObj = new JsonSlurper().parseText(resumeFile.text)
                    resumeObj['meta'] = [ 
                        version: project.rootProject.version,
                        lastModified: resumeLastModified
                    ]
                    project.file("${project.buildDir}").mkdirs()
                    project.file("${project.buildDir}/${mExtension.resumeSource}").write(new JsonBuilder(resumeObj).toPrettyString())
                    println "Versioning ${mExtension.resumeSource} to version ${project.rootProject.version}"

                }
            }

            mExtension.resumeFormats.each { format ->
                project.task("build${format.capitalize()}Resume") {
                    description "Generates ${format} Resume from resume source"
                    group TASK_GROUP
                    inputs.file("${project.buildDir}/${mExtension.resumeSource}")
                    outputs.file("${project.buildDir}/resume.${format}")
                    dependsOn project.buildResume
                    mustRunAfter project.validateResume, project.spellCheck, project.checkUrls
                    doFirst {
                        if (mExtension.themes.containsKey(format)) {
                            buildResumeWithTheme(project, format, mExtension.themes[format])
                        } else {
                            buildResume(project, format)
                        }
                    }
                }
            }

            project.task('analyzeResume') {
                description 'Runs some basic content analysis on resume, highlighting keywords mentioned'
                group TASK_GROUP
                inputs.file(mExtension.resumeSource)
                dependsOn project.rootProject.registerTests, project.checkPrerequisites
                mustRunAfter project.validateResume, project.spellCheck
                doFirst {
                    try {
                        def out = new ByteArrayOutputStream()
                        project.exec {
                            commandLine 'hackmyresume', 'analyze', mExtension.resumeSource
                            standardOutput out
                        }
                        def report = out.toString()
                        project.file(project.buildDir).mkdirs()
                        project.file("${project.buildDir}/resume-analysis.txt").text = report
                        project.rootProject.resumeContentAnalysis.success(report)
                    } catch(Exception e) {
                        project.rootProject.resumeContentAnalysis.failure('Error Generating Report', e.toString())
                    }
                }
            }

            project.task('clean', type:Delete) {
                description 'Delete all generated files in build directory'
                group TASK_GROUP
                delete project.buildDir
            }

            project.task('build') {
                description 'Meta task to run all required tasks to build resume'
                group TASK_GROUP
                dependsOn project.validateResume, project.spellCheck, project.checkUrls, project.analyzeResume
                
            }

            project.build.dependsOn {
                project.tasks.findAll {
                    it.name ==~ /build.*Resume/
                }
            }

            project.task('print') {
                description 'Print pdf version of resume on locally configured printer'
                group TASK_GROUP
                mustRunAfter project.build
                dependsOn project.checkPrerequisites
                doFirst {
                    project.exec {
                        commandLine 'lp', '-n', mExtension.numberOfCopies, '-o', 'fit-to-page', '-o', 'sides=two-sided-long-edge', '-o', 'media=letter', "${project.buildDir}/resume.pdf"
                    }
                }
            }

            project.task('printPreview') {
                description 'Open pdf version of resume for viewing locally'
                group TASK_GROUP
                dependsOn project.buildPdfResume
                doFirst {
                    project.exec {
                        commandLine 'open', "${project.buildDir}/resume.pdf"
                    }
                }
            }

            project.task('publishResumeToWeb') {
                description 'Publish all resume artifacts to the web'
                group TASK_GROUP
                mustRunAfter project.build
                dependsOn project.checkPrerequisites
                doFirst {

                    project.exec {
                        commandLine 'aws', 's3', 'cp', "${project.buildDir}/resume.html", "s3://${mExtension.websiteUrl}${mExtension.websitePrefix}/index.html"
                    }

                    project.exec {
                        commandLine 'aws', 's3', 'cp', "${project.buildDir}/resume-analysis.txt", "s3://${mExtension.websiteUrl}${mExtension.websitePrefix}/"
                    }

                    mExtension.resumeFormats.each { format ->
                        project.exec {
                            commandLine 'aws', 's3', 'cp', "${project.buildDir}/resume.${format}", "s3://${mExtension.websiteUrl}${mExtension.websitePrefix}/resume.${format}"
                        }
                    }
                }
            }

            project.task('publishResumeToGoogle') {
                description 'Publish pdf resume to Google Drive'
                group TASK_GROUP
                mustRunAfter project.build
                dependsOn project.checkPrerequisites
                doLast {

                    def out = new ByteArrayOutputStream()
                    project.exec {
                        commandLine 'gdrive', 'list', '--no-header', '-q', "name = 'resume.v${project.rootProject.version}.pdf'"
                        standardOutput out
                    }

                    out.toString().readLines().each {
                        def fileId = it.split(" ")[0]
                        project.exec {
                            commandLine 'gdrive', 'delete', fileId
                        }
                    }

                    project.exec {
                        commandLine 'gdrive', 'upload', '--name', "resume.v${project.rootProject.version}.pdf", "${project.buildDir}/resume.pdf"
                    }

                    if(mExtension.isPromote) {
                        def out2 = new ByteArrayOutputStream()
                        project.exec {
                            commandLine 'gdrive', 'list', '--no-header', '-q', 'name = "resume.pdf"'
                            standardOutput out2
                        }
                        def fileId = out2.toString().split(" ")[0]
                        project.exec {
                            commandLine 'gdrive', 'update', fileId, "${project.buildDir}/resume.pdf"
                        }
                    }
                }
            }

            project.task('publishResumeToApple') {
                description 'Publish pdf resume to iCloud Drive'
                group TASK_GROUP
                mustRunAfter project.build
                dependsOn project.checkPrerequisites
                doLast {
                    project.exec {
                        commandLine 'cp', "${project.buildDir}/resume.pdf", "${iCloudLocalDir}/resume.v${project.rootProject.version}.pdf"
                    }
                    if(mExtension.isPromote) {
                        project.exec {
                            commandLine 'cp', "${project.buildDir}/resume.pdf", "${iCloudLocalDir}/resume.pdf"
                        }
                    }
                }
            }

            project.task('postDeployCheck') {
                description 'Run a series of simple tests post-deploy to ensure all resources have been deployed as expected'
                group TASK_GROUP
                mustRunAfter project.publishResumeToWeb, project.publishResumeToGoogle, project.publishResumeToApple
                dependsOn project.rootProject.registerTests
                doFirst {

                    def ensureStringsTestObj = project.rootProject.ensureStringsTest
                    def resumeDeployedWebTestObj = project.rootProject.resumeDeployedWebTest
                    def resumeDeployedGoogleTestObj = project.rootProject.resumeDeployedGoogleTest
                    def resumeDeployedAppleTestObj = project.rootProject.resumeDeployedAppleTest

                    def resumeFileName = "resume.v${project.rootProject.version}.pdf"

                    if(mExtension.isPromote) {
                        ensureStringsTestObj = project.rootProject.ensureStringsProdTest
                        resumeDeployedWebTestObj = project.rootProject.resumeDeployedWebProdTest
                        resumeDeployedGoogleTestObj = project.rootProject.resumeDeployedGoogleProdTest
                        resumeDeployedAppleTestObj = project.rootProject.resumeDeployedAppleProdTest
                        resumeFileName = 'resume.pdf'
                    }
                    
                    def deployURL = "https://${mExtension.websiteUrl}${mExtension.websitePrefix}/"
                    def content = deployURL.toURL().text
                    
                    mExtension.ensureStrings.each { word ->
                        if (!content.contains(word)) {
                            ensureStringsTestObj.failure('Expected String Not Found', "The ensure string \"${word}\" was not found in ${deployURL}")
                        }
                    }
                    ensureStringsTestObj.success("Found all expected strings in deployed HTML resume at ${deployURL}:\n\n${mExtension.ensureStrings.join('\n')}")
                    
                    def urlList = []
                    mExtension.resumeFormats.each { format ->
                        def url = "https://${mExtension.websiteUrl}${mExtension.websitePrefix}/resume.${format}"
                        try {
                            def test = url.toURL().text
                            println "Found deployed ${format} resume at ${url}"                
                            urlList << url
                        } catch(Exception e) {
                            resumeDeployedWebTestObj.failure('Deployed Resume Not Accessible', "The ${format} version of the resume could not be found at the deployed URL of ${url}\n\n${e.toString()}")
                        }
                    }
                    resumeDeployedWebTestObj.success("All resume formats have been deployed successfully and are currently web-accessible at the following URLs:\n\n${urlList.join('\n')}")

                    def out = new ByteArrayOutputStream()
                    project.exec {
                        commandLine 'gdrive', 'list', '--no-header', '-q', "name = '${resumeFileName}'"
                        standardOutput out
                    }

                    if(out.toString().split(" ").size() > 1) {
                        resumeDeployedGoogleTestObj.success("${resumeFileName} is available in Google Drive")
                    } else {
                        resumeDeployedGoogleTestObj.failure('Resume Not Found in Google Drive', "${resumeFileName} is not found in Google Drive")
                    }


                    if(project.file("${iCloudLocalDir}/resume.v${project.rootProject.version}.pdf").exists()) {
                        resumeDeployedAppleTestObj.success("${resumeFileName} is available in iCloud Drive")
                    } else {
                        resumeDeployedAppleTestObj.failure('File Not Found', "${resumeFileName} was not found in iCloud Drive")
                    }
                    
                }
            }

            project.task('deploy') {
                description 'Meta task for running all required tasks for deploying all resume artifacts'
                group TASK_GROUP
                dependsOn project.publishResumeToWeb, project.publishResumeToApple, project.publishResumeToGoogle, project.postDeployCheck    
                if(mExtension.isPromote) {
                    dependsOn project.rootProject.tasks['githubRelease']
                }
            }

        }
    }

    def buildResumeWithTheme(project, format, theme) {
        project.exec {
            commandLine 'hackmyresume', 'build', "${project.buildDir}/${mExtension.resumeSource}", 'TO', "${project.buildDir}/resume.${format}", '-t', theme
        }
    }

    def buildResume(project, format) {
        project.exec {
            commandLine 'hackmyresume', 'build', "${project.buildDir}/${mExtension.resumeSource}", 'TO', "${project.buildDir}/resume.${format}"
        }
    }

}