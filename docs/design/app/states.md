# State

In Hedera, data that is stored "in state" is identical across all nodes in the cluster. The state is used to compute a
total state hash that is gossiped between all nodes. If two nodes have different state hashes, they are said to have
"Inconsistent State Signatures", or an ISS. The state is also used for "reconnect", where a node may have different
state from other nodes (maybe it was down for a while, or is new to join the network) and the system can synchronize
state between nodes.

The state of the system is, ultimately, stored in a Merkle tree using an API defined by the hashgraph platform. However,
this implementation reality is not a detail that any of the service implementations need to be aware of! Instead, all
state stored in the system can ultimately be broken down into simple key/value data structures. Indeed, the hashgraph
platform makes this easy with an API for in-memory k/v storage (MerkleMap) and on-disk k/v storage (VirtualMap).

The `com.hedera.node.app.spi.state` package contains APIs for a service module to interact with state. It was our
objective to limit, as much as possible, the information the service module has about the actual underlying merkle
tree. This was done for two reasons. First, access to the full merkle tree allows a service to "dig around" in the tree
and learn about other state and possibly depend on other state, outside its own *namespace*. For example, it would be
awful for service "A" to find and depend upon state of service "B" by going through the merkle tree itself. The proper
way to do this is to use the public API available on service "B". The second reason we try to abstract the details of
the underlying merkle implementation is to give us a greater degree of flexibility for re-entrant cases like when the
`hedera-smart-contract-service` uses a system contract to make a call to another service like `hedera-token-service`.

The final and strongest reason for introducing the classes in the `state` package is that it solves several of the
more challenging problems in the services code base: deciding which copy of the merkle tree state to use, buffering
all modifications to the merkle tree, and keeping track of all state read from the merkle tree. Let's go through each
of these different core use cases and describe how the classes in `state` help with them.

## Merkle Trees

All persistent state is stored in a "merkle tree". A merkle tree is simply a tree structure used to reduce the amount
of work needed to produce a "root hash" of all state in the tree (the tree caches the hashes of each node in the tree,
so when a single leaf changes, we only have to compute the new hash of the leaf and the hash of each parent node in
the tree to compute the new root hash). Performance can be improved by recomputing the root hash for multiple
modifications: that is, re-hashing the tree after every modification is slower than re-hashing every N modifications,
where N is a reasonably large value. We have found that when N is 10,000, it is faster than when N is 1.

We also need to periodically compare "root hashes" of the state with all nodes in the network, to make sure that
all nodes have matching state. Divergent state is a **very bad thing**, because it could mean that in the database
of one machine you have an hbar balance of X, while on another machine you have an hbar balance of Y! Differences in
the "state signature" of different machines is known as an ISS (inconsistent state signature).

The hashgraph algorithm works on `Event`s, sorting them and bundling them together into `Round`s. Each `Event` has
zero or more `SwirldTransaction`s. As each `Round` is produced, it is sent to the Hedera application to be handled.
On `Round` boundaries, the platform makes a "fast copy" of the merkle tree. A "fast copy" allows the application to
continue processing the next `Round`, while the `Round` that was just completed is hashed, signed, and gossiped with
all other nodes to compare signatures. Since hashing, signing, and gossiping takes time, this is done asynchronously.
Most of the tree is treated as copy-on-write for a fast-copy, but some parts of the tree are deep-copied.

At any given time, we may have a few 10's of "fast copies" in memory. The most recent is the "working" or "mutable"
state. This is the copy of the tree used by the handle-transaction thread to which all modifications are made.
All other copies are immutable, and will throw exceptions if you attempt to modify them (there is one
modification to the tree permitted after the fast copy is made, and that is to apply the hashes to the nodes in
the tree during the hash phase).

The next-most recent version of the tree is the "latest immutable state". This is the state used for answering queries
made by customers of the consensus nodes. It is also the state used in `pre-handle` (more on this later) for preparing
asynchronously, and ahead of time, transactions for processing.

