# Scheduled Transactions -- BDD tests specification

Listed below are the set of bdd tests defined for Scheduled Transactions.

## Creation

### Happy paths

**1. Should be able to create Scheduled Transaction with `transactionBody` only**

**Expect**:
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

**Expect**:
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

**Expect**:
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

**Expect**:
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

**Expect**: 
- New Schedule entity must be created and its ID must be specified in the `TransactionReceipt`'s `ScheduleID` property.
- When querying the Schedule Entity by the `ScheduleID` form the `transactionReceipt`:
    - it must have the same `ScheduleID` as specified in the `TransactionReceipt`
    - it must have `creatorAccountID` set, equal to the `transaction sender` of the `schedule create` transaction
    - it must have `payerAccountID` set, equal to the `transaction sender` of the `schedule create` transaction
    - it must have the same `transactionBody` bytes retrieved
    - **it must have a set of `signers` populated. The signers must correspond to the simple `Key`(s) whose signature has been provided.** 
    - it must have empty `adminKey`
    - it must have empty `memo` 
    
**6. Check for idempotent creation #1**
<br>**Given**: Scheduled Transaction created with `txBytes`, `admin` and `payer`.
<br>**When**: New Scheduled is created with the same `txBytes`, but different `admin` and `payer`.
<br>**Expect**: New Scheduled Transaction to be created.

**7. Check for idempotent creation #2**
<br>**Given**: Scheduled Transaction created with `txBytes`, `admin` and `payer`.
<br>**When**: New Scheduled is created with the same `txBytes` and `admin`, but different `payer`.
<br>**Expect**: New Scheduled Transaction to be created.

**8. Check for idempotent creation #3**
<br>**Given**: Scheduled Transaction created with `txBytes`, `admin` and `payer`.
<br>**When**: New Scheduled is created with the same `txBytes` and `admin`, but different `payer`.
<br>**Expect**: New Scheduled Transaction to be created.

**9. Check for idempotent creation #4**
<br>**Given**: Scheduled Transaction created with `txBytes`, `admin` and `payer`.
<br>**When**: New Scheduled is created with the same `txBytes` and `payer`, but different `admin`.
<br>**Expect**: New Scheduled Transaction to be created.

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

**When**: New Scheduled is created with the same `txBytes`, `admin` and `payer`.
<br>**Expect**: New Scheduled Transaction to be created (even though `txBytes`, `admin` and `payer` are the same) 

**12. Check for TX creation after previous identical TX expiry**
<br>**Given**:
0. Configure very low `txExpirySecs`
1. Scheduled Transaction created with `txBytes`, `admin` and `payer`.
2. Wait for Transaction to expire

**When**: New Scheduled is created with the same `txBytes`, `admin` and `payer`.
<br>**Expect**: New Scheduled Transaction to be created (even though `txBytes`, `admin` and `payer` are the same) 

### Negative

**1. Creating Scheduled Transaction with non-existing `payerAccountID` must fail**
<br>**When**: New Scheduled transaction created with non-existing `payerAccountID`
<br>**Expect**: The `ScheduleCreate` operation to fail with `` TODO response code.

**2. Creating Scheduled Transaction with non-required signature must fail.**
<br>**When**: New Scheduled transaction created with   
  

- creating scheduled transaction and providing signature in the `sigMap` signed by a Key that is not required for the execution of the scheduled transaction must fail.
- creating scheduled transaction with `memo` more than `100 bytes` should fail
- creating scheduled TX, set `sigMap` with invalid format -> should fail
- creating scheduled TX, set `sigMap` signing different transaction -> should fail  
 
## Signing

### Happy Paths
**1. Collecting signatures for Scheduled Transaction**
<br>**Given**: Scheduled Transaction created with `txBody` requiring 3 simple `Keys` to sign.
<br>**When**: Schedule Sign executed providing 2 signatures from `2` of the required `Keys`.
<br>**Expect**: Scheduled Transaction to have the 2 `Keys` set as signers.

- TODO think of a use-case with empty sigMap triggering the execution?

### Negative
- try to perform schedule sign on already executed TX -> should fail
- try to perform schedule sign on non-existing TX -> should fail
- create scheduled TX, try to add invalid signatures (with invalid format) -> should fail
- create scheduled TX, try to add invalid signatures (signing different transaction) -> should fail
- create scheduled TX, try to add invalid signatures (signing keys are not one of the required ones) -> should fail
- create scheduled TX (token mint), change the mint key, try to add signature from the previous mint key -> should fail (as it is not required anymore)

## Deletion
### Happy Paths

- Create scheduled TX, execute schedule Delete on it. When queried again, should resolve to not found.

### Negative Paths

- Performing schedule delete on scheduled TX with no Admin key should fail
- Performing schedule delete on scheduled TX with different from the Admin key sig should fail 
- Perform schedule delete on already deleted TX -> should fail
- Perform schedule delete on already executed TX -> should fail
- Perform schedule delete on expired TX -> should fail
- Perform schedule delete on non-existing TX -> should fail

## Execution

### Happy Paths

### Negative

- create scheduled transaction that is actually a scheduled transaction (schedule-ception), append all required signatures. Make sure that the scheduled transaction is triggered for execution fails.

