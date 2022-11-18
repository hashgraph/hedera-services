## Workflows

This package and subpackages contain the workflows that are implemented by the application. The other packages
contain libraries and utilities, but this package contains all the actual workflows. If you want to know how the
application implements pre-handle, or handle, or queries, or auto-renew and expiry, or transaction ingestion, then
you have found the right place.

**By design** the workflows are set up to be trivial to walk through and understand, and to align almost 1-1 with
drawn diagrams. They are designed to be easily testable in isolation of a full working system, including all error
conditions and other situations. A real implementation would have hundreds of tests of every conceivable input.

### Ingest Workflow

The `ingest` package contains the workflow utilized for submitting transactions to a consensus node. There is
quite a lot of documentation on the classes, and I hope they are still mostly accurate! You will notice that these
classes, like almost **all** classes in this code base, use *final* fields and constructors given all the required
args.

### Query Workflow

The `query` package contains the workflow for handling queries. I don't know how accurate this package is right now,
a lot has changed and I haven't looked at it recently.

### Pre-Handle Workflow

The `prehandle` package contains the workflow for pre-handling transactions. This hasn't been updated recently, so
take the implementation as a rough sketch. An `Event` at a time is sent to the `prehandle`, and then it iterates through
each transaction, possibly using multiple threads. Each transaction gets its own `TransactionMetadata` which ends up
being set on the `SwirldsTransaction` object. Later, when we do handle-transaction, we can access this metadata.

### Handle Workflow

The `handle` package is where the handle-transaction logic lives. This is also not fully fleshed out as of right now,
but has enough to give the general feeling for how it would work.

For each transaction, we create:
1. A new `FeeAccumulator` from a factory method supplied by the `Hedera` object when it created this workflow. We need
   a factory because we need a new instance each time, but want one associated with the current working state. Because
   the concrete implementation should not be created by this code directly, and because the fee schedule may change
   periodically based on some other transaction, we delegate to a factory to create these instances.
2. A new `RecordBuilderImpl`.
3. An `AtomicLong` of the `nextId` for entities. This is used to implement the lambda for `EntityIdGenerator`.
4. An internal helper class, `StatesAccessor`, which is basically a map of all `States` accessed during the transaction.
   This is critical, because during transaction handling we need to buffer up all changes in the `State` objects that
   are retrieved by `States`, so at the end if everything goes well, we can commit it all.
5. A new `HandleContext`

Now that we have a `HandleContext`, we will dispatch it to the appropriate service to handle. The service will use
the `FeeAccumulator` and `States` and other information to execute the transaction. If something goes wrong with the
transaction, it is expected to throw an exception. If no exception is thrown, then the transaction has completed
correctly.

The next step is to "finalize" the transaction fees, transfers, and rewards. A special method in
`CryptoTransactionHandler` called `finalizeTransfers` is handed the `FeeAccumulator` and a `TransferRecordBuilder`
(this is the same record builder we created at the start of the transaction, since among all other types, it
implements `TransferRecordBuilder`). The `CryptoTransactionHandler` we use has the same state as the handle transaction,
so if any accounts were touched, it will know about them.

The implementation of `finalizeTransfers` asks the `AccountStore` (and implementation detail of `hedera-token-service`)
for all `Account`s that were modified during the transaction. It has these, because the `AccountStore` is backed by
`States`, which buffer changes and have not yet been merged back into the merkle tree. Given this list of modified
`Accounts` and the `FeeAccumulator`, `finalizeTransfers` will compute all fees and rewards and modify all accounts
appropriately, and write all transfer details into the `TransferRecordBuilder`.

After this call completes, every change has been made that should be made. We can now flush all changes that have
been buffered up into the merkle tree, and submit the `TransactionRecord` to the `RecordStreamManager`.

Should anything go wrong along the way, we will take appropriate action. I do not have all those error paths stubbed
out yet. If the transaction handling fails, we still want to charge accounts in some ways and produce a record. If it
fails in other ways (for example, if we fail to update the merkle state or produce a transaction record or something
strange like that) then we have an unhandled error which can be fatal. Great care is taken in these code paths and
a great deal of testing to make sure they are rock solid.

# Handling Transactions