Another older state known as the "latest signed state" is the one currently being gossiped to all other nodes in the
network. And finally, if the node is helping to "reconnect" another node, it will keep a copy of state in memory from
which it "teaches" the other node about the state.

While the "merkle tree" is a complex data structure, the service implementations actually don't need to know
or care about it at all. The merkle tree is an implementation detail of the application, not of the services. If each
service had to know about all these different states and which state it should use for which code path, the code would
become quite complex and prone to mistakes! Instead, we let the Hedera application be responsible for understanding this
complexity. Each service needs only a simple key-value store for storing data. So we created the `ReadableState` and
`WritableState` classes as simple key-value store APIs which services can work with, and let the Hedera application 
implement these interfaces over the right version of the merkle tree for the right calls. From the service's
perspective, it doesn't know anything about any of these `Round`s or `Event`s or `SwirldTransaction`s or fast copies or
mutable and immutable states. It is simply given, by the application, the `ReadableState` to use for reading or the
`WritableState` for writing.

## States

There are two "state" interfaces: `ReadableState` and `WritableState`. `WritableState` extends from `ReadableState`,
so a `WritableState` is read/write. Each service implementation has one or more `MerkleMap`s or `VirtualMap`s holding
the data for that service. These are wrapped by the platform and made available to the service as a `WritableState` or
`ReadableState`.

Each `State` is scoped to a particular service implementation. They are never shared across service implementations.
In fact, the `State` of a service **MUST NOT** be exposed as public API by any service API. The state of a service
is strictly an implementation detail of the service.

From the `ReadableState` a service implementation can:
 - `get` values from the k/v store
 - check whether a given key is contained by the state

From the `WritableState` a service implementation can perform all tasks on a `ReadableState`, plus:
 - `getForModify` to read the value for the purpose of subsequently modifying it (there is an optimized code
   path for this case)
 - `put` a new value
 - `remove` a value
 - get keys of all modified values with `modifiedKeys`

The application module has access to additional implementation methods on states, such as the ability to get a set
of all keys that have been queried, or specify the consensus time at which different states were modified.

All states act as a near cache for looked up values. Since many (if not most) values are stored on disk with a
`VirtualMap`, performance is positively impacted by caching looked up values locally in the state.

### States used with Queries

