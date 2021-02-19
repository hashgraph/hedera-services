# Scheduled Transactions -- End-to-End Tests specification

Listed below are the set of EET defined for Scheduled Transactions.
This is not the full list of EETs. More will be added in the coming days/weeks.

## Creation

#### 1. Should be able to create Scheduled Transaction with `transactionBody` only

**Expect**:
- New Schedule entity must be created and its ID must be specified in the `TransactionReceipt`'s `ScheduleID` property.
- When querying the Schedule Entity by the `ScheduleID` form the `transactionReceipt`:
    - it must have the same `ScheduleID` as specified in the `TransactionReceipt`
    - it must have `creatorAccountID` set, equal to the `transaction sender` of the `schedule create` transaction
    - it must have `payerAccountID` set, equal to the `transaction sender` of the `schedule create` transaction
    - it must have the same `transactionBody` bytes retrieved
    - it must have empty `signatories` 
    - it must have empty `adminKey`
    - it must have empty `memo`

#### 2. Should be able to create Scheduled Transaction with `transactionBody` and `adminKey` only

**Expect**:
- New Schedule entity must be created and its ID must be specified in the `TransactionReceipt`'s `ScheduleID` property.
- When querying the Schedule Entity by the `ScheduleID` form the `transactionReceipt`:
    - it must have the same `ScheduleID` as specified in the `TransactionReceipt`
    - it must have `creatorAccountID` set, equal to the `transaction sender` of the `schedule create` transaction
    - it must have `payerAccountID` set, equal to the `transaction sender` of the `schedule create` transaction
    - it must have the same `transactionBody` bytes retrieved
    - **it must have the same `adminKey` as specified in the creation TX**
    - it must have empty `signatories` 
    - it must have empty `memo`
    
#### 3. Should be able to create Scheduled Transaction with `transactionBody` and `payerAccountID` only

**Expect**:
- New Schedule entity must be created and its ID must be specified in the `TransactionReceipt`'s `ScheduleID` property.
- When querying the Schedule Entity by the `ScheduleID` form the `transactionReceipt`:
    - it must have the same `ScheduleID` as specified in the `TransactionReceipt`
    - it must have `creatorAccountID` set, equal to the `transaction sender` of the `schedule create` transaction
    - **it must have `payerAccountID` set to the `payerAccountID` specified in the creation TX**
    - it must have the same `transactionBody` bytes retrieved
    - it must have empty `adminKey`
    - it must have empty `signatories` 
    - it must have empty `memo`
    
#### 4. Should be able to create Scheduled Transaction with `transactionBody` and `memo`

**Expect**:
- New Schedule entity must be created and its ID must be specified in the `TransactionReceipt`'s `ScheduleID` property.
- When querying the Schedule Entity by the `ScheduleID` form the `transactionReceipt`:
    - it must have the same `ScheduleID` as specified in the `TransactionReceipt`
    - it must have `creatorAccountID` set, equal to the `transaction sender` of the `schedule create` transaction
    - it must have `payerAccountID` set, equal to the `transaction sender` of the `schedule create` transaction
    - it must have the same `transactionBody` bytes retrieved
    - it must have empty `signatories` 
    - it must have empty `adminKey`
    - **it must have `memo` set, equal to the `memo` specified in the creation TX**

#### 5. Should be able to create Scheduled Transaction with `transactionBody` and `signatures`

When creating the Scheduled Transaction, populate the `SignatureMap` with one or more signatures by `Key`(s) required by the transaction that is being scheduled.

**Expect**: 
- New Schedule entity must be created and its ID must be specified in the `TransactionReceipt`'s `ScheduleID` property.
- When querying the Schedule Entity by the `ScheduleID` form the `transactionReceipt`:
    - it must have the same `ScheduleID` as specified in the `TransactionReceipt`
    - it must have `creatorAccountID` set, equal to the `transaction sender` of the `schedule create` transaction
    - it must have `payerAccountID` set, equal to the `transaction sender` of the `schedule create` transaction
    - it must have the same `transactionBody` bytes retrieved
    - **it must have a set of `signatories` populated. The signatories must correspond to the simple `Key`(s) whose signature has been provided.** 
    - it must have empty `adminKey`
    - it must have empty `memo` 
    
