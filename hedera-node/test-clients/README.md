# Testing Hedera

These tests specify the behavior of a Hedera network with the services approved for implementation
by the governing council.

They are primarily black box tests that interact with the consensus node public API, including its gRPC
services and block stream. White box testing is also crucial, utilizing an **embedded mode** that allows
test code to directly access the node's internal state and file system, manipulate virtual time, and
submit transactions that would otherwise be rejected at ingest.

Developers write tests as instances of the `HapiSpec` Java class. Although it is possible to run a `HapiSpec`
directly against a remote network, this is uncommon. Typically, developers execute `HapiSpec`s as dynamic
tests on the JUnit platform using JUnit Jupiter, against a four-node network whose lifetime is scoped to
the JUnit `LauncherSession`. A `@HapiTest` meta-annotation marks these test methods, as we need to
register the Jupiter extensions that manage the test network lifecycle and make it the target of each
`HapiSpec`.

The default Gradle `test` task signals these extensions to create the target network by spawning four
child subprocesses as nodes. Meanwhile, the `testEmbedded` and `testRepeatable` tasks signal the
extensions to embed a single `Hedera` instance in the test process, invoking its workflows directly.
In embedded mode, there is no hashgraph consensus because the `Platform` is replaced with a simple mock,
and the `HederaState` implementation uses in-memory data structures instead of a Merkle tree.

**Table of contents**

1. [Structure of a `HapiSpec`](#structure-of-a-hapispec)
2. [`HederaNetwork` implementations](#hederanetwork-implementations)
3. [Style guide](#style-guide)
4. [JUnit Jupiter extensions](#junit-jupiter-extensions)
5. [Comparison to other tests](#comparison-to-other-tests)

## Structure of a `HapiSpec`

A `HapiSpec` groups a sequence of `SpecOperation`'s and provides them shared infrastructure and context.
When the spec is executed against a target `HederaNetwork`, it runs each operation in sequence.

Most operations are subclasses of `HapiSpecOperation`, which provides implementation support for submitting
transactions and sending queries to the target network. The below diagram is a schematic of a `HapiSpec`.

![HapiSpec schematic](./docs/assets/hapispec-schematic.png)

Operations share context through their spec's `HapiSpecRegistry`. The registry maps from `String` names to
arbitrary values; which are themselves usually references to entities on the target network. For example,
after a `HapiCryptoCreate` operation submits a transaction that creates a new account, it stores the new
account's id (say, `AccountID[accountNum=1001]`) in the registry with the name given by the test author
(say, `"Alice"`). The test author can then write a following `HapiCryptoUpdate` operation that references
the account by its stable name, and not worry that the next time the spec runs the created account might
actually have a different id on the target network.

Besides providing the context of its registry and target network, a `HapiSpec` also supports its operations
with some useful infrastructure components. The four most important components are,
1. A `TxnFactory` that helps with constructing valid `TransactionBody` and `TransactionID` messages.
2. A `KeyFactory` for generating and signing with cryptographic keys.
3. A `FeeAndRatesProvider` with up-to-date fee schedules and exchange rates for the target network.
4. A `HapiSpecSetup` with properties used to configure the spec; most important of these are the
   id and private key of the spec's default payer account.

## `HederaNetwork` implementations

There are four implementations of the `HederaNetwork` interface, as follows:
1. `RemoteNetwork` - a proxy to a remote Hedera network, supporting only gRPC access to the nodes without
    access to their block streams, internal state, or file system.
2. `SubProcessNetwork` - a managed network of four child processes, each running a Hedera node. This
    implementation supports starting and stopping nodes, and provides access to each node's block stream,
    logs, and upgrade artifacts. However, it does not support direct access to working state.
3. `ConcurrentEmbeddedNetwork` - a simulated network that instantiates a single `Hedera` instance
   directly in the test process. The internal state is a `FakeHederaState` implementation whose data
   sources are collections from the `java.util.concurrent` package. This allows direct access to, and
   modification of, the network's state.
4. `RepeatableEmbeddedNetwork` - an embedded variant automatically selected when running a
   `HapiSpec` in repeatable mode. This implementation requires single-threaded test execution and uses
   virtual time to ensure the same consensus times every test run.

Examining the differences between these implementations as we move from black box testing to increasingly
white box testing provides valuable insights into the key architectural elements of a Hedera network.
Let’s take a closer look at each mode in more detail.

### The `RemoteNetwork`

A `HapiSpec` executing against a `RemoteNetwork` behaves like any other client of that network. It sends
transactions and queries to the network’s gRPC services and receives responses as protobuf messages.
While the `RemoteNetwork` provides the most realistic testing environment for a spec, it is also the
most limited in terms of what the test can observe and manipulate. Network latency also makes it the
slowest way to execute a spec.

![RemoteNetwork view](./docs/assets/remote-network.png)

From this perspective, we see the centrality of the gRPC services to the Hedera network, but little
else. By moving to a subprocess network, we get a more complete picture of a consensus node's operation.

### The `SubProcessNetwork`

When a `HapiSpec` executes as a `@HapiTest` in a JUnit test executor, the default network is a
`SubProcessNetwork`. This network starts four child processes of the test executor process, each running
a Hedera node. Gossip and consensus happen over the loopback interface, as does gRPC communication
between the `HapiSpec` and its target network.

![SubProcessNetwork view](./docs/assets/subprocess-network.png)

A huge difference is the test now has complete visibility into the node's logs, block stream, and upgrade
artifacts.
