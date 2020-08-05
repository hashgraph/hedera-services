[![CircleCI](https://circleci.com/gh/hashgraph/hedera-services/tree/master.svg?style=shield&circle-token=6628b37c62b2e1f8f7bf1274bee204dc9bc9292b)](https://circleci.com/gh/hashgraph/hedera-services/tree/master)
[![codecov](https://codecov.io/github/hashgraph/hedera-services/coverage.svg?branch=master&token=ZPMV8C93DV)](https://codecov.io/gh/hashgraph/hedera-services)

# Hedera Services 

The child modules in this repository define and implement the API to the 
services offered by the nodes in the Hedera public network, which is built 
on the Swirlds Platform.

## Overview of child modules
* _hapi-proto/_ - protobuf defining the Hedera API.
* _hedera-node/_ - implementation of Hedera services on the Swirlds platform.
* _test-clients/_ - client libraries for end-to-end testing of the Hedera network.

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