#### 6. Check for idempotent creation #1
**Given**: Scheduled Transaction created with `txBytes`, `admin` and `payer`.\
**When**: New Scheduled is created with the same `txBytes`, but different `admin` and `payer`.\
**Expect**: New Scheduled Transaction to be created.

#### 7. Check for idempotent creation #2
**Given**: Scheduled Transaction created with `txBytes`, `admin` and `payer`.\
**When**: New Scheduled is created with the same `txBytes` and `admin`, but different `payer`.\
**Expect**: New Scheduled Transaction to be created.

#### 8. Check for idempotent creation #4
**Given**: Scheduled Transaction created with `txBytes`, `admin` and `payer`.\
**When**: New Scheduled is created with the same `txBytes` and `payer`, but different `admin`.\
**Expect**: New Scheduled Transaction to be created.

#### 9. Check for idempotent creation #5
**Given**: Scheduled Transaction created with `txBytes`, `admin` and `payer`.\
**When**: New Scheduled is created with the same `txBytes`, `admin` and `payer`.\
**Expect**:
 - No new Scheduled Transaction created.
 - Signatures of the second transaction have been appended to the first one.
 - `ScheduleID` is populated in the Transaction Receipt with the same ID as of the first Transaction. 

#### 10. Check for TX creation after previous identical TX execution
**Given**:
1. Scheduled Transaction created with `txBytes`, `admin` and `payer`.
2. Append required signatures for the transaction using `ScheduleSign`
3. Transaction Executes

**When**: New Scheduled TX created with the same `txBytes`, `admin` and `payer`.\
**Expect**: New Scheduled Transaction to be created (even though `txBytes`, `admin` and `payer` are the same) 

#### 11. Check for TX creation after previous identical TX expiry
**Given**:
0. Configure very low `ledger.schedule.txExpiryTimeSecs`
1. Scheduled Transaction created with `txBytes`, `admin` and `payer`.
2. Wait for Transaction to expire

**When**: New Scheduled is created with the same `txBytes`, `admin` and `payer`.\
**Expect**: New Scheduled Transaction to be created (even though `txBytes`, `admin` and `payer` are the same) 

#### 12. Creating Scheduled Transaction with non-existing `payerAccountID` must fail
**When**: New Scheduled transaction created with non-existing `payerAccountID` is created.\
**Expect**: The `ScheduleCreate` operation to fail with `INVALID_SCHEDULE_PAYER_ID`

#### 13. Creating Scheduled Transaction with non-required signature must fail
**When**: New Scheduled transaction created with `sigMap` providing signatures signed by a key(s) that are not required for the execution of the transaction body.\
**Expect**: The `ScheduleCreate` operation to fail with `SOME_SIGNATURES_WERE_INVALID`
  
#### 14. Creating Scheduled Transaction with too long `memo` must fail
**When**: New Scheduled transaction created with `memo` populated with more than `100` bytes.\
**Expect**: The `ScheduleCreate` operation to fail with `ENTITY_MEMO_TOO_LONG` 

#### 15. Creating Scheduled Transaction with invalid `ed25519` `signature` must fail
**When**: New Scheduled transaction created with `sigMap` that does not have valid `ed25519` `signature` in one of the `SignaturePair`s\
**Expect**: The `ScheduleCreate` operation to fail with `SOME_SIGNATURES_WERE_INVALID`.

#### 16. Creating nested Schedule Create must fail
**When**: New Scheduled transaction scheduling `ScheduleCreate` operation.
**Expect** The `ScheduleCreate` operation to fail with `UNSCHEDULABLE_TRANSACTION`.

#### 17. Creating nested Schedule Sign must fail
**When**: New Scheduled transaction scheduling `ScheduleSign` operation.
**Expect** The `ScheduleCreate` operation to fail with `UNSCHEDULABLE_TRANSACTION`.
 
## Signing

