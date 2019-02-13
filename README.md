# gradle-json-resume

[![jitpack build](https://jitpack.io/v/warlordofmars/gradle-json-resume.svg)](https://jitpack.io/#warlordofmars/gradle-json-resume)

## Overview

Gradle Plugin to provide a full CI/CD workflow for a [JSON Resume](https://jsonresume.org/)

## Features

* **Syntax/Schema Validation** - Resumé source is validated against normal JSON syntax rules, as well as the [JSON Resume Schema](https://jsonresume.org/schema/)
* **Spell Check** - Resumé source is checked for any spelling errors.  Any unrecognized words can be added to an ignore list if necessary.
* **URL Check** - Resumé source is parsed to find all instances of a valid URL.  Each URL is checked to confirm that the URL is still valid and currently responding
* **Multi-Forrmat Build** - Resumé source can be compiled into a number of different output formats:  HTML, PDF, Markdown, YAML
* **Custom Resumé Themes** - Each configured output format can be configured to use a custom [JSON Resume Theme](https://jsonresume.org/themes/)
* **Resumé Deployment** - Resumé will be deployed to several locations for ultimate consumpton:
  * **Web** - Resumé is published to a static website hosted in AWS S3
  * **Google Drive** - Resumé is published to a Google Drive document
  * **iCloud** - Resumé is published to a iCloud Drive document
  * **Print** - Resumé is printed using a local printer

* **Test Results** - All tests that are performed throughout the `build` and `deploy` process are captured and recorded in a JUnit-style XML report.

## Prerequisites

There are two prerequisites required to exist prior to using this plugin:

### hackmyresume

[hackmyresume](https://github.com/hacksalot/HackMyResume) is used to do the actual JSON Resume build.  This is being used, instead of the actual JSON Resume CLI utility because of an open [bug](https://github.com/jsonresume/resume-cli/issues/94) in the JSON Resume CLI utility related to PDF generation.  `hackmyresume` does not suffer from this same bug.

To install `hackmyresume` utility, run the following:

```bash
npm install -g hackmyresume
```

### aspell

[aspell](http://aspell.net/) is used to provide spell-checking functionality.

To install `aspell` utility, run the following:

```bash
brew install aspell
```

### awscli

[awscli](https://aws.amazon.com/cli/) is used to copy resumé files to an S3 bucket for publishing to the Web.

To install `awscli` utility, run the following:

```bash
brew install awscli
```

### wkhtmltopdf

[wkhtmltopdf](https://wkhtmltopdf.org/) is used by `hackmyresume` for PDF generation.

To install the `wkhtmltopdf` utility, run the following:

```bash
brew install wkhtmltopdf
```

### gdrive

[gdrive](https://github.com/prasmussen/gdrive) (Google Drive CLI) is used to publish resumé file to Google Drive.

To install the `gdrive` utility, run the following:

```bash
brew install gdrive
```

## Setup

To use this plugin, the following buildscript repositories and dependencies must be configured:

```gradle
buildscript {
  repositories {
    maven { url 'https://jitpack.io' }
  }
  dependencies {
    classpath 'com.github.warlordofmars:gradle-json-resume:release-0.1.3'
  }
}
```

Then to apply the plugin:

```gradle
apply plugin: 'com.github.warlordofmars.gradle.resume'
```

To configure:

```gradle
resume {

    // list of resume formats to be generated (must contain 'html' and 'pdf')
    resumeFormats = ['html', 'pdf', 'yaml', 'md']

    // the JSON Resume source file
    resumeSource = 'resume.json'

    // mapping of themes to be used with each resumeFormat configured
    themes = [
        html: '',
        pdf: ''
    ]


    websiteUrl = rootProject.websiteUrl
    websitePrefix = rootProject.websitePrefix
    numberOfCopies = rootProject.numberOfCopies
    ensureStrings = rootProject.ensureStrings
    spellCheckIgnoreList = rootProject.spellCheckIgnoreList
    isPromote = rootProject.isPromote
}
```

## Versioning

Versioning on this project is applied automatically on all changes using the [axion-release-plugin](https://github.com/allegro/axion-release-plugin).  Git tags are created for all released versions, and all available released versions can be viewed in the [Releases](https://github.com/warlordofmars/gradle-json-resume/releases) section of this project.

## Author

* **John Carter** - [warlordofmars](https://github.com/warlordofmars)

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details

## Acknowledgments

* Using [gdrive](https://github.com/prasmussen/gdrive) (Google Drive CLI) to publish resumé file to Google Drive.
