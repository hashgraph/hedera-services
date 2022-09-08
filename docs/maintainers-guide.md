# Maintainers-Guide

## IntelliJ set up
Install IntelliJ for development using the jetbrains toolbox, instead of installing directly for upgrading to latest version easily.
Download at [https://www.jetbrains.com/lp/toolbox/](https://www.jetbrains.com/lp/toolbox/) and select the correct version. 
If you are on an M1/M2 chipset, select **.dmg (macOs Applie Silicon)**.
Once you have the jetbrains toolbox installed, open it up. 
Install **Intellij IDEA Ultimate**.

## Clone repository set up

Clone this repository:
```
git clone git@github.com:hashgraph/hedera-services.git
```

From IntelliJ, choose `File -> Open` the _hedera-services/_ directory you just cloned.

Make sure you are using JDK17 as the project SDK:

<p>
    <img src="./assets/sdk-17.png"/>
</p>

Open the Gradle tool window, and run `Tasks/build/assemble` in the root project:

## GPG set up
Every commit being pushed to the repository should be verified. So it is important to set up GPG keys before contributing to the repository.
Use the following tutorials to set up a GPG key. Be sure to enable Vigilant Mode in github.

- Github
    - [Github - Generating a new GPG key](https://docs.github.com/en/authentication/managing-commit-signature-verification/generating-a-new-gpg-key)
    - [Github - Adding a GPG key to your Github account](https://docs.github.com/en/authentication/managing-commit-signature-verification/adding-a-gpg-key-to-your-github-account)
    - [Github - Configuring your Git CLI for GPG commit signing](https://docs.github.com/en/authentication/managing-commit-signature-verification/telling-git-about-your-signing-key)
    - [Github - Signing Commits with the Git CLI](https://docs.github.com/en/authentication/managing-commit-signature-verification/signing-commits)
    - [Github - Vigilant Mode](https://docs.github.com/en/authentication/managing-commit-signature-verification/displaying-verification-statuses-for-all-of-your-commits) ‼️
- IntelliJ IDEA (if you use IntelliJ to interact with git)
    - [IntelliJ - Signing Commits with GPG Keys](https://www.jetbrains.com/help/idea/set-up-GPG-commit-signing.html)
    - [IntelliJ Official YouTube - GPG Commit Signing (10:59)](https://youtu.be/RBhz-8fZN9A?t=659)