# Scheduled Transactions -- BDD tests specification

Listed below are the set of bdd tests defined for Scheduled Transactions.

## Creation

### Happy paths

**1. Should be able to create Scheduled Transaction with `transactionBody` only**

Expect:
- New Schedule entity must be created and its ID must be specified in the `TransactionReceipt`'s `ScheduleID` property.
- When querying the Schedule Entity by the `ScheduleID` form the `transactionReceipt`:
    - it must have the same `ScheduleID` as specified in the `TransactionReceipt`
    - it must have `creatorAccountID` set, equal to the `transaction sender` of the `schedule create` transaction
    - it must have `payerAccountID` set, equal to the `transaction sender` of the `schedule create` transaction
    - it must have the same `transactionBody` bytes retrieved
    - it must have empty `signers` 
    - it must have empty `adminKey`
    - it must have empty `memo`

**2. Should be able to create Scheduled Transaction with `transactionBody` and `adminKey` only**

Expect:
- New Schedule entity must be created and its ID must be specified in the `TransactionReceipt`'s `ScheduleID` property.
- When querying the Schedule Entity by the `ScheduleID` form the `transactionReceipt`:
    - it must have the same `ScheduleID` as specified in the `TransactionReceipt`
    - it must have `creatorAccountID` set, equal to the `transaction sender` of the `schedule create` transaction
    - it must have `payerAccountID` set, equal to the `transaction sender` of the `schedule create` transaction
    - it must have the same `transactionBody` bytes retrieved
    - **it must have the same `adminKey` as specified in the creation TX**
    - it must have empty `signers` 
    - it must have empty `memo`
    
**3. Should be able to create Scheduled Transaction with `transactionBody` and `payerAccountID` only**

Expect:
- New Schedule entity must be created and its ID must be specified in the `TransactionReceipt`'s `ScheduleID` property.
- When querying the Schedule Entity by the `ScheduleID` form the `transactionReceipt`:
    - it must have the same `ScheduleID` as specified in the `TransactionReceipt`
    - it must have `creatorAccountID` set, equal to the `transaction sender` of the `schedule create` transaction
    - **it must have `payerAccountID` set to the `payerAccountID` specified in the creation TX**
    - it must have the same `transactionBody` bytes retrieved
    - it must have empty `adminKey`
    - it must have empty `signers` 
    - it must have empty `memo`
    
**4. Should be able to create Scheduled Transaction with `transactionBody` and `memo`**

Expect:
- New Schedule entity must be created and its ID must be specified in the `TransactionReceipt`'s `ScheduleID` property.
- When querying the Schedule Entity by the `ScheduleID` form the `transactionReceipt`:
    - it must have the same `ScheduleID` as specified in the `TransactionReceipt`
    - it must have `creatorAccountID` set, equal to the `transaction sender` of the `schedule create` transaction
    - it must have `payerAccountID` set, equal to the `transaction sender` of the `schedule create` transaction
    - it must have the same `transactionBody` bytes retrieved
    - it must have empty `signers` 
    - it must have empty `adminKey`
    - **it must have `memo` set, equal to the `memo` specified in the creation TX**

**5. Should be able to create Scheduled Transaction with `transactionBody` and `signatures`**

When creating the Scheduled Transaction, populate the `SignatureMap` with one or more signatures by `Key`(s) required by the transaction that is being scheduled.

Expect: 
- New Schedule entity must be created and its ID must be specified in the `TransactionReceipt`'s `ScheduleID` property.
- When querying the Schedule Entity by the `ScheduleID` form the `transactionReceipt`:
    - it must have the same `ScheduleID` as specified in the `TransactionReceipt`
    - it must have `creatorAccountID` set, equal to the `transaction sender` of the `schedule create` transaction
    - it must have `payerAccountID` set, equal to the `transaction sender` of the `schedule create` transaction
    - it must have the same `transactionBody` bytes retrieved
    - **it must have a set of `signers` populated. The signers must correspond to the simple `Key`(s) whose signature has been provided.** 
    - it must have empty `adminKey`
    - it must have empty `memo` 
    
**6. Check for idempotent creation#1**
<br>**Given**: Scheduled Transaction created with `txBytes`, `admin` and `payer`.
<br>**When**: New Scheduled is created with the same `txBytes`, but different `admin` and `payer`.
<br>**Expect**: New Scheduled Transaction to be created

**7. Check for idempotent creation #2**
<br>**Given**: Scheduled Transaction created with `txBytes`, `admin` and `payer`.
<br>**When**: New Scheduled is created with the same `txBytes` and `admin`, but different `payer`.
<br>**Expect**: New Scheduled Transaction to be created

**8. Check for idempotent creation #3**
<br>**Given**: Scheduled Transaction created with `txBytes`, `admin` and `payer`.
<br>**When**: New Scheduled is created with the same `txBytes` and `admin`, but different `payer`.
<br>**Expect**: New Scheduled Transaction to be created

**9. Check for idempotent creation #4**
<br>**Given**: Scheduled Transaction created with `txBytes`, `admin` and `payer`.
<br>**When**: New Scheduled is created with the same `txBytes` and `payer`, but different `admin`.
<br>**Expect**: New Scheduled Transaction to be created

**10. Check for idempotent creation #5**
<br>**Given**: Scheduled Transaction created with `txBytes`, `admin` and `payer`.
<br>**When**: New Scheduled is created with the same `txBytes`, `admin` and `payer`.
<br>**Expect**:
 - No new Scheduled Transaction created.
 - Signatures of the second transaction have been appended to the first one.
 - `ScheduleID` is populated in the Transaction Receipt with the same ID as of the first Transaction. 

**11. Check for TX creation after previous identical TX execution**
<br>**Given**:
1. Scheduled Transaction created with `txBytes`, `admin` and `payer`.
2. Append required signatures for the transaction using `ScheduleSign`
3. Transaction Executes
<br>**When**: New Scheduled is created with the same `txBytes`, `admin` and `payer`.
<br>**Expect**: New Scheduled Transaction to be created (even though `txBytes`, `admin` and `payer` are the same) 



- create scheduled tx, wait for tx to expire, create second one (with same tx bytes, admin and payer) and check that new TX was created
- create scheduled tx, append all required signatures (tx executes), create second one (with same tx bytes, admin and payer) and check that new TX was created

### Negative
 
## Signing

### Happy Paths

## Deletion

### Happy Paths

## Querying 

### Happy Paths

## Execution

### Happy Paths

### Negative

- create scheduled transaction that is actually a scheduled transaction (schedule-ception), append all required signatures. Make sure that the scheduled transaction is triggered for execution fails.

