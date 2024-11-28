# Platform Base

Platform base is a set of modules that belong to the base team and are used everywhere within the project.

## Goals

The modules that belong to Platform Base must fulfill the following requirements:

- They must provide an easy to understand API
- They must provide documentation for all public interfaces, classes, methods
- They must provide documentation for each package (`package-info.java`)
- Each module must be a Java module
- Today all features must be supported on the modulepath and the classpath (might be modulepath only in future).
- If the project provide test fixtures, they must be provided as a module as well.
- The code must be well tested
- The code must be documented by `@NonNull`, `@Nullable` annotations
- Define a concrete public API and hide as much implementation as possible in private (not exported) code.

## Modules

The following modules belong to the scope of Platform Base:

- swirlds-base (`com.swirlds.base`)
- swirlds-config-api (`com.swirlds.config.api`)
- swirlds-logging (`com.swirlds.logging`)
- swirlds-metrics-api (`com.swirlds.metrics.api`)
- swirlds-common (`com.swirlds.common`)
- swirlds-config-impl (`com.swirlds.config.impl`)
- swirlds-config-benchmark (`com.swirlds.config.impl`)
- swirlds-config-extensions (`com.swirlds.config.extensions`)
- swirlds-config-processor (`com.swirlds.config.processor`)
- swirlds-logging-log4j-appender (`com.swirlds.logging.log4j.appender`)

Additional modules will follow in future since we plan to split swirlds-common into multiple modules.

## Structure and best practices in the rest of the repository

Platform Base works to ensure that all modules within its scope our responsibility are well designed and serve as examples of best practice.
To this end we define patterns and recommended practices, create well defined issues, and conduct high quality pull request review.
All modules in Platform Base declare the absolute minimum external dependencies so that modules are more widely usable.
Where necessary Platform Base will create issues and submit pull requests to improve design throughout the entire system, and to continually minimize external dependencies.

## Documentations

One part of the work on Platform Base is to provide documentation for all modules / features.
The documentation is provided as markdown files that can be found here:

- [Service architecture](./service-architecture/service-architecture.md)
- [Configuration](./configuration/configuration.md)
- [Context](./context/context.md)
- [Metrics](./metrics/metrics.md)
- [Base executor](./base-executor/base-executor.md)
- [Test Support](./test-support/test-support.md)
