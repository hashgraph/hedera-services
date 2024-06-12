[![Node: Build Application](https://github.com/hashgraph/hedera-services/actions/workflows/node-flow-build-application.yaml/badge.svg)](https://github.com/hashgraph/hedera-services/actions/workflows/node-flow-build-application.yaml)
[![Artifact Determinism](https://github.com/hashgraph/hedera-services/actions/workflows/flow-artifact-determinism.yaml/badge.svg)](https://github.com/hashgraph/hedera-services/actions/workflows/flow-artifact-determinism.yaml)
[![Node: Performance Tests](https://github.com/hashgraph/hedera-services/actions/workflows/flow-node-performance-tests.yaml/badge.svg)](https://github.com/hashgraph/hedera-services/actions/workflows/flow-node-performance-tests.yaml)

[![codecov](https://codecov.io/gh/hashgraph/hedera-services/graph/badge.svg?token=ZPMV8C93DV)](https://codecov.io/gh/hashgraph/hedera-services)
[![Latest Version](https://img.shields.io/github/v/tag/hashgraph/hedera-services?sort=semver&label=version)](README.md)
[![Made With](https://img.shields.io/badge/made_with-java-blue)](https://github.com/hashgraph/hedera-services/)
[![Development Branch](https://img.shields.io/badge/docs-quickstart-green.svg)](hedera-node/docs/gradle-quickstart.md)
[![License](https://img.shields.io/badge/license-apache2-blue.svg)](LICENSE)

# Hedera Services

Implementation of the Platform and the [services offered](https://github.com/hashgraph/hedera-protobufs) by
nodes in the [Hedera public network](https://hedera.com).

## Overview of child modules
* _platform-sdk/_ - the basic Platform.
* _hedera-node/_ - implementation of Hedera services on the Platform.

## JVM
An [Eclipse Adoptium](https://adoptium.net/) build of the Java 21 JDK is required. If an Adoptium JDK is not installed,
the Gradle build will download an appropriate Adoptium JDK. The JDK version used to execute Gradle must be Java 21+ in
order for the `checkAllModuleInfo` task to succeed.

## Solidity
Hedera Contracts support `pragma solidity <=0.8.9`.

## Developer IntelliJ quickstart

The [IntelliJ quickstart](hedera-node/docs/intellij-quickstart.md) covers how to
start a local network of Services nodes from IntelliJ for testing and
development.

## Support

If you have a question on how to use the product, please see our
[support guide](https://github.com/hashgraph/.github/blob/main/SUPPORT.md).

## Contributing

Contributions are welcome. Please see the [contributing guide](https://github.com/hashgraph/.github/blob/main/CONTRIBUTING.md) to see how you can get involved.

## Code of Conduct

This project is governed by the [Contributor Covenant Code of Conduct](https://github.com/hashgraph/.github/blob/main/CODE_OF_CONDUCT.md). By participating, you are
expected to uphold this code of conduct.

## License

[Apache License 2.0](LICENSE)
