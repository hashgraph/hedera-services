# Maintainers-Guide

## IntelliJ set up
Install IntelliJ for development using the [jetbrains toolbox](https://www.jetbrains.com/lp/toolbox/), 
instead of installing directly. It is helpful for upgrading to the latest version easily.
If you are on an M1/M2 chipset, select **.dmg (macOs Apple Silicon)**.

Once you have the jetbrains toolbox installed, open it up. Install **Intellij IDEA Ultimate**.

## Clone repository set up

Clone this repository using:
```
git clone git@github.com:hashgraph/hedera-services.git
```

From IntelliJ, choose `File -> Open` the _hedera-services/_ directory you just cloned.

Make sure you are using JDK17 as the project SDK. You can download JDK-17.0.3 for mac [here](https://adoptium.net/temurin/releases/).

<p>
    <img src="assets/jdk-17.png"/>
</p>

## Gradle
Once the repository is opened in IntelliJ, we recommend using either the Gradle command line 
`(./gradlew spotlessApply)`  or the [Google Java Format IntelliJ Plugin](https://github.com/google/google-java-format#intellij-android-studio-and-other-jetbrains-ides) 
to format your code. 
Please make sure to set your code style under `IntelliJ IDEA -> Preferences -> google-java-format Settings -> Code Style` 
to the `Android Open Source Project (AOSP)` style if using the IntelliJ plugin.

Open the Gradle tool window, and run `Tasks/build/assemble` to on the root project.

## GPG set up
Every commit being pushed to the repository should be verified. So it is important to set up GPG keys before 
contributing to the repository. Use the following tutorials to set up a GPG key. 

*Be sure to enable Vigilant Mode and adding GPG key in GitHub*.

- Github
    - [Github - Generating a new GPG key](https://docs.github.com/en/authentication/managing-commit-signature-verification/generating-a-new-gpg-key)
    - [Github - Adding a GPG key to your Github account](https://docs.github.com/en/authentication/managing-commit-signature-verification/adding-a-gpg-key-to-your-github-account)
    - [Github - Configuring your Git CLI for GPG commit signing](https://docs.github.com/en/authentication/managing-commit-signature-verification/telling-git-about-your-signing-key)
    - [Github - Signing Commits with the Git CLI](https://docs.github.com/en/authentication/managing-commit-signature-verification/signing-commits)
    - [Github - Vigilant Mode](https://docs.github.com/en/authentication/managing-commit-signature-verification/displaying-verification-statuses-for-all-of-your-commits) ‼️
- IntelliJ IDEA (if you use IntelliJ to interact with git)
    - [IntelliJ - Signing Commits with GPG Keys](https://www.jetbrains.com/help/idea/set-up-GPG-commit-signing.html)
    - [IntelliJ Official YouTube - GPG Commit Signing (10:59)](https://youtu.be/RBhz-8fZN9A?t=659)

## Development Model
We follow [GitFlow branching model](https://nvie.com/posts/a-successful-git-branching-model/) for the development.

<p>
    <img src="./assets/gitflow-branching-model.png"/>
</p>

As per this model , there will be 2 long-lived branches:
1. `develop` - This will be the default branch developers will be working on. It will always be up-to-date with the 
latest development changes for the upcoming release.
2. `main` - This will have the `production-ready` code in it. In most cases release engineering will be working with it.

### Creating issues on GitHub
Any feature/bugfix that will be worked on in the release cycle, should be associated to an issue in GitHub.
The issue should be associated to the `Services Sprint Tracking` and the associated project type in `Projects` tab.
It should also have the targeted milestone set on it.

<p>
    <img src="./assets/labels-on-issue.png"/>
</p>

### User Stories

#### As a developer, I would like to create a branch to work on the feature for the upcoming release
As per the development model, every developer should create a feature branch to work from `develop` branch. The 
`develop` branch should be up-to-date with all the features going into the next release.

#### As a developer, I would like to create a branch to work on the feature NOT targeted for upcoming release
As per the development model, every developer should create a feature branch to work from `develop` branch. But, the 
feature branch should NOT be merged into `develop` until the decision is made if the feature is going into upcoming 
release.

#### As a developer, I would like to merge my feature branch or bug fix for the upcoming release
Open a pull request from the feature branch to `develop` branch and add `hashgraph/hedera-services-team` as reviewers.

Also add the following labels on the pull request :
- `CI:UnitTests` - Initiates PR UnitTests needed for the PR to merge
- `CI:FullStackTests` - Initiates full stack PR checks needed for PR to merge
- `CI:FinalChecks` - Initiates final checks required for the PR to merge

PR should be merged after an approving review and all the checks are passed.

NOTE: Any feature that is not going into the upcoming release should stay in the feature branch and should not be merged
to `develop`.
NOTE: Please use either the Gradle command line`(./gradlew spotlessApply)`  or the [Google Java Format IntelliJ Plugin](https://github.com/google/google-java-format#intellij-android-studio-and-other-jetbrains-ides)
to format your code to avoid failing checks in CI pipeline.

#### As a release engineer, I would like to create a release branch

Release branch should be created from `develop` branch at the end of first sprint in the release cycle. This adheres to
completing all the development targeted for the upcoming release in the first sprint of release cycle.

Once a release branch is created there _should not_ be any feature developments merged into `release` branch targeted 
for that release.

The initial `alpha` tags will be tagged from the release branch created.

#### As a developer, I would like to merge a bugfix/hotfix after release branch is created

Once the release branch is created, only bugfixes or hotfixes should be merged into release branch. To do that, create 
a `hotfix` from the `release` branch. Once the fix is in the branch, open a pull request to the release branch. Once 
the fix is merged into `release` branch, it should be cherry-picked into the `develop` branch.

#### As a developer, I would like to merge a bugfix/hotfix from the production code

To fix a bug from one of the previous releases(production code), create a hotfix branch from `main`. Once the fix is in
the branch, create a pull request targeting to `main`. Once bugfix is merged into `main`and it should be cherry-picked 
back into the current `release` branch(if the release branch is still open), and also into `develop`.

#### As a release engineer, I would like to tag the main release at the end of release cycle

At the end of release cycle, release engineering will merge the release branch for current release into `main`and tag 
release from the `main` branch.
