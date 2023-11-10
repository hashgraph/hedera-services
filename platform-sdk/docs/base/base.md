# Platform Base

Platform base is a set of modules that belong to the base team and are used everywhere within the project.

## Goals

The modules that belong to Platform Base must fulfill the following requirements:

- They must provide an easy to understand API
- They must provide documentation for all public interfaces, classes, methods...
- They must provide documentation for each package (`package-info.java`)
- Each module must be a Java module
- Today all features must be supported on the modulepath and the classpath (might be modulepath only in future).
- If the project provide test fixtures, they must be provided as a module as well.
- A goal is to have all unit tests defined as modules, too.
- The code must be well tested
- The code must be documented by `@NonNull`, `@Nullable` annotations
- Define a concrete public API and hide as much implementation as possible in private (not exported) code.

## Modules

The following modules belong to the scope of Platform Base:

- swirlds-base (`com.swirlds.base`)
- swirlds-config-api (`com.swirlds.config.api`)
- swirlds-logging (`com.swirlds.logging`)
- swirlds-common (`com.swirlds.common`)
- swirlds-config-impl (`com.swirlds.config.impl`)
- swirlds-config-benchmark (`com.swirlds.config.impl`)

Additional modules will follow in future since we plan to split swirlds-common into multiple modules.

## Structure and best practices in the rest of the repository

Platform Base defines a lot of pattern and best practices for the modules
The team sees it as a good idea to follow the same pattern in the rest of the repository.
Therefore, the Platform Base team tries to help all other colleagues to have a great structure and module definition in the complete repository.
Here the team creates issues and pull requests to help all the other teams to have a great structure as well.
Next to that the Platform Base team tries to remove as many external dependencies as possible to make it easier for all other teams to use the modules.
Here the team again creates issues and pull requests to remove external dependencies and replace them with internal modules in all modules of the repository.

## Documentations

One part of the work on Platform Base is to provide documentation for all modules / features.
The documentation is provided as markdown files that can be found here:

- [Configuration](./base/configuration/configuration.md)
- [Context](./base/context/context.md)
- [Metrics](./base/metrics/metrics.md)
- [Test Support](./base/test-support/test-support.md)