# Internal structure of a module

**INFORMATION:** This document is a draft based on our current ideas. The document might change over time. Next to this
the described architecture might not be reflected directly in the code. Once the service architecture has been finalized
the description inn this documentation must fit to the code.

Since our project is using Gradle as build tool all our modules are Gradle modules. Next to this mostly all modules are
Java modules.

## Gradle module description

Each module needs a `build.gradle.kts` file that describes the module.

General best practices for all our (Java) modules are defined in custom plugins that can be found
under `buildSrc/src/main/kotlin`. For a Java module the `com.hedera.hashgraph.javaConventions` plugin should be used.
Next to this each module should have a description. Since nothing else is needed for a minimal module the most simple
`build.gradle.kts` looks like this:

```
plugins {
id("com.hedera.hashgraph.javaConventions")
}

description = "A minimal module without any dependecies"
```

The `group`, `name` and `version` of the module should not be added here. The `group` is the same for all modules on the
repository (`com.hedera.hashgraph`) and its definition can be found in the `com.hedera.hashgraph.conventions` plugin.
For the `version` we use a global definition, too. The current `version` is defined in the `gradle.properties` file in
the root folder of the project. The `name` of a module is simple created based on the folder name of the module.

## Project sources set

For a Java module all sources must be placed under `src/main/java` and `src/main/resources` while `src/main/java` must
contain all the Java files that should be compiled. All other files must be placed under `src/main/resources`. Since all
our Java based modules are full JPMS modules a `module-info.java` file must be placed directly under `src/main/java`.

Each JPMS module has a unique name that start with `com.hedera.node.app.` and should reflect the usecase of the module.
If you have a module for the service foo the JPMS name might be `com.hedera.node.app.services.foo`. The name of the
module always defines the root package of the module. In the given sample the root package will
be `com.hedera.node.app.services.foo`. Based on that definition no Java class should be placed outside from that
package (outside from `src/main/java/com/hedera/node/app/services/foo`). Resources can be placed directly
under `src/main/resources` if it makes sense. In general it is best practice to place resources that do not need a
specific path below the `src/main/resources/com/hedera/node/app/services/foo` folder, too.

## Tests

All modules can have different types of tests. The `com.hedera.hashgraph.javaConventions` plugin provides direct support
for unit test, integration tests, and end-to-end tests. Next to this the test fixtures functionality of Gradle is
supported.

### Test fixtures

To create clean and readable unit tests it is best practice to provide common functionality for tests in the test
fixtures of a module. Based on the `com.hedera.hashgraph.javaConventions` plugin test fixtures are supported for all
Java modules.

All Java sources for the test fixtures must be placed under `src/testFixtures/java`. Additional resources that should be
shared for tests can be placed under `src/testFixtures/resources`.

Like for the source set of a project the test fixtures sets are defined as full JPMS modules, too. Based on that
a `module-info.java` file must be placed directly under `src/testFixtures/java` if at least one Java file is present.
The name of the test fixtures module should be based on the name of the source module and add a `testfixtures` suffix.
For the given sample of the foo service the name and the base package for the test fixtures set would
be `com.hedera.node.app.services.foo.testfixtures`. Since the test fixtures JPMS module will only be used in tests it
should be fully opened. This makes the content of the `module-info.java` much more readable since no individual `opens`
statements need to be added. The complete module will be opened by adding the `open` keyword directly to the module
definition:

```
open module com.hedera.node.app.services.foo.testfixtures {
    //...
}
```

**Hint:** Similar to any other module the test fixtures set of a module can be added as a dependendency by using the
correct Gradle syntax like in this sample: `testImplementation(testFixtures(project(":foo-service"))) `

### Unit tests

For the unit test set the `src/test/java` and `src/test/resources` folders must be used. Unit tests will be executed on
the Java classpath and therefore no `module-info.java` is needed. This has the big benefit that unit tests can have the
same base package as the module sources. By doing so unit tests can access package private classes, constructors, ...

### Integration tests

For the integration test set the `src/itest/java` and `src/itest/resources` folders must be used. All integration tests
will be executed on the module path to be as near to the real usage as possible. Based on that a `module-info.java` file
is needed. Like for test fixtures the name for the module and the base package is based on the name of the source module
plus the 'itest' suffix. For the given sample the JPMS module name for the integration tests would
be `com.hedera.node.app.services.foo.itest`. Since the test fixtures JPMS module will only be used in tests it should be
fully opened. This makes the content of the `module-info.java` much more readable since no individual `opens`
statements need to be added. The complete module will be opened by adding the `open` keyword directly to the module
definition:

```
open com.hedera.node.app.services.foo.itest {
    //...
}
```

### End-to-end tests

For the end-to-end test set the `src/eet/java` and `src/eet/resources` folders must be used.

## JMH benchmarks

Next to tests a module can have several benchmarks to test the performance of critical components within the module. All
benchmarks must be based on JMH and the `src/jmh/java` and `src/jmh/resources` folders must be used.

## Basic module template

Based on the given definitions a module folder in the project looks like this:

```
 foo-service/
 ├── src/main/java/
 │   ├── com.hedera.node.app.service.foo
 │   │   ├── FooService.java
 │   │   └── package-info.java
 │   └── module-info.java
 ├── src/main/resources/
 │   ├── com.hedera.node.app.service.foo
 │   │   └── some_data.json
 │   └── logging.properties
 ├── src/testFixtures/java/
 │   ├── com.hedera.node.app.service.foo.testfixtures
 │   │   └── FooServiceTestConfig.java
 │   └── module-info.java
 ├── src/test/java/
 │   └── com.hedera.node.app.service.foo
 │       └── FooServiceTest.java
 ├── src/itest/java/
 │   ├── com.hedera.node.app.service.foo.itest
 │   │   └── FooServiceITest.java
 │   └── module-info.java
 └── build.gradle.kts
 ```

### Open questions:

The following questions are currently not answered by the doc

- Should it be `src/eet/java` or `src/eetest/java` or `src/e2e/java`?
- Should e-2-e tests run on the classpath or the module path?
- Should benchmarks run on the classpath or the module path?





