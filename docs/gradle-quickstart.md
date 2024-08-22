# Gradle quickstart

## Installation

The repo contains a `gradlew` (or `gradlew.bat` on windows) script. This script will automatically
download the right version of Gradle for this project, scoped to this project. This means you never
need to have Gradle installed manually on your computer. It also means, as the project updates to
newer versions of Gradle, you will pick them up automatically. And, the version of Gradle is checked
in, meaning you will always have the right version for whatever commit you are building from.

The only requirement to run Gradle is having a recent JDK installed. In order to ensure reproducible
builds, this project is configured to check the JDK you are currently using and fail if it does not
correspond to the JDK you are currently using. If you get an error, please download the indicated
JDK and make sure the `java` command on your `PATH` is of that JDK or that your `JAVA_HOME` is
pointing at that JDK.

## Building the project

This documents explains how to use Gradle directly from the command line via the `./gradlew <task>`
command. All Gradle tasks can also be invoked from the Gradle view in
[IntelliJ IDEA](intellij-quickstart.md).

There are several Gradle tasks you can use. Most notably:

- `./gradlew assemble` compile all code and create all Jar files
- `./gradlew qualityGate` in addition to the above, run all quality checks and auto-fix formatting
  where possible
- `./gradlew :<module-name>:<test-type>` run all tests in one module of the given
  [test type](#Testing).

You may run `./gradlew` (without arguments) for a detailed overview

## Running a services instance or example apps

- `./gradlew :app:modrun` runs a services instance
- `./gradlew :test-clients:runTestClient -PtestClient=com.hedera.services.bdd.suites.crypto.HelloWorldSpec`
- `./gradlew :swirlds-platform-base-example:run` runs Platform-base Example App

## Using Gradle during Development

### Changing or adding Modules of Hedera (aka Gradle Subprojects)

All modules are listed in [settings.gradle.kts](../settings.gradle.kts) using
`include(":<module-name>", "<module-folder-path>")`. The `module-folder-path` should be a folder in
a subdirectory like [platform-sdk](../platform-sdk) or [hedera-node](../hedera-node). In the folder,
the following files are expected:

- `build.gradle.kts` specifies to which group of modules the module belongs, e.g.
  `id("com.hedera.gradle.services")` or `id("com.hedera.gradle.platform")` and may contain
  [dependency definitions](#changing-or-adding-dependencies) for tests.
- `src/main/java/module-info.java` is the Java Module specification that is also used to determine
  the [dependencies of the module](#changing-or-adding-dependencies) by Gradle. Note that the last
  segment of the module name defined in the `module-info.java` file needs to correspond to the
  name of the module defined in [settings.gradle.kts](../settings.gradle.kts).

### Changing or Adding Dependencies

This project use of the _Java Module System (JPMS)_. With this, dependencies between modules are
defined in the `src/main/java/module-info.java` files that each module contains. Other modules are
identified by their _Module Name_ there. For example, a dependency to the `swirlds-logging` module
is expressed by `requires com.swirlds.logging`. A dependency to the 3rd party library
`com.fasterxml.jackson.core` is expressed by `requires com.fasterxml.jackson.core`. Note: This
project utilizes the
[org.gradlex.java-module-dependencies](https://github.com/gradlex-org/java-module-dependencies)
plugin to achieve this integration between Gradle and the Java Module System.

Each dependency definition contains a scope – e.g. `requires` or `requires transitive`. If you are
unsure about a scope, use `requires` when adding a dependency. Then execute `./gradlew qualityGate`
which runs a dependency scope check that analysis the code to determine which Java types are visible
(and should be visible) to which modules. If the check fails, it will advise you how to change the
scope.

### Adding or Changing the Version of a 3rd party dependency

If you use a 3rd party module lke `com.fasterxml.jackson.core`, a version for that module needs to
be selected. For this, the
[hedera-dependency-versions/build.gradle.kts](../hedera-dependency-versions/build.gradle.kts)
defines a so-called _Gradle platform_ (also called BOM) that contains the versions of all 3rd party
modules used. If you want to upgrade the version of a module, do this here. Remember to run
`./gradlew qualityGate` after the change. If you need to use a new 3rd party module in a
`src/main/java/module-info.java` file, you need to add the version here. (If the new module is not
completely Java Module System compatible, you may also need to add
[patching rules](#patching-3rd-party-modules)).

### Patching 3rd Party Modules

Some 3rd party libraries we use are not yet fully Java Module System compatible. And some modules
pull in other dependencies that we can neglect. Situations like this are treated as wrong/incomplete
metadata in our Gradle setup and the file
[com.hedera.gradle.jpms-modules.gradle.kts](../gradle/plugins/src/main/kotlin/com.hedera.gradle.jpms-modules.gradle.kts)
contains the rules to adjust or extend the metadata of 3rd party libraries to address such problems.

Note: This project utilizes the
[org.gradlex.extra-java-module-info](https://github.com/gradlex-org/extra-java-module-info) and
[org.gradlex.jvm-dependency-conflict-resolution](https://gradlex.org/jvm-dependency-conflict-resolution/#resolution-plugin)
plugins to ease the definition of patching rules.

### Incrementing the Version of Hedera itself

Our Gradle build has a single version number for all modules. It is defined in
[version.txt](../version.txt). Changing this version number will automatically apply to every
module.

### Testing

We have different types of tests, defined in different folders – so-called _source sets_ – in each
module.

#### Unit Tests

- located in `src/main/test` of the corresponding module
- run with `./gradlew :<module-name>:test`

Unit tests will **always** be executed in PR builds and must pass before merging. The vast majority
of our tests should be unit tests (measured in the 10's of thousands). These tests are never flaky
and should avoid arbitrary waits and timeouts at all costs. The full body of unit tests should
execute in roughly 5 minutes.

#### Integration Tests

- located in `src/main/itest` of the corresponding module
- run with `./gradlew :<module-name>:itest`

We define integration tests as those that involve several components, but not an entire working
instance. These use JUnit. Integration tests take longer to execute than unit tests. These should be
the second most plentiful type of test. They are designed to ensure two or more components work
together. We recommend using [Testcontainers](https://www.testcontainers.org/) for databases, mirror
nodes, explorers, or other components that live in different repos. These tests should be written
carefully to avoid flakiness. If a test fails, it should **always** mean that there is a real
problem. Per module or subproject, integration tests should take no more than 10 minutes to execute.
Across the entire repo, there should be thousands of integration tests.

Integration tests must **all pass** before merging to **main**, so they must be fast and reliable.

#### Hammer Tests

- located in `src/main/hammer` of the corresponding module
- run with `./gradlew :<module-name>:hammer`

A hammer test is a unit test that "hammers" the code. A more common and less visceral name for this
type of test is a "fuzzing" test. These usually take the form of pseudo-random tests that run for an
extended period of time and attempt to use a component in as many ways as possible.

Hammer tests by their nature take longer to execute. These are run on a nightly basis. They have
concrete pass/fail behavior. If any hammer test fails, this should mean there is **definitely** a
bug that needs to be triaged.

#### Micro-benchmarks

- located in `src/main/jmh` of the corresponding module
- run with `./gradlew :<module-name>:jmh`

Micro-benchmarks are like the unit-tests of performance testing. They should be used liberally for
establishing metric-driven decisions about different designs. The specific numbers produced by a
microbenchmark are not themselves very useful because different hardware under different conditions
can give different numbers. But they are useful when comparing A/B implementations on the same
hardware. These tests also take a significant amount of time to execute, and are not very good at
giving pass/fail criteria after execution.

Rather, micro-benchmarks exist to help developers verify the impact of their changes in a particular
part of the system. Appropriate benchmarks should be run prior to creation of a PR. These are run
nightly, and we record the results, so we can do trend analysis over time.

We use the [Java Micro-benchmarking Harness](https://github.com/openjdk/jmh), or JMH, for writing
and executing our micro-benchmarks.

### Cleaning

Gradle projects put all build artifacts into `build` directories. To clean your workspace of all
these build artifacts, use `./gradlew clean`. Note: cleaning is not necessary to get correct built
results. You only need to do it if you want to free disc space.

## Changing details in the Gradle setup

Generally, Gradle is configured through so-called Gradle _convention plugins_. A convention plugin
is a plugin that applies a certain set of defaults to all builds that include that convention. We
define one such plugins in [gradle/plugins/src/main/kotlin](../gradle/plugins/src/main/kotlin) using
Gradle's Kotlin DSL notation. If you need to adjust something in the build itself, this is the
places where all configuration is located. For details, see comments in the existing convention
plugins (`*.gradle.kts` files).
