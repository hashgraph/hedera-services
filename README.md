[![Continuous Build](https://github.com/hashgraph/hedera-services/actions/workflows/continuous.yml/badge.svg)](.github/workflows/continuous.yml)
[![codecov](https://codecov.io/github/hashgraph/hedera-services/coverage.svg?branch=master&token=ZPMV8C93DV)](README.md)
[![Latest Version](https://img.shields.io/github/v/tag/hashgraph/hedera-services?sort=semver&label=version)](README.md)
[![Made With](https://img.shields.io/badge/made_with-java-blue)](https://github.com/hashgraph/hedera-services/)
[![Development Branch](https://img.shields.io/badge/docs-quickstart-green.svg)](docs/gradle-quickstart.md)
[![License](https://img.shields.io/badge/license-apache2-blue.svg)](LICENSE)

# Hedera Services

Implementation of the [services offered](https://github.com/hashgraph/hedera-protobufs) by
nodes in the Hedera public network, which is built on the Platform.

## Overview of child modules
* _hedera-node/_ - implementation of Hedera services on the Platform.
* _test-clients/_ - clients and frameworks for end-to-end testing of Services.
* _hapi-fees/_ - libraries to estimate resource usage of Services operations.
* _hapi-utils/_ - deprecated libraries primarily involved in fee calculation.

## JVM
OpenJDK 17 is strongly recommended.

## Solidity
Hedera Contracts support `pragma solidity <=0.8.9`.

## Docker Compose quickstart

The [Docker quickstart](docs/docker-quickstart.md) covers how to
start a local network of Hedera Services nodes using Docker Compose.

## Developer IntelliJ quickstart

The [IntelliJ quickstart](docs/intellij-quickstart.md) covers how to
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