As consensus nodes receive new transactions, they bundle them up in an `Event` and gossip this event with the network,
so they may eventually come to consensus and each node may then handle the transactions in a fair, deterministic
order. Each node gossips events asynchronously and in parallel, with the hashgraph algorithm sorting them into fair
order. Once a `Round` of `Event`s is produced, nodes can begin processing those `Event`s and their transactions in
order.

Each `Service` implementation provides three "handlers": a `QueryHandler`, a `PreTransactionHandler`, and a
`TransactionHandler`. Each of these handlers define a concrete set of methods for handling different types of queries,
for handling different types of transactions. For example, `ConsensusPreTransactionHandler` (for the Hedera Consensus
Service) might have a method `TransactionMetadata preHandleCreateTopic(TransactionBody txn)` which is called to
pre-handle a "create topic" transaction. Likewise, the `ConsensusTransactionHandler` may have a method called
`handleCreateTopic` for handling the "create topic" transaction after it has come to consensus.

The application module invokes these handlers with the correct arguments at the correct time and on the correct
thread, depending on the workflow. A query is initially handled by gRPC logic in the application module, which then
looks up the appropriate service module based on the URL of the request. It then finds the `QueryHandler` for the
service, and looks up the appropriate method (based on the gRPC request), and invokes it. The application has error
handling for many kinds of exceptions, and is responsible for marshalling the response.

**Crucially**, the service module knows **nothing** about gRPC! The service module wants to live at the level of simple
POJOs and business logic, without any knowledge or concern about how it was invoked or who invoke it. It just does its
business, throws exceptions if needed, and otherwise lives in blissful ignorance. This philosophy applies to all code
in the service, including the other handlers, and not only the `QueryHandler`.

The methods in `QueryHandler`s, `PreTransactionHandler`s, and `TransactionHandler`s need some contextual information
to perform their work. For example, some queries are paid queries, and need a way for the query transaction to be
submitted to the hashgraph platform for consensus so the query can be paid. All transaction handlers need to be given
some state to work with (sometimes this state will be the latest immutable state, sometimes the current mutable state),
a simple API to work with the throttle engine, and a way to accumulate fees to be paid upon success of the transaction.
They also need some way to create records to be added to the record stream. APIs for these components are defined in the
SPI module, and implemented by the application, and supplied to the handlers at the time they are invoked.

#### RecordBuilder

When a service handles a transaction, or a paid query, it must construct a `TransactionRecord` to be collected and sent
to mirror nodes. We handle this by passing a `RecordBuilder` to the handle methods for transactions and paid queries.
They can then store the necessary state that gets turned into a `TransactionReceipt` and `TransactionRecord`. You may
be surprised to find that `RecordBuilder` itself is marker interface with no methods on it! This is intentional.
Each API in `hedera-app-spi` **only exposes the API needed to interface between the app and service modules**. There
is no `build` method on `RecordBuilder`, because the implementation of `RecordBuilder` lies with `hedera-app` and
services never have a need to build and inspect the resulting `TransactionRecord`. So we don't provide a build method
as part of that API (you will note that the `RecordBuilderImpl`, which is private to the `hedera-app` module, does
in fact provide such an API).

**By design**, we limit the SPI to just the minimal necessary.

One problem that we had to deal with comes from the protobuf definition for `TransactionRecord`. It (and
`TransactionReceipt`) has a hodgepodge of fields from different services mushed into one interface. If `RecordBuilder`
had exposed all those APIs, it would have broken all kinds of module boundaries and created circular dependencies.
Instead, each service module can define its own `RecordBuilder` subclass, adding just the API that it needs to be able
to add to a record. For example, the `hedera-token-service-api` defines the following records:
- `CreateAccountRecordBuilder`
- `CreateTokenRecordBuilder`
- `TokenRecordBuilder`
- `TokenTransferRecordBuilder`
- `TransferRecordBuilder`

Different transaction or query handling methods take different builders. (In actuality our single `RecordBuilderImpl`
implements all these interfaces and can produce a single `TransactionRecord`). The reason we break these builders down
in such a granular way, is to make it as *foolproof* as possible to implement the handle transaction methods. The
builder exposes just the amount of API that it should, and no more.

#### FeeAccumulator