The HAPI supports a number of queries. Queries are always answered either from **the latest immutable state** or the
**last signed state** (if a state proof is requested). Query logic only needs a read-only view of the state, and so use
`ReadableState`. It is not important to keep track of the different keys queries use. When the
[Query Workflow](workflows.md#query-workflow) is run, it lazily determines whether a `QueryHandler` has already been
created for the current query thread, and whether the latest immutable state matches the state used with that query
handler. If so, the handler is reused. If both conditions are not true, then a new `QueryHandler` is created, using the
`ReadableState`s for the service module (as registered with the `StateRegistry`) based on the latest immutable state.

### States used during Ingestion

When clients send a transaction to a node, the node must perform some pre-check logic before sending the transaction
to the hashgraph platform for consensus. The pre-check logic needs access to **the latest immutable state**. It never
performs any modification to the state. The [Ingest Workflow](workflows.md#ingest-workflow) needs access to key
information on the transaction payer, and uses a `ReadableState` to get this information, by delegating to the
token service.

### States used during Pre-Handle

Before a transaction comes to consensus, the platform gives the application an opportunity to do some pre-work in
the background. This pre-transaction work is always done using **the latest immutable state**. Doing pre-work is
perilous, because if care is not taken, it can lead to an ISS! Since this possibility motivates some additional
complexity in the design, it is worth a brief discussion on how an ISS here can happen.

Suppose I have two nodes in the network, Carol and Bob. Each is receiving a steady stream of transactions and gossiping
between themselves and all other nodes in the network. At some point, a `Round` of transactions comes to consensus and
each node is able to process those transactions in order, updating their state accordingly. While that `Round`'s
transactions are being processed on the _handle transaction_ thread, many other threads in the background are doing
pre-handle work on transactions which have not yet come to consensus.

Suppose I have some account A with a balance of 10 hbars. In the current `Round` being handled by the _handle
transaction_ thread there is a transaction which will change the balance to 100 hbars. Suppose a transaction in
pre-handle would modify the balance of A by subtracting 50 hbars. Due to the racy nature of the _handle transaction_
thread and the multiple pre-transaction worker threads, it may be that on Carol the **latest signed state** is different
from that on Bob. Perhaps the _handle transaction_ thread has already adjusted the balance to 100hbars for account A and
completed that round by the time Carol sees this new transaction that wants to subtract 50 hbars. When Carol sees this,
she concludes that subtracting 50 hbar is a perfectly fine thing to do (since 100 - 50 > 0), and marks the transaction
as good. It may be that Bob's **latest signed state** still shows an account balance of 10 for A, because it hasn't
finished running the transaction that would change it to 100. So when Bob pre-handles the transaction to decrement by
50 hbars, he may reject the transaction because 10 - 50 < 0! Carol and Bob thus have divergent state. This would be
very bad. We could give up on concurrency entirely, but this would be very bad for performance.

Instead, we can see that the underlying problem is that during pre-handle a node may see some state S0, which may or
may not get modified in a racy way with the _handle transaction_ thread to become S1. So what we need to do is
remember that in pre-handle we accessed the state S0, and when we get to handling that transaction on the
_handle transaction_ thread (perhaps some time later), we need to verify that the value associated with that state
is still S0. If it has become S1, then we need to throw away whatever we computed in pre-handle, and recompute it
again, this time with the **current working state**.

Therefore, when the [Pre-handle Workflow](workflows.md#pre-handle-workflow) processes a transaction, it **must** use a
`ReadableState` that tracks all state that was accessed, and remember the set of state that was accessed.When the
transaction is handled on the _handle transaction_ thread, we will check whether any state accessed during pre-handle
has been modified since it was accessed. If so, we throw away any result from pre-handle and run again, this time
serially on the _handle transaction_ thread on with the **current working state**. If no state used during pre-handle
has been modified, then we can trust the pre-work we did and complete the transaction.

### States used during Transaction Handling

When a state is used during the [Transaction Handling Workflow](workflows.md#handling-transactions), it is always
a `WritableState` based on the **current working state**. The only workflow that supports writable state is this one.
At the end of the `Round`, after **all** transactions in the round have been handled, the state is committed to the
merkle tree.

## State Registry

When a Service instance is created, it is given a `StateRegistry`. Through this interface, the service is able to manage
its `State`s. It can create and register new `State`s, and decide whether that state should reside in memory, or on
disk. It can also migrate state, or even delete state. The `StateRegistry` given to the service instances is scoped to
that service. It is not possible for one service to look up `State` for another service through the `StateRegistry`.
Each `State` is referenced by a "state key", which is likewise scoped to the `StateRegistry`. Two different service
instances could use the same "state key" without naming collisions. Each `StateRegistry` is thus a unique namespace.

The following code is an example of a service which makes use of the `StateRegistry` to create a new `WritableState`. It
makes use of the `StateDefinition` to do so.

```java
public class MyService implements Service {
    public MyService(final StateRegistry registry) {
        // This call will either _get_ the state associated with MY_STATE, or it will _register_ the new state
        final var myState = registry.registerOrMigrate(
                "MY_STATE",
                (builder, existingStateOpt) -> {
                    if (existingStateOpt.isEmpty()) {
                        // This is a genesis condition -- the state does not already exist
                        return builder.inMemory("MY_STATE")
                                .define();
                    } else {
                        // Not genesis. If I needed to do a migration, I could do it here. For example, I could
                        // move from in-memory to on-disk by creating a new on-disk version and moving data over,
                        // or I could walk through my state and make necessary modifications.
                        return existingStateOpt.get();
                    }
                });
    }
}
```

The `StateRegistry`, `StateRegistryCallback`, and `StateDefinition` are used together to create and manage the state for
the service. A service may have multiple states. Maybe the `hedera-token-service` has a state for `Account` entities,
and another for `Token`s, and another for `AccountNft`s, and so on.

**NEXT: [Throttles](throttles.md)**
