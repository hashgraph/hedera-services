# Gradle quickstart

## Installation

The repo contains a shell script called gradlew (or gradlew.bat on windows). This script will automatically download the
right version of Gradle for this project, scoped to this project. This means you never need to have Gradle installed
manually on your computer. It also means, as the project updates to newer versions of Gradle, you will pick them up
automatically. And, the version of Gradle is checked in, meaning you will always have the right version for whatever
commit you are building from.

Further, Gradle has the concept of a "toolchain". In our case, this toolchain is Java. Each individual developer needs
some semi-recent version of Java installed for running Gradle itself, but within the Gradle build it defines what
version of Java to download and use for building the project (compile, test, etc). This means you do not have to have a
specific version of Java pre-installed on your machine, but will always get the right version for the commit/branch you
are working on.

## Libraries and Dependencies

The `settings.gradle.kts` file in the root of the repo defines the "library catalog" -- the set of libraries from which
subprojects should select. By rule (which is not enforceable in Gradle itself), we should never declare a dependency for
the first time in a subproject. Instead, we first define the library, its version, and any library bundles in
`settings.gradle.kts`. Each subproject then declares its dependencies based on those libraries. In this way, we have a
single master-list of all libraries that have been approved for the project, including their versions.

## Versions

Our Gradle build has a single version number for all projects. It is defined in gradle.properties. Changing this version
number will automatically apply to every subproject.

## buildSrc

Gradle has plugins. One type of plugin is the "convention plugin". A convention plugin is a plugin that applies a
certain set of defaults to all builds that include that convention. We define one such `hedera-convention` in buildSrc.
It is then used by each of the subprojects to reduce the amount of boilerplate. We can create additional conventions in
the future if need be. buildSrc is a special directory in Gradle for hosting custom project plugins.

## Sub Projects

Each subproject has its own `build.gradle.kts` where the dependencies for that project are defined. Some subprojects (
like `hedera-node`) have additional build logic.

## Usage

To build, simply `./gradlew build`. This is the most comprehensive task. It will compile, assemble, and test the
project. If you only want to test a specific subproject, you can do `./gradlew hedera-node:build` for example, or if you
are in the hedera-build subdirectory you can use `../gradlew build`. Gradle is smart and builds everything your project
depends on before building your project, but doesn't rebuild anything it doesn't need to.

You can use `./gradlew compile` if you just want to compile and not test, or `./gradlew test` if you want to test (it
will also compile things if needed). The `test` task is for unit tests. Each subproject also supports integration tests.
with the `itest` task. An integration test is one that tests several components together but does not require a complete
end to end environment. You can run all integration tests by `./gradlew itest` or run a specific module's
integration tests by scoping it like `./gradlew hedera-node:itest`.

Each subproject also supports "hammer" tests. A hammer test is a unit test that "hammers" the code. These usually
take the form of pseudo-random tests that run for an extended period of time and attempt to use a component in as many
ways as possible it can be thought of as a type of fuzz test. These can be run for all subprojects with `./gradlew hammer`
or for a specific subproject.

Finally, end-to-end tests (defined in `test-clients`) can be run with `./gradlew eet`. These tests need a running
instance. You can start one in one terminal with `./gradlew run` and then execute the tests from a second terminal with
`./gradlew eet`. Or you can use JRS to start an instance, or use some existing environment like previewnet.

Finally, you can use `./gradlew run` to run the project. It will compile (but not test) if needed.

There is also `./gradlew clean` to clean everything up. This will also clean out any temp stuff created for the sake of
running (logs, etc.).
