[![CircleCI](https://circleci.com/gh/hashgraph/hedera-services/tree/master.svg?style=shield&circle-token=6628b37c62b2e1f8f7bf1274bee204dc9bc9292b)](https://circleci.com/gh/hashgraph/hedera-services/tree/master)
[![codecov](https://codecov.io/github/hashgraph/hedera-services/coverage.svg?branch=master&token=ZPMV8C93DV)](https://codecov.io/gh/hashgraph/hedera-services)

# Hedera Services 

Implementation of the [services offered](https://github.com/hashgraph/hedera-protobufs) by 
nodes in the Hedera public network, which is built on the Platform.

## Overview of child modules
* _hedera-node/_ - implementation of Hedera services on the Platform.
* _test-clients/_ - clients and frameworks for end-to-end testing of Services.
* _hapi-fees/_ - libraries to estimate resource usage of Services operations.
* _hapi-utils/_ - deprecated libraries primarily involved in fee calculation.

## JVM
OpenJDK12 is strongly recommended.

## Solidity 
Hedera Contracts support `pragma solidity <=0.5.9`.

## Docker Compose quickstart 

The [Docker quickstart](docs/docker-quickstart.md) covers how to 
start a local network of Hedera Services nodes using Docker Compose.

## Developer IntelliJ quickstart 

The [IntelliJ quickstart](docs/intellij-quickstart.md) covers how to 
start a local network of Hedera Services nodes from IntelliJ for
testing and development.
