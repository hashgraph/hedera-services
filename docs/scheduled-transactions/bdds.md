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
    
    
- create scheduled tx, create second one (only with same tx bytes) and check that new one was created
- create scheduled tx, create second one (with same tx bytes, admin) and check that new one was created
- create scheduled tx, create second one (with same tx bytes, admin and payer) and check that sigs were appended
- create scheduled tx, append all required signatures (tx executes), create second one (with same tx bytes, admin and payer) and check that new TX was created
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