The `FeeAccumulator` interface is implemented by `hedera-app` and passed to the service in the `HandleContext`. The
service module must `accumulate` its fees in the accumulator. This is trivial to do:

```
feeAccumulator.accumulate(HederaFunctionality.CRYPTO_CREATE_ACCOUNT);
```

In some cases it may be necessary for a service to accumulate fees and only "apply" them to the main accumulator on
some criteria (such as success). When a smart contract needs to call a system contract for token create, it needs to
buffer its own fee accumulator in case the token create call fails. This can be accomplished by using a simple
buffering implementation of the `FeeAccumulator` interface.

#### EntityIdGenerator

Some transactions result in the creation of new entities. All entities get an entity ID from the same pool of
numbers (we do not have a separate namespace for entity IDs for accounts and files and contracts -- we always use
the same incrementing long value for all entities). The `EntityIdGenerator`, passed to handlers, gives the
service the ability to generate new sequential IDs for use with new entities. The value is trivially rolled back
in case of an error (in fact, we simply don't commit the new `nextEntityId` unless we have success. On failure, we
just throw it away and start over with the last good value).

#### ThrottleAccumulator

TBD: I'm not sure that `ThrottleAccumulator` belongs in the SPI at all. It may be that we can let this be an
implementation detail of the `HandleTransactionDispatcherImpl`. The key thing is that we must be able to throttle during
the handle-transaction phase on the handle-transaction thread, and we must be able to do this in a re-entrant way
if a Smart Contract calls, for example, Token Create or some other operation. Maybe every single handle method should
check the throttle, or maybe not. I kind of think it should, which is why I have it here. But maybe not, maybe that
is a function the app can provide (i.e. maybe services can be implemented without having to think about throttles).

#### TransactionMetadata

Transactions are first handled as part of "pre-handle". This happens with multiple background threads. Any state read
or computed as part of this pre-handle, including any errors, are captured in the `TransactionMetadata`. This is then
made available to the transaction during the "handle" phase as part of the `HandleContext`.

### HandleTransactionDispatcher

This interface allows a service to dispatch a transaction to another service in an automated way. The utility of this
interface is somewhat suspect. It would be very helpful for something like a *BatchTransaction* (which doesn't exist
but is under discussion). It is probably not particularly useful for Smart Contracts, because each system contract
can just directly call the service that it wants to use.

### TransactionHandler and QueryHandler

These two interfaces are simple marker interfaces with no methods on them. Sub interfaces of `Service` define more
specific subtypes of `TransactionHandler` and `QueryHandler` with concrete method definitions. In general, each
of these handlers should define the full set of methods defined on the protobuf service definitions corresponding
to either a transaction or query. They may implement additional methods.

For example, the `CryptoTransactionHandler` defines `createAccount`, `updateAccount`, `cryptoTransfer` and so on.
But it also defines `createGenesisAccount` which is a special method called during the genesis workflows for creating
initial genesis accounts, and `finalizeTransfers` which is used as part of the handle-transaction workflow. A service
may implement *ANY* public API that is appropriate for the service.

There are a couple key concepts:
1. A single instance of `TransactionHandler` or `QueryHandler` is tied to a single version of merkle state. It is
   possible for a handler to be reused for multiple calls, or it may be created fresh for each call -- the exact
   semantics are an implementation detail and not something the service should depend on. What the service *can*
   depend on, is that the handler instance is always tied to single version of the merkle tree.
2. A single instance of `TransactionHandler` or `QueryHandler` will only ever be accessed from a single thread. There
   is no need for any volatile or thread safe code. This is done to reduce the number of potential bugs.

## Note on Pre-Handle and Handle

One of the design discussions that we had was how to design pre-handle in a way that we could potentially do everything
in a multithreaded way. Although the `TransactionMetadata` would support an architecture like that, we explicitly
**DID NOT** design the rest of the system to support this. We may be able to do so, but we were worried about memory,
locking, and thread synchronization becoming bottlenecks. We're still fairly unhappy with the amount of garbage we're
generating per transaction, but we believe the current architecture does have opportunity for re-use, which we lose if
we do everything a multithreaded way. However, we tried to keep an architecture loosely coupled enough that we
maybe be able to pull it off, if we so desire.

**NEXT: [States](states.md)**