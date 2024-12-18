**TBDs:**

- In which module will this logic live? I've currently put it in the network but as far as I can see currently only
  admin transactions live there. Probably not the right place. UtilService probably? Not sure.
- Do we need a feature flag for this HIP?
- What are the throttle parameters?
- How should fees be calculated?
- Is batchKey required?
- What will be the mechanism for gathering signatures for the inner transactions? For the schedule we have ScheduleSign,
  do we have something similar for batch transactions?
- Node that if we use dispatch mechanism for each transaction we are limited by the maximum proceeding child
  transactions(50 at the moment, see ConsensusConfig.handleMaxFollowingRecords). This includes all the child
  transactions.
  E.g. we can have a batch with 50 transactions each of them with 0 child transactions or 1 transaction that has 49
  child transactions.

# Introduce AtomicBatchTransaction

## Purpose

Add new functionality that would make it possible to execute multiple transactions in a single atomic unit. This would
enhance the capabilities of the Hedera network by enabling complex transaction flows without relying on smart contracts
or external monitoring.

## Goals

1. Define new `AtomicBatchTransaction` HAPI transaction.
2. Implement the batch transaction handler logic.
3. Introduce a `batchKey` to ensure transaction integrity.

## Architecture

**TBD:** do we need a feature flag?

### Add the new RPC to `NetworkService`(**TBD**):

```protobuf
service NetworkService {
  // Existing properties omitted

  /**
  * Execute a batch of transactions atomically.
  * <p>
  * All transactions in the batch will be executed in order, and if any
  * transaction fails, the entire batch will fail.
  */
  rpc atomicBatchTransaction (Transaction) returns (TransactionResponse);
}
```

### Response codes to add(response_code.proto):

```protobuf
/**
* The list of batch transactions is empty
*/
  BATCH_LIST_EMPTY

/**
* The list of batch transactions contains duplicated transactions
*/
  BATCH_LIST_CONTAINS_DUPLICATES

/**
* The list of batch transactions contains null values
*/
  BATCH_LIST_CONTAINS_NULL_VALUES
```

### Add an `AtomicBatchTransactionBody`:

```protobuf
message AtomicBatchTransactionBody {
  repeated Transaction transactions = 1;
}
```

### Add the `batchKey` and the `AtomicBatchTransactionBody` to the `TransactionBody`:

```protobuf
message TransactionBody {
  // Existing properties omitted.

  /**
  * The <b>entire public key</b> of the trusted batch assembler.
  *
  * Only Ed25519 and ECDSA(secp256k1) keys and hence signatures are currently supported.
  */
  Key batchKey = 63;

  oneof data {
    // Existing values omitted.

    AtomicBatchTransactionBody atomicBatchTransactionBody = 64;
  }
}
```

### Create an `AtomicBatchTransactionHandler`:

- Add it to the `NetworkAdminHandlers`(**TBD**)
- Add it to the `TransactionHandlers`
- Add it to `TransactionDispatcher.getHandler`
- Add the new functionality to `basic_types.proto.HederaFunctionality`
- Add the new functionality to the `ApiPermissionConfig` with `0-*` range
- Add the new RPC definitions in `NetworkServiceDefinition`(**TBD**)

### PureChecks:

- Validation if `AtomicBatchTransactionBody` exists; throw `NullPointerException` if it does not.
- Validation if the list of transactions is empty; throw `BATCH_LIST_EMPTY` if it is.
- Validation if there are duplicate transactions (by `hashCode` and/or `body.transactionID`);
  throw `BATCH_LIST_CONTAINS_DUPLICATES` if there are.
- Validation if there are `null` objects in the transaction list; throw `BATCH_LIST_CONTAINS_NULL_VALUES` if found.

### PreHandler

- Call the `context.requireKeyOrThrow` for `PreHandleContext.batchKey` (is it required? **TBD**)

### Handler

- Go through each `AtomicBatchTransactionBody.transactions` and dispatch each
- If any of the transactions or child transactions fail, the whole batch fails

### Throttles

- **TBD**
- Add the new transaction to all `throttles.json` files under `ThroughputLimits` bucket?

### Fees

- Fees should be proportional to the number of batch transactions? **TBD**

### Acceptance Tests

- Given a valid AtomicBatchTransaction containing multiple valid inner transactions, when the batch is executed, then
  all inner transactions should succeed, and the transaction record should reflect the combined changes of all inner
  transactions.
- Given a valid AtomicBatchTransaction containing an invalid inner transaction, when the batch is executed, then the
  entire batch transaction should fail, and none of the inner transactions should be applied.
- Given a valid AtomicBatchTransaction containing a mix of valid and invalid inner transactions, when the batch is
  executed, then the entire batch transaction should fail, and none of the inner transactions should be applied.
- Given a AtomicBatchTransaction without a batchKey signature when the batch is executed, then the transaction should
  fail. (**TBD**)
