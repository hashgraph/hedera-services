
# Scheduled Transactions Spec 

Scheduled Transactions are separated into 2 phases. The first one supports an "MVP" version of Scheduled Transactions in which users are able to schedule transactions that execute after all required signatures are collected.
In the second version of Scheduled Transactions, users are going to be able to schedule transactions to execute at a given point in the future.

## Scheduled Transactions (Phase 1)

### Goals and Constrains  
  
- Allow transaction to be submitted without all the required signatures and provide functionality for each of the signers to submit their signatures independently after a transaction was created.  
- Allow users to submit transactions to Hedera that will execute **once** all required signatures are acquired.  
  
**Constrains**  
- Expiry time for the scheduled transactions defined by a global property with a time limit of `N` seconds (`ledger.schedule.txExpiryTimeSecs`). If `N` seconds pass from the creation of the scheduled transactions, and it haven't gathered all of the required signatures, Hedera nodes will clear them from memory/state.  
- Throttles on every GRPC Operation that is defined for the new MVP Scheduled Transactions will be implemented.
- Based on the 2 bullets above, there is implicit limit of pending scheduled transactions in the network enforced by `txExpiryTimeSecds` and the throttle on `ScheduleCreate` operation.
  
### GRPC Interface  
  
New [Schedule Service](https://github.com/hashgraph/hedera-services/blob/master/hapi-proto/src/main/proto/ScheduleService.proto) is added in the protobufs. It is responsible for both the **creation and management** of Scheduled transactions.  
  
New `HederaFunctionality`:  
- `ScheduleCreate` - Creates new scheduled transaction.  
- `ScheduleSign` - Signs an already created scheduled transaction.  
- `ScheduleDelete` - Deletes an already created scheduled transaction. Must be signed by the specified `adminKey` .
- `ScheduleGetInfo` - Returns information for an already created scheduled transaction.    
  
#### ScheduleCreate  
  
Additional [`ScheduleCreateTransactionBody`](https://github.com/hashgraph/hedera-services/blob/master/hapi-proto/src/main/proto/ScheduleCreate.proto) is added in the protobufs. The message has the following format:  
  
```  
message ScheduleCreateTransactionBody {  
  bytes transactionBody // The transaction serialized into bytes that must be signed
  Key adminKey // (optional) The Key which is able to delete the Scheduled Transaction (if tx is not already executed)
  AccountID payer // (optional) The account which is going to pay for the execution of the Scheduled TX. If not populated, the scheduling account is charged for the execution of the scheduled TX
  SignatureMap sigMap // (optional) Signatures that could be provided (similarly to how signatures are provided in ScheduleSign operation) on Scheduled Transaction creation
}  
```  
  
**Optional properties**  
  
- `adminKey` is an optional field. If set, the specified `adminKey` is able to execute `ScheduleDelete` operation.  
- `payer` is an optional field. If set, the specified payer will be charged for the execution of the scheduled transaction. If ommited, the payer of the scheduled transaction will be the account which created the scheduled transaction in the first place.
- `sigMap` is an optional field that may or may not contain signatures from some of the parties required to sign the transaction 
  
Using this structure, users are able to create the Scheduled Transaction without the need for them to sign the actual underlying transaction. Account A creates Scheduled TX for account B, C and D to sign.  
  
**Idempotent creation**  
  
Creating Scheduled transactions is an idempotent operation in the sense that if multiple parties perform `ScheduleCreate` operation specifying "identical" transactions, only the first one will create the transaction and the other operations will append the provided signatures.  

Criteria for "identical" transactions - If there is a previously created Scheduled Transaction, that hasn't yet executed and the following properties are the same:
-  `transactionBody`
- `payer` 
- `adminKey` (if set)
  
If the transaction is deemed "identical", the second `scheduleCreate` tx will not be considered as a new scheduled transaction and the signatures provided in the signature map will be appended to the original transaction.  
  
`ScheduleCreate` transaction referring to an already created scheduled transaction and providing the rest of the required signature(s) will cause the underlying encoded transaction to be executed!  
  
#### ScheduleSign  
  
Additional [`ScheduleSignTransactionBody`](https://github.com/hashgraph/hedera-services/blob/master/hapi-proto/src/main/proto/ScheduleSign.proto) is added in the protobufs. The operation appends the signature(s) to an already existing Scheduled Entity. If after adding the new signature(s), the transaction has the required number of signatures, it will be executed immediately in the same transaction context. The message has the following format:  
  
```  
message ScheduleSignTransactionBody {  
  ScheduleID scheduleID // The ID of the Scheduled entity
  SigMap sigMap // The signature map containing the signature(s) to authorise the transaction
}  
```  
  
#### ScheduleDelete  
  
Additional [`ScheduleDeleteTransactionBody`](https://github.com/hashgraph/hedera-services/blob/master/hapi-proto/src/main/proto/ScheduleDelete.proto) is added in the protobufs. The operation deletes an already created Scheduled Transaction (unless the TX has already been executed). The transaction must be signed by the `adminKey` specified on the `ScheduleCreate` operation. Once the delete operation is executed, the data structure holding scheduled TX info in-state is marked as deleted, but is not cleared out.
  
The message has the following format:  
  
```  
message ScheduleDeleteTransactionBody {  
  ScheduleID schedule // The ID of the Scheduled Entity
}  
```  
  
#### ScheduleGetInfo  
  
An additional query [`ScheduleGetInfoQuery`](https://github.com/hashgraph/hedera-services/blob/master/hapi-proto/src/main/proto/ScheduleGetInfo.proto) is added for retrieving information related to Scheduled Transactions. The operation has the following format:  
  
```  
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
Once a given Scheduled Transaction **expires** or **executes**, it is no longer returned on `ScheduleGetInfoQuery`. The returned response is `SCHEDULE_DELETED`
  
#### Transaction Receipts & Records  
  
##### Transaction Receipt  
  
New `scheduleID` property is added in the `TransactionReceipt` protobuf. The new property is the ID of the newly created Scheduled TX. It is populated **only** in the receipts of `ScheduleCreate` transactions.  
  
```  
message TransactionReceipt {  
  ResponseCodeEnum status  
  ... 
  ScheduleID schedule // The ID of the newly created Scheduled Entity
}  
```  
  
##### Transaction Record  
  
Transaction Records change fundamentally due to Scheduled TX. `ScheduleSign` or `ScheduleCreate` transactions trigger the execution of the scheduled transaction at some point (unless it expires). The effects of the scheduled transaction will be represented into a separate `TransactionRecord`. 
Schedule Sign (or idempotently created Schedule Create) transaction that triggers the execution of the scheduled TX will produce **two** transaction records **with different consensus timestamps**.
  
New `scheduleRef` property is added in the `TransactionRecord` profobuf. The new property is the ID of Scheduled Entity that casues the execution of the underlying scheduled transaction.  
  
```  
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
  
### Design
  
#### State  
  
New FCMap is added (`Map<MerkleEntityId, MerkleSchedule>`).  
`MerkleSchedule` stores information related to scheduled transactions:  
  
- `byte[] transactionBody` → the body of the TX  
- `JKey adminKey` → the key that can perform `ScheduleDelete`  
- `AccountID schedulingAccount` → the account which scheduled the TX  
- `AccountID payer` → the account which is going to be paying for the execution  
- `HashSet<JKey> signers` → the keys that provided signatures for the scheduled tx so far.  
- `boolean deleted` → standard property that indicates whether the entity can be considered "deleted"  
  
##### ScheduleStore  
  
New `ScheduleStore` is implemented. It provides functionality for:  
- Creating Scheduled Entities  
- Appending Signatures  
- Deleting transaction (on `ScheduleDelete` or `Scheduled TX execution`)  
- Calculating whether there is an already existing "identical" transaction
  
`ScheduleStore`  keeps in-memory map of `hash(compositeKey) -> ScheduleID`, where `CompositeKey` is `transactionBody + adminKey + payerAccount`.
Every time transaction is scheduled, the transition logic checks whether there is an already existing "identical" scheduled transaction, that haven't yet executed.

If there is none, the `CompositeKey` of the transaction being scheduled is computed and is stored in a map referencing the schedule ID created for that scheduled transaction.

If there is, the operation is considered as idempotent creation and only the signatures are being appended to the original `ScheduleID` entity.  
  
The HashMap will be recreated from the `FCMap` after a restart or reconnect of the nodes.  
  
##### Transition Logic  
  
New `ScheduleCreateTransitionLogic`, `ScheduleSignTransitionLogic`, `ScheduleDeleteTransitionLogic` and `GetScheduleInfoAnswer` are implemented. 
  
**Creating Scheduled TX**  
  The following steps represent the major logic in creating Scheduled Transactions:

 1. Check whether "identical" transaction already exists. If no -> add the new scheduled transaction into the state
 2. Compute the `Key` that provided the signature and verify that he is one of the required keys for the Scheduled Transaction. (respond with error code if he is not).
 3. Add the `Key` to the `signers` set in the state.
 4. Compute whether all of the required `Key`'s provided signatures. If yes -> set the transaction context for the child transaction in order to be executed after the `Create` operation is fully processed. 
  
**Appending Signatures**  

The following steps represent the major logic in appending signatures to Scheduled Transactions:

 1. Verify that the scheduled TX for which we are appending signatures exists  
 2. Compute the `Key` that provided the signature and verify that he is one of the required keys for the Scheduled Transaction. (respond with error code if he is not).
 3. Add the `Key` to the `signers` set in the state.
 3. Compute whether all of the required `Key`'s provided signatures. If yes -> set the transaction context for the child transaction in order to be executed after the `Create` operation is fully processed.

##### Signature Verifications  
  
Every transaction is undergoing signature verification using the `expandSignatures` method called immediately after TX is submitted to Hedera. The current signature expansion logic will be extended with:  
 1. Additional implementation in `HederaSigningOrder` `forSchedule`. Used to handle signature verification **for the** `ScheduleCreate`, `ScheduleSign` and `ScheduleDelete` transactions.  
 2. Logic for expanding the `scheduled transaction` signatures (the underlying transaction in `ScheduleCreate` and `ScheduleSign`).  
 3. Logic for verifying that the verified signatures are `VALID` (in the `handleTransaction` context)

##### Fees  
  
Scheduled Transactions are more expensive compared to other operations due to the computation overhead and memory/state footprint.  

On `ScheduleCreate` operation, the fee that will be paid by the executing account will be for the creation of the scheduled entity, appending signatures (if any) and computing whether the Scheduled Transaction is ready for execution.
  
On `ScheduleSign` operation, the fee that will be paid by the executing account will be for the appending of the signature to the scheduled entity and computing whether the Scheduled Transaction is ready for execution.  
  
On the execution of a scheduled TX, the account which will be paying for the transaction will be the specified `payer` (when the TX was created) **OR** if no payer was provided, the **account that scheduled the TX in the first place**. 
  
Performance tests will be performed to see how many TPS we can do and how many we can execute (for large transactions with many signatures). Based on the performance tests, the fees will be set accordingly to their computation.   
  
#### Execution of Scheduled TX  
  
**Scheduled** **TX ID**  
  
Transactions of scheduled type will have the following format of `TransactionID` **on submission**!  
```  
message TransactionID {  
  uint32 nonce // Used to add addditional option for clients to create idempotent Transactions. Default 0
}  
```   
This is to say that the encoded Transaction in the `transactionBytes` of the Scheduled Transaction could be submitted without any of the `TransactionID` properties populated.
Once the transaction is executed (all required signatures are collected), the transaction is exported and externalised in the transaction record stream with the following `TransactionID`:
```  
message TransactionID {  
  Timestamp transactionValidStart // Inherited from original ScheduleCreate TX
  AccountID accountID // Inherited from original ScheduleCreate TX  
  bool scheduled // true  
  uint32 nonce // The nonce that was populated in the Transaction Bytes on submission
}  
```  
Once the check for `readyForExecution` determines that the Scheduled Transaction is ready to be executed, the transition logic (whether it is `Create` or `Sign`) creates `SignedTxAccessor` instance and sets it in the `Transaction Context`.

Once the `Sign/Create` execution finishes, the `AwareProcessLogic` checks whether there are any `triggered` transactions. If there are, the `Transaction Context` is being reset, the consensus time of the "normal" `Schedule Sign/Create` transaction is changed `consensusTime - 1 nanos`. At this point the execution of the `Scheduled Transaction` starts.

The validations for Scheduled Transaction are different from normal transactions. Some of them are skipped. F.e the check for the `transactionValidStart` + `validDuration` will be changed for TXs of scheduled type. Since the transaction execution might **not** be at the same time when `ScheduleCreate` is performed **and** the execution might be delayed up-to `ledger.scheduler.txExpiryTimeSecs` time (after that it will expire), the existing check for transaction time-related validity:  
`consensusTimestamp > transactionID.transactionValidStart + transactionValidDuration`  
  
is **no longer** performed.  
  
`transactionValidStart`, `accountID` and `scheduled` are populated by Hedera nodes when the TX starts execution. Users will submit Scheduled Transactions with `TransactionID` populating only `nonce` properties.  
  
Nonce will be an additional option presented to users so that they can create multiple sequential Scheduled Transactions that have the same `transaction body`. Using the `nonce` users will be able to create multiple scheduled transactions and all of them will be considered separate Scheduled TX (since the hash of the transaction body will be different for every TX)  
  
Example:  
*In Ethereum to Hedera bridge, validators of the bridge will create Scheduled Transactions for releasing assets to users by populating the timestamp of the ETH transaction in the `nonce` property.* This way if 1 user wants to bridge 2 times the same amount in a short period of time, validators will create 2 separate TXs.  
  
MVP Scheduled Transactions will execute once the required signatures are collected either on `ScheduleCreate` or `ScheduleSign`. The execution will take place **in the same transaction** as the **last** required signature is submitted. Meaning that once the last required signer executes `ScheduledSign` transaction (or `ScheduleCreate`), the underlying transaction will be executed (in the same `handleTransactionContext`).  
  
The record stream (when the transaction executes) will export **two transaction records**.  
  
Example:  
 1.  Alice creates a "scheduled" crypto transfer transaction, requiring 3 signatures (Alice, Bob, Carol). This transaction adds the `transaction bytes`, `schedulingAccount` and `payer` in the state. The authenticity of Alice's signature is verified and in the state, nodes are marking that her `JKey` provided a valid signature. The transaction is externalised as a new transaction record with `Transaction ID X` and `Consensus Time Y`  
 2. Bob executes `scheduled sign` transaction, providing his signature. The authenticity of the provided signatures is verified. The corresponding `JKey(s)` is marked "provided valid signatures" in state. The transaction is externalised as a new transaction record with `Transaction ID Z` and `Consensus Time T`.  
 3. Carol executes `scheduled sign` transaction, providing her signature. The authenticity of the provided signature is verified. The corresponding `JKey(s)` is marked "provided valid signatures" in state. Since this is the last required signature for that transaction, the scheduled transaction is removed from the in-memory map `(CompositeKey → Schedule ID)` and the Scheduled entity in state is marked as `deleted`.  
  
The state changes are externalised in transaction record **reflecting the Scheduled Sign**. The tx record has `Transaction ID U` and `Consensus Time R-1 nanos`
  
The scheduled tx is passed to the appropriate `Transition Logic` for execution. The scheduled transaction is externalised as a **second** transaction record with `Transaction ID X` (inheriting the `accountID` and `transactionValidStart` from `ScheduleCreate` **and** the additional properties `scheduled=true` and `nonce`.   
The consensus time is  `Consensus Time R`. The transaction record for the transaction will contain the `transferList` of transferred HBARs **and** the new `scheduleRef` property referencing the Scheduled Entity ID.  
  
If the scheduled transaction fails for some reason, the transaction record of the execution will be represented as failed in the record stream as normal transactions do. The transaction record of the schedule sign transaction will **not be affected.**  
  
#### Throttling & Limits  
  
 1. Throttle on every GRPC Operation that is defined for the new MVP Scheduled Transactions will be implemented 
 2. Using the throttling bucket for Scheduled Transactions creation implicit limit on the `maxPendingTxns` in the network will be enforced. Example:  
  
```  
 buckets.scheduleCreate.capacity = 100
 ledger.schedule.txExpiryTimeSecs = 1800 // secs (30 mins) => maxPendingTxns = 180000
 ```  
  Implicitly, max pending txns in the network will be limited to `scheduleCreate.capacity` * `ledger.schedule.txExpiryTimeSecs`, which in this example is `180 000` txns. This is true with the assumption that **all** submitted transactions are **expiring** and this is the worst-case scenario. It is expected that Scheduled Transactions are going to execute earlier than their expiration time, thus clearing memory/state.  
  
#### Expiry  
  
Transactions that haven't executed (did not receive the required signatures) are being cleared from the state after `ledger.schedule.txExpiryTimeSecs`
  
Monotonic Queue is used to track transaction expires (similar to transaction records).   
  
On every `handleTransaction` execution, a check for expiring `Transactions` is performed, clearing the transaction data from state and memory.  
  
In order to achieve `O(1)`, the queue is a queue of objects that each contain a reference to a scheduled transaction - `Schedule Entity ID`. Once a given TX executes or is deleted from the state it is marked as `deleted`. Nodes are not updating the queue that clears expired TX. This way we guarantee that retrieving and deleting the next TX is performed in `O(1)` complexity and not `O(log(n))`.  
  
The queue is similar to the Transactions Records expiry mechanism.  
  
The queue must be recreated from the `FCMap` (Scheduled TX in state) after a restart or reconnect.