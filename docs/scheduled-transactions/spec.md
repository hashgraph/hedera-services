# Specification of Scheduled Transactions (MVP)

# Goals and Constrains

- Allow transaction to be submitted without all the required signatures and provide functionality for each of the signers to submit their signatures independently after a transaction was created.
- Allow users to submit transactions to Hedera that will execute **once** all required signatures are acquired.

**Constrains**

- There will be an expiry time for the transaction defined by a global property with a time limit for N minutes (`ledger.scheduler.txExpiryTime`). (Once N minutes pass and the transaction does not gather all of the required signatures, Hedera nodes will clear them from memory/state).
- There will be throttles on every GRPC Operation that is defined for the new MVP Scheduled Transactions.
- There will be an implicit limit of pending transactions in the network enforced by `txExpiryTime` and the throttle on `ScheduleCreate` operation. More details [here]().

# GRPC Interface

New Schedule service will be added in the protobufs. It will be responsible for both the **creation and management** of Scheduled transactions.

New `HederaFunctionality`:

- `ScheduleCreate` - Creates new scheduled transaction
- `ScheduleSign` - Signs an already created scheduled transaction
- `ScheduleDelete` - Deletes an already created scheduled transaction. Must be signed by the specified `adminKey`
- `ScheduleGetInfo` - Returns information for an already created scheduled transaction

## Operations

### ScheduleCreate

Additional `ScheduleCreateTransactionBody` will be added in the protobufs. The message will have the following format:

```json
message ScheduleCreateTransactionBody {
	bytes transactionBody // The transaction serialized into bytes that must be signed
	Key adminKey // (optional) The Key which is able to delete the Scheduled Transaction (if tx is not already executed)
    AccountID payer // (optional) The account which is going to pay for the execution of the Scheduled TX. If not populated, the scheduling account is charged
	SignatureMap sigMap // (optional) Signatures that could be provided (similarly to how signatures are provided in ScheduleSign operation) on Scheduled Transaction creation
}
```

**Optional properties**

- `adminKey` is an optional field. If set, the specified `adminKey` is able to execute `ScheduleDelete` operation.
- `sigMap` is an optional field that may or may not contain signatures from some of the parties required to sign the transaction

Using this structure, users will be able to only create the Scheduled Transaction without the need for them to sign the actual underlying transaction. Account A creates Scheduled TX for account B, C and D to sign.

**Idempotent creation**

Creating Scheduled transactions will be idempotent operation in some sense.

Criteria for idempotent creation:

- `ScheduleCreate` operation is performed with the same `transactionBody` as a previously created scheduled transaction
- `payer` accounts are the same

If the 2 rules are fulfilled, the second `scheduleCreate` tx will not be considered as a new scheduled transaction. To add on top of that, the signatures provided in the signature map will be appended to the original transaction as-well.

`ScheduleCreate` transaction referring to an already created scheduled transaction and providing the rest of the required signature(s) will cause the underlying encoded transaction to be executed!

### ScheduleSign

Additional `ScheduleSignTransactionBody` will be added in the protobufs. The operation will append the signature(s) to the already existing Scheduled Entity. If after adding the new signature(s), the transaction has the required number of signatures it will be executed immediately in the same transaction context. The message will have the following format:

```json
message ScheduleSignTransactionBody {
	ScheduleID scheduleID // The ID of the Scheduled entity
	SigMap sigMap // The signature map containing the signature(s) to authorise the transaction
}
```

### ScheduleDelete

Additional `ScheduleDeleteTransactionBody` will be added in the protobufs. The operation will delete an already created Scheduled Transaction (unless the TX has already been executed). The transaction must be signed by the `adminKey` specified on the `ScheduleCreate` operation. Once this operation is executed, the data structure holding scheduled TX info in-state will be marked as deleted, but will not be cleared out.

The message will have the following format:

```json
message ScheduleDeleteTransactionBody {
	ScheduleID schedule // The ID of the Scheduled Entity
}
```

### ScheduleGetInfo