#### 18. Collecting signatures for Scheduled Transaction
**Given**: Scheduled Transaction created with `txBody` requiring 3 simple `Keys` to sign.\
**When**: Schedule Sign executed providing 2 signatures from `2` of the required `Keys`.\
**Expect**: Scheduled Transaction to have the 2 `Keys` set as signatories.

#### 19. Adding signatures to already executed TX must fail
**Given**: Scheduled Transaction with ID `X` created and executed.\
**When**: New Scheduled Sign operation submitted with `ScheduleID` `X`.\
**Expect**: The `ScheduleSign` operation to fail with `SCHEDULE_WAS_DELETED`

#### 20. Adding signatures to non-existing TX must fail
**When**: New Scheduled Sign operation submitted with non-existing `ScheduleID`.\
**Expect**: The `ScheduleSign` operation to fail with `INVALID_SCHEDULE_ID`

#### 21. Adding invalid `ed25519` `signature` must fail
**Given**: Scheduled Transaction created.\
**When**: New Scheduled Sign operation submitted with `sigMap` that does not have valid `ed25519` `signature` in one of the `SignaturePair`s\
**Expect**: The `ScheduleSign` operation to fail with `SOME_SIGNATURES_WERE_INVALID`.

#### 22. Adding signature signed by non-required signer must fail
**Given**: Scheduled Transaction created.\
**When**: New Scheduled Sign operation submitted with `sigMap` providing signatures signed by a key(s) that are not required for the execution of the transaction body.\
**Expect**: The `ScheduleSign` operation to fail with `SOME_SIGNATURES_WERE_INVALID`

#### 23. Adding signature signed by non-required signer must fail #2
**Given**:
 1. Token with `mint` and `admin` keys created (`mint = MintKey`)
 2. Token Mint Scheduled Transaction created.
 3. Token's mint key changed to new one. (`MintKey` changed to `NewMintKey`)

**When**: New Schedule Sign operation submitted adding the signature of `MintKey`\
**Expect**: The `ScheduleSign` operation to fail with `SOME_SIGNATURES_WERE_INVALID` since the `mint` key has been set to `NewMintKey` and `MintKey` signature is not required.

## Deletion

#### 24. Deleting Scheduled Transactions
**Given**: Scheduled Transaction created with `adminKey` set.\
**When**: New Schedule Delete operation submitted for the deletion of the created scheduled transaction.\
**Expect**: The `ScheduleDelete` operation to succeed. Once `ScheduledTransaction` is queried, it must resolve to `SCHEDULE_WAS_DELETED`.

#### 25. Deleting immutable scheduled transaction must fail
**Given**: Scheduled Transaction created with no `adminKey` set.\
**When**: New Schedule Delete operation submitted for the deletion of the scheduled transaction\
**Expect**: The `ScheduleDelete` operation to fail with `SCHEDULE_IS_IMMUTABLE`.

#### 26. Deleting scheduled transaction can be performed only by the admin key
**Given**: Scheduled Transaction created with `adminKey` set.\
**When**: New Schedule Delete operation submitted by non-admin key.\
**Expect**: The `ScheduleDelete` operation to fail with `UNAUTHORIZED`.

#### 27. Deleting already deleted scheduled transaction must fail
**Given**:
 1. Scheduled Transaction created with `adminKey` set.
 2. Scheduled Transaction deleted by the admin.

**When**: New Schedule Delete operation submitted by the admin.\
**Expect**: The `ScheduleDelete` operation to fail with `INVALID_SCHEDULE_ID`.

#### 28. Deleting already executed scheduled transaction must fail
**Given**:
 1. Scheduled Transaction created
 2. Scheduled Transaction executed.

**When**: New Schedule Delete operation submitted by the admin.\
**Expect**: The `ScheduleDelete` operation to fail with `INVALID_SCHEDULE_ID`.

#### 29. Deleting expired scheduled transaction must fail
**Given**:
 1. Scheduled Transaction created
 2. Scheduled Transaction expired.

**When**: New Schedule Delete operation submitted by the admin.\
**Expect**: The `ScheduleDelete` operation to fail with `INVALID_SCHEDULE_ID`.

#### 30. Deleting non-existing scheduled transaction must fail
**When**: New Schedule Delete operation submitted by the admin for the deletion of non-existing `ScheduleID`.\
**Expect**: The `ScheduleDelete` operation to fail with `INVALID_SCHEDULE_ID`.

## Execution

#### 31. Scheduled Transaction Executes with default payer
**Given**:
 1. Crypto Transfer Scheduled transaction created with `ScheduleID=S` and no `payerAccount` set.
 2. `ScheduleCreate` has `TransactionID.transactionValidStart=X` and `TransactionID.accountID=Y` 

**When**: New Schedule Sign operation submitted, providing the required signatures for the transaction to execute.\
**Expect**: 
- The `ScheduleSign` operation to succeed.
- `ScheduleSign` record to have `consensusTimestamp=Z`
- `CryptoTransfer` record to have `consensusTimestamp= Z+1 nano`
- `CryptoTransfer` record to have Transaction ID -> `transactionValidStart=X`; `accountID=Y`; `scheduled=true` and empty `nonce` property
- `CryptoTransfer` record to have `scheduleRef=S`  
- `CryptoTransfer` record to have `transferList` containing fee transfer, paid by `accountID=Y`

#### 32. Scheduled Transaction with configured payer
**Given**:
 1. Crypto Transfer Scheduled transaction created with `ScheduleID=S` and `payerAccount=P`
 2. `ScheduleCreate` has `TransactionID.transactionValidStart=X` and `TransactionID.accountID=Y` 

**When**: New Schedule Sign operation submitted, providing the required signatures for the transaction to execute.\
**Expect**: 
- The `ScheduleSign` operation to succeed.
- `ScheduleSign` record to have `consensusTimestamp=Z`
- `CryptoTransfer` record to have `consensusTimestamp= Z+1 nano`
- `CryptoTransfer` record to have Transaction ID -> `transactionValidStart=X`; `accountID=Y`; `scheduled=true` and empty `nonce` property
- `CryptoTransfer` record to have `scheduleRef=S`  
- **`CryptoTransfer` record to have `transferList` containing fee transfer, paid by `accountID=P`**

#### 33. Scheduled Transaction with default payer, but with no funds
**Given**:
 1. Crypto Transfer Scheduled transaction created with `ScheduleID=S` and no `payerAccount` set.
 2. `ScheduleCreate` has `TransactionID.transactionValidStart=X` and `TransactionID.accountID=Y` 
 3. All funds from `accountID=Y` transferred to another account. 

**When**: New Schedule Sign operation submitted, providing the required signatures for the transaction to execute.\
**Expect**: 
- The `ScheduleSign` operation to succeed.
- `ScheduleSign` record to have `consensusTimestamp=Z`
- `CryptoTransfer` record to fail with `INSUFFICIENT_PAYER_BALANCE`
- `CryptoTransfer` record to have `consensusTimestamp= Z+1 nano`
- `CryptoTransfer` record to have Transaction ID -> `transactionValidStart=X`; `accountID=Y`; `scheduled=true` and empty `nonce` property
- `CryptoTransfer` record to have `scheduleRef=S`

#### 34. Scheduled Transaction with configured payer, but with no funds
**Given**:
 1. Crypto Transfer Scheduled transaction created with `ScheduleID=S` and `payerAccount=P`.
 2. `ScheduleCreate` has `TransactionID.transactionValidStart=X` and `TransactionID.accountID=Y` 
 3. All funds from `accountID=P` transferred to another account (payer account does not have balance anymore to pay for the scheduled transaction) 

**When**: New Schedule Sign operation submitted, providing the required signatures for the transaction to execute.\
**Expect**: 
- The `ScheduleSign` operation to succeed.
- `ScheduleSign` record to have `consensusTimestamp=Z`
- `CryptoTransfer` record to fail with `INSUFFICIENT_PAYER_BALANCE`
- `CryptoTransfer` record to have `consensusTimestamp= Z+1 nano`
- `CryptoTransfer` record to have Transaction ID -> `transactionValidStart=X`; `accountID=Y`; `scheduled=true` and empty `nonce` property
- `CryptoTransfer` record to have `scheduleRef=S`