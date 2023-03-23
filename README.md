# French Pack for SonarQube

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=jycr_sonar-l10n-fr&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=jycr_sonar-l10n-fr)
[![Build](https://github.com/jycr/sonar-l10n-fr/actions/workflows/build.yml/badge.svg)](https://github.com/jycr/sonar-l10n-fr/actions/workflows/build.yml)

This plugin offers a French language localization of [SonarQube](http://www.sonarqube.org/).

## Releases

Releases can be installed directly via the Update Center page (browse Administration > System > Update Center). They can also be [downloaded manually](https://github.com/jycr/sonar-l10n-fr/releases) (see [release notes](https://github.com/jycr/sonar-l10n-fr/releases)).

## Contributing

### Have questions or feedback?

To provide feedback (request a feature, report a bug etc.), you can fill an [issue](https://github.com/jycr/sonar-l10n-fr/issues).

### Pull Request (PR)

To submit a contribution, create a pull request for this repository. Please make sure that you follow [code style](https://github.com/SonarSource/sonar-developer-toolset#code-style-configuration-for-intellij) and all tests are passing.

## Proposals for the translation of the main concepts

Localization follows the principle of [epicene writing](https://fr.wikipedia.org/wiki/Langage_%C3%A9pic%C3%A8ne "Open french Wikiepdia page for 'langage épicène'").

Below the list of the main concepts used in SonarQube, their description and their proposed French translation (sometimes with other proposals or synonyms).

### [Quality Profile](https://docs.sonarqube.org/latest/instance-administration/quality-profiles/)

> Define the set of [rules](https://docs.sonarqube.org/latest/user-guide/rules/overview/) to be applied during code analysis.

Translation proposed: _**:fr: Profil Qualité**_

### [Quality Gate](https://docs.sonarqube.org/latest/user-guide/quality-gates/)

> Quality gates enforce a quality policy in your organization by answering one question: is my project ready for release?
>
> To answer this question, you define a set of conditions against which projects are measured. For example:
> * No new blocker issues
> * Code coverage on new code greater than 80%

Translation proposed: _**:fr: Barrière Qualité**_

### [Issue](https://docs.sonarqube.org/latest/user-guide/issues/)

> While running an analysis, SonarQube raises an issue every time a piece of code breaks a coding rule.

Translation proposed: _**:fr: Problème**_

Other translation suggestions (to be discussed):
* _**:fr: Défaut**_
    * Can be confused with the term: _**:fr: Valeur par défaut**_ (_default value_)
* _**:fr: Vice**_

### [Security Hotspot](https://docs.sonarqube.org/latest/user-guide/security-hotspots/)

> A security hotspot highlights a security-sensitive piece of code that the developer needs to review. Upon review, you'll either find there is no threat or you need to apply a fix to secure the code.
>
> Another way of looking at hotspots can be the concept of [Defense in depth (computing)](https://en.wikipedia.org/wiki/Defense_in_depth_(computing)), in which several redundant protection layers are placed in an application so that it becomes more resilient in the event of an attack.

Translation proposed: _**:fr: Risque de sécurité**_

### [Portfolio](https://docs.sonarqube.org/latest/user-guide/portfolios/)

> The portfolio home page is the central place for managers and tech leads to keep an eye on the releasability of the projects under their supervision. Releasability is based on the portfolio's projects' [quality gates](https://docs.sonarqube.org/latest/user-guide/quality-gates/). Each portfolio home page offers an aggregate view of the releasability status of all projects in the portfolio.

Translation proposed: _**:fr: Portfolio**_

Other translation suggestions (to be discussed):
* _**:fr: Portefeuille**_

### [Tag](https://docs.sonarqube.org/latest/user-guide/rules/built-in-rule-tags/)

> Tags are a way to categorize rules and issues. Issues inherit the tags on the rules that raised them. Some tags are language-specific, but many more appear across languages. Users can add tags to rules and issues and most rules have some tags out of the box

Translation proposed: _**:fr: Étiquette**_

Other translation suggestions (to be discussed):
* _**:fr: Libellé**_

### [Marketplace](https://docs.sonarqube.org/latest/instance-administration/marketplace/)

> The Marketplace is the place for keeping the pieces of the SonarQube platform up to date. It lets you:

Translation proposed: _**:fr: Boutique d'application**_

### [Code smell](https://docs.sonarqube.org/latest/user-guide/concepts/#quality)

> A maintainability-related issue in the code. Leaving it as-is means that at best, developers maintaining the code will have a harder time than they should when making changes. At worst, they'll be so confused by the state of the code that they'll introduce additional errors as they make changes.

Translation proposed: _**:fr: Mauvaise pratique de programmation**_

### [Clean as You Code](https://docs.sonarqube.org/latest/user-guide/clean-as-you-code/)

> The Marketplace is the place for keeping the pieces of the SonarQube platform up to date. It lets you:

Translation proposed: _**:fr: Nettoyer en codant**_