An additional query will be added for retrieving information related to Scheduled Transactions. The operation will have the following format:

```json
message ScheduleGetInfoQuery {
	QueryHeader header // standard info sent from client to node including the signed payment, and what kind of response is requested (cost, state proof, both, or neither).
	ScheduleID schedule // The ID of the Scheduled Entity
}

message ScheduleGetInfoResponse {
	ScheduleID schedule // The ID of the Scheduled Entity
	AccountID creatorAccountID // The Account ID which created the Scheduled TX
	AccountID payerAccountID // The account which is going to pay for the execution of the Scheduled TX
	bytes transactionBody // The transaction serialized into bytes that must be signed
	KeyList signers // The keys that have provided signatures so far for the Scheduled TX
    Key adminKey // The Key which is able to delete the Scheduled Transaction if set
}
```

**Important**

Once a given Scheduled Transaction **expires** or **executes**, it will no longer be returned on `ScheduleGetInfoQuery`. The response would be that the `Schedule` entity does not exist.

## Transaction Receipts & Records

### Transaction Receipt

New `scheduleID` property will be added in the `TransactionReceipt` protobuf. The new property will be the ID of the newly created Scheduled TX. It will be populated **only** in the receipts of `ScheduleCreate` transactions.

```json
message TransactionReceipt {
	ResponseCodeEnum status
	...
	ScheduleID schedule // The ID of the newly created Scheduled Entity
}
```

### Transaction Record

Transaction Records will change fundamentally due to Scheduled TX. `ScheduleSign` or `ScheduleCreate` transactions will trigger the execution of the scheduled transaction at some point (unless it expires). The effects of the scheduled transaction will be separated into new `TransactionRecord`. So for a Schedule Sign transaction that triggers the execution of the scheduled TX the record stream will have **two** transaction records **with different consensusTimestamps**. 

New `scheduleRef` property will be added in the `TransactionRecord` profobuf. The new property will be the ID of Scheduled Entity that caused the execution of the underlying scheduled transaction.

```json
TransactionRecord {
  /** current ones */
  receipt
  transactionHash
  consensusTimestamp
  transactionID
  memo
  transactionFee
  transferList
  tokenTransferList

	/** new property */
  ScheduleID scheduleRef // reference to the executed scheduled TX
}
```

# Design

## State

New FCMap will be added (`Map<MerkleEntityId, MerkleSchedule>`).
`MerkleSchedule` will store information related to scheduled transactions:

- `byte[] transactionBody` → the body of the TX
- `JKey adminKey`  → the key that can perform `ScheduleDelete`
- `AccountID schedulingAccount` → the account which scheduled the TX
- `AccountID payer` → the account which is going to be paying for the execution
- `HashSet<JKey> signers` → the keys that provided signatures for the scheduled tx so far.
- `boolean deleted` → standard property that indicates whether the entity can be considered "deleted"

### ScheduleStore

New `ScheduleStore` will be implemented. It will provide functionality for:

- Creating Scheduled Entities
- Appending Signatures
- Deleting transaction (on `ScheduleDelete` or `Scheduled TX execution`)

`ScheduleStore` will keep in-memory map of `hash(transactionBody) -> {ScheduleID, List<AccountID> requiredSigners}` . Every time a new transaction is being scheduled, its hash will be stored in a map referencing the schedule ID and a list of the `requiredSigners` will be computed from the transaction bytes.

Before every schedule entity ID creation, the transition logic will check whether there is an existing entry for the same `transactionBody` in the map. If there is **and** the payer account from the second scheduling TX is the same as the `payer` already in state, it will be considered as idempotent creation and only the signatures will be appended to the original `ScheduleID` entity.

The HashMap will be recreated from the `FCMap` after a restart or reconnect.

### Transition Logic

New `ScheduleCreateTransitionLogic`, `ScheduleSignTransitionLogic`, `ScheduleDeleteTransitionLogic` and `GetScheduleInfoAnswer` will be implemented.

**Creating Scheduled TX**

When creating a scheduled TX, we can divide the logic into the following components:

- (0) Add the new scheduled tx into the state - the important part here is that there must be a check for the idempotent creation of scheduled tx.
- (1) Append the provided signatures (if any) to the scheduled tx
- (2) Check whether all of the required signatures are collected - the important part here is to use the already implemented logic in `HederaSigningOrder` in order to compute the required `JKey`'s for the transaction and compare them to the list of accounts stored in the `hash(txBody) -> {ScheduleID, List<AccountID>}` map.
- (3) Execute the scheduled TX if signatures are collected. More on that [here]()

**Appending Signatures**

When appending signatures to an already scheduled TX, we can divide the logic into the following components:

- (0) Verify that the scheduled TX for which we are appending signatures exists
- (1) Append the provided signatures (if any) to the scheduled tx
- (2) Check whether all of the required signatures are collected - the important part here is to use the already implemented logic in `HederaSigningOrder` in order to compute the required `JKey`'s for the transaction and compare them to the list of accounts stored in the `hash(txBody) -> {ScheduleID, List<AccountID>}` map.
- (3) Execute the scheduled TX if signatures are collected. More on that [here]()

### Fees

Scheduled Transactions will be more expensive due to the computation overhead and memory/state footprint.

On `ScheduleCreate` operation, the fee that will be paid by the executing account will be for the creation of the scheduled entity.

On `ScheduleSign` operation, the fee that will be paid by the executing account will be for the appending of the signature to the scheduled entity.

On the execution of a scheduled TX, the account which will be paying for the transaction will be the specified `payer` (when the TX was created) **OR** if no payer was provided, the **account that scheduled the TX in the first place**.

Performance tests must be performed to see how many TPS we can do and how many we can execute (for large transactions with many signatures). Based on the performance tests, the fees will be set accordingly to their computation. 

## Execution of Scheduled TX

**Scheduled** **TX ID**

Transactions of scheduled type will have the following format of `TransactionID`s:

```json
message TransactionID {
	Timestamp transactionValidStart // Inherited from original ScheduleCreate TX
	AccountID accountID // Inherited from original ScheduleCreate TX
  bool scheduled // true
  uint32 nonce // Used to add addditional option for clients to create idempotent TX. Default 0
}
```

Scheduled transactions will have a modified `handle submission` flow.

The check for the `transactionValidStart` + `validDuration` will be changed for TXs of scheduled type. Since the transaction execution might **not** be at the same time when `ScheduleCreate` is performed **and** the execution might be delayed up-to `ledger.scheduler.txExpiryTime` time (after that it will expire), the existing check for transaction time-related validity:

`consensusTimestamp > transactionID.transactionValidStart + transactionValidDuration`

will be changed. This validation will **no longer** be performed.

`transactionValidStart` and `accountID` will be populated by Hedera nodes when the TX starts execution. Users will submit Scheduled Transactions with `TransactionID` populating only `scheduled` and `nonce` properties.

Nonce will be an additional option presented to users so that they can create multiple sequential Scheduled Transactions that have the same `transaction body`. Using the `nonce` users will be able to create multiple scheduled transactions and all of them will be considered separate Scheduled TX (since the hash of the transaction body will be different for every TX)

Example:
*In Ethereum to Hedera bridge, validators of the bridge will create Scheduled Transactions for releasing assets to users by populating the timestamp of the ETH transaction in the `nonce` property.* This way if 1 user wants to bridge 2 times the same amount in a short period of time, validators will create 2 separate TXs.

MVP Scheduled Transactions will execute once the required signatures are collected either on `ScheduleCreate` or `ScheduleSign`. The execution will take place **in the same transaction** as the **last** required signature is submitted. Meaning that once the last required signer executes `ScheduledSign` transaction (or `ScheduleCreate`), the underlying transaction will be executed (in the same `handleTransactionContext`).

The record stream (when the transaction executes) will export **two transaction records**.

Example:

1. Alice creates a "scheduled" crypto transfer transaction, requiring 3 signatures (Alice, Bob, Carol). This transaction adds the `transaction bytes`, `schedulingAccount` and `payer` in the state. The authenticity of Alice's signature is verified and in the state, we are marking that her `JKey` provided a valid signature. The transaction is externalised as a new transaction record with `Transaction ID X`  and `Consensus Time Y`
2. Bob executes `scheduled sign` transaction, providing his signature. The authenticity of the provided signatures is verified. The corresponding `JKey(s)` is marked in the state that provided valid signatures. The transaction is externalised as a new transaction record with `Transaction ID Z` and `Consensus Time T`.
3. Carol executes `scheduled sign` transaction, providing her signature. The authenticity of the provided signature is verified. The corresponding `JKey(s)` is marked in the state that provided valid signatures. Since this is the last required signature for that transaction, the scheduled transaction is removed from the memory map `(hash → {ID, AccountIDs})` and the Scheduled entity in state is marked as `deleted`.

The state changes are externalised in transaction record **reflecting the Scheduled Sign**. The tx record has `Transaction ID U` and `Consensus Time R` and `tieOrder=0`

The scheduled tx is passed to the appropriate `Transition Logic` for execution. The scheduled transaction is externalised as a **second** transaction record with `Transaction ID X` (inheriting the `accountID` and `transactionValidStart` from `ScheduleCreate` **and** the additional properties `scheduled=true` and `nonce`. 
The consensus time is  `Consensus Time R` (**same as the schedule sign TX record)** and the `tieOrder=1`.  The transaction record for the transaction will contain the `transferList` of transferred HBARs **and** the new `scheduleRef` property referencing the Scheduled Entity ID.

If the scheduled transaction fails for some reason, the transaction record of the execution will be represented as failed in the record stream as a normal TX. The transaction record of the schedule sign transaction will **not be affected.**

### Signature Verifications

Every transaction is undergoing signature verification using the `expandSignatures` method called immediately after TX is submitted to Hedera. The current signature expansion logic will be extended with:

- Additional implementation in `HederaSigningOrder` `forSchedule`. Used to handle signature verification **for the** `ScheduleCreate`, `ScheduleSign` and `ScheduleDelete` transactions.
- Logic for expanding the `scheduled transaction` signatures (the underlying transaction in `ScheduleCreate` and `ScheduleSign`).

## Throttling & Limits

- there will be a throttle on every GRPC Operation that is defined for the new MVP Scheduled Transactions.
- Using the throttling bucket for Scheduled Transactions creation we will be able to enforce implicitly the `maxPendingTxns` in the network. Example:

    ```markdown
    buckets.scheduleCreate.capacity = 100
    ledger.scheduler.txExpiryTime = 1800 secs (30 mins)
    => maxPendingTxns = 180000
    ```

    Implicitly, max pending txns in the network will be limited to `scheduleCreate.capacity` * `ledger.scheduler.txExpiryTime`, which in this example is 180000 txns. This is true with the assumption that **all** submitted transactions are **expiring** and this is the worst-case scenario. It is expected that Scheduled Transactions are going to execute earlier than their expiration time, thus clearing memory/state.

## Expiry

Transactions that haven't executed (did not receive the required signatures) will be cleared from the state after `ledger.scheduler.txExpiryTime` minutes.

Monotonic Queue will be used to track transaction expiries (similar to transaction records). 

On every `handleTransaction` execution, a check for expiring `Transactions` will be performed, clearing the transaction data from state and memory.

In order to achieve `O(1)`, the queue will be a queue of objects that each contain a reference to a scheduled transaction - Schedule Entity ID. Once a given TX executes or is deleted from the state it will be marked as `deleted`. We are not going to update the queue that clears expired TX. This way we will guarantee that retrieving and deleting the next TX will be performed in `O(1)` complexity and not `O(log(n))`.

The queue will be similar to the Transactions Records expiry mechanism.

The queue must be recreated from the `FCMap` (Scheduled TX in state) after a restart or reconnect.