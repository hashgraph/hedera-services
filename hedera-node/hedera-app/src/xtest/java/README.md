# XTests

## Introduction
XTests are intended to fill the gap between unit tests and integration tests. 
They are designed to test the functionality of a services module in its entirety, as well as 
interactions between module.

## Goals and Benefits
- Easier to write, read and understand, and to debug into than the HAPI suite BDDs because they just exercise the service (or services directly) through its API instead of being an end-to-end test where a HAPI transaction goes in and a record stream comes out, with all kinds of concurrency and asynchronicity happening in between, and the measurement of the effect being rather indirect.
- At the same time making it easier to test edge cases because you can hand-craft test data using the actual data structures taken by the API, and can thus drive the system into states it can't normally get to by using only valid HAPI transactions.
- A more complete test of the service-under-test than (our current) unit tests because mocking is extremely discouraged. So you're actually testing the real code instead of hard-wiring in to the test a bunch of assumptions of how the real code is supposed to work.
- And yet they should have the flavor of a unit test: small, tightly focused to one specific issue and in fact one specific edge case (and thus compartmentalized from other tests testing in the same area)
- xTests are ideally suited for testing and understanding inter module interactions such as dispatching transactions from smart contracts service to token service
- xTests executes more quickly and thus allows the developer to iterate between test and functionality more efficiently than integration tests

## When xTests Are Not Appropriate

While xTests offer benefits as described above, they are not appropriate for all testing scenarios.
Some specific scenarios where xTests are not appropriate include:

- Any test that require multiple nodes to be running
- Tests that require complex signing/signature verification
- Any test where the existing scaffolding infrastructure needs to be significantly modified (although this will likely become more flexible over time)

## Mocking

xTests support mocking of objects just as in any unit test however in general mocking is discouraged unless the mocked object has no bearing on the functionality under test.  One objective to xTests is to understand potential side effects of tested functionality which may be hidden by excessive mocking. 

## xTest Structure and Examples

Just as during regular execution of nodes, xTests depend on dagger to provide the necessary dependencies such as state, context, fees and configuration.
The classes that provide these basic scaffolding dependencies are 
[BaseScaffoldingComponent](https://github.com/hashgraph/hedera-services/blob/develop/hedera-node/hedera-app/src/xtest/java/common/BaseScaffoldingComponent.java) and [BaseScaffoldingModule](https://github.com/hashgraph/hedera-services/blob/develop/hedera-node/hedera-app/src/xtest/java/common/BaseScaffoldingModule.java).

There is a base class for all xTests called `AbstractXTest` which provide basic, common and useful functionality.  In particular the [scenarioPasses()](https://github.com/hashgraph/hedera-services/blob/develop/hedera-node/hedera-app/src/xtest/java/common/AbstractXTest.java#L119)
method provides the structure for a test execution - from setup to scenario execution to assertions.

Currently, xTests have been written for the smart contract service (primarily centered around system contracts) and the token service.
All xTests for the smart contract service should descend from [AbstractContractXTest](https://github.com/hashgraph/hedera-services/blob/develop/hedera-node/hedera-app/src/xtest/java/contract/AbstractContractXTest.java)
which provides common functionality such as calling smart contract handlers with synthetic transactions and calling system contract functions.
Several prototypical examples to illustrate common xTest patterns are 
[AssociationsXTest](https://github.com/hashgraph/hedera-services/blob/develop/hedera-node/hedera-app/src/xtest/java/contract/AssociationsXTest.java), 
[ClassicViewsXTest](https://github.com/hashgraph/hedera-services/blob/develop/hedera-node/hedera-app/src/xtest/java/contract/ClassicViewsXTest.java)
and [HtsErc20TransfersXTest](https://github.com/hashgraph/hedera-services/blob/develop/hedera-node/hedera-app/src/xtest/java/contract/HtsErc20TransfersXTest.java)

Generally, xTests follow the pattern of setting up initial structures (accounts, tokens, aliases, tokenRels etc.), executing a scenario and then asserting the results. 
The scenarios are defined by overriding the `doScenarioOperations()` method in the xTest class.  