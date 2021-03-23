# The autorenew feature

## Goal
-	Allow Hedera Services to automatically search for entities such as accounts, files, smart contracts, topics, tokens... that are about to expire and renew them.

## Design
- Introduce new settings in `application.properties`:
  * `autorenew.isEnabled`
  * `autorenew.numberOfEntitiesToCheck`
  * `autorenew.maxNumberOfEntitiesToRenew`
  * `autorenew.gracePeriod`
- Each Hedera entity has an `expirationTime` which is the effective consensus timestamp at (and after) which the entity is set to expire.
- Each Hedera entity also has an `autoRenewAccount` which is the account to pay for the fee at renewal. This `autoRenewAccount` could be itself if the entity is a crypto account or an account associated with the entity when it was created.
- When a Hedera entity is created, its initial lifetime is defined by its `autoRenewPeriod`. At its `expirationTime`, Hedera Services will try to extend an entity's lifetime by another `autoRenewPeriod` if the `autoRenewAccount` has enough balance to do so, or as much extension as the remaining balance permits.
- After handling a transaction, Hedera Services will search within the next `autorenew.numberOfEntitiesToCheck` for upto `autorenew.maxNumberOfEntitiesToRenew` entities that expired then try to renew these entities.
- After the grace period, if the `expirationTime` of an entity is not extended, it will be deleted from the system.

## Implementation
Hedera Services will perform a circular scanning of entities, meaning after we reach the last entity in the system, we will go back scanning from the first entity in the system.

When trying to renew an entity:
1. Calculate the fee to extend the entity's `expirationTime` for another `autoRenewPeriod`.
2. If the `autoRenewAccount` of the entity has enough balance to cover this fee:
  - extend the entity's `expirationTime` for another `autoRenewPeriod`.
  - otherwise, translate the remaining balance of the `autoRenewAccount` into an extension, preferably proportional to the fee calculated in step 1, then extend accordingly.
3. If the grace period also passes, delete the entity from the system.

For restart and reconnect: The last scanned entity must be in the state for synchronization.

## Renewal record
After renewing an entity, Hedera Services will generate a `TransactionRecord` that serves as a renewal record and contains the following:

| Field | Type | Label | Description |
|---|---|---|---|
| receipt | TransactionReceipt | | receipt will contain either an accountID, a fileID, a contractID, a topicID or a tokenID that got renewed |
| transactionHash | bytes | | empty |
| consensusTimestamp | Timestamp | | The consensus timestamp of the renewal |
| transactionID | TransactionID | | { empty transactionValidStart, autoRenewAccount } |
| memo | string | | "Entity {ID} was renewed. New expiry: {newExpiry}" |
| transactionFee | uint64 | | The fee charged for the renewal of the entity |
| contractCallResult | ContractFunctionResult | | empty |
| contractCreateResult | ContractFunctionResult | | empty |
| transferList | TransferList | | {(autoRenewAccount, -transactionFee), (defaultFeeCollectionAccount, transactionFee)} |
| tokenTransferLists | TokenTransferList | repeated | empty |
| scheduleRef | ScheduleID | | empty |

The main difference between a renewal record and a regular `TransactionRecord` is that it has an empty `transactionHash` and an empty `transactionID.transactionValidStart`. There were older versions of Hedera Services that did not have `transactionHash` in a `TransactionRecord`, but an empty `transactionID.transactionValidStart` will guarantee that the `TransactionRecord` was generated in place of a renewal record.

## Deletion record
After deleting an entity due to the zero balance of the `autoRenewAccount`, Hedera Services will generate a `TransactionRecord` that serves as a deletion record and contains the following:

| Field | Type | Label | Description |
|---|---|---|---|
| receipt | TransactionReceipt | | receipt will contain either an accountID, a fileID, a contractID, a topicID or a tokenID that got deleted |
| transactionHash | bytes | | empty |
| consensusTimestamp | Timestamp | | The consensus timestamp of the deletion |
| transactionID | TransactionID | | { empty transactionValidStart, autoRenewAccount } |
| memo | string | | "Entity {ID} was deleted." |
| transactionFee | uint64 | | 0 |
| contractCallResult | ContractFunctionResult | | empty |
| contractCreateResult | ContractFunctionResult | | empty |
| transferList | TransferList | | empty |
| tokenTransferLists | TokenTransferList | repeated | empty |
| scheduleRef | ScheduleID | | empty |

A deletion record will be very similar to a renewal record. The `memo` will reflect that the entity was deleted. Due to the zero balance of the `autoRenewAccount`, the `transactionFee` is zero and the `transferList` is empty.

## Special notes
- At the time of this writing, a file is not associated with an `autoRenewAccount` so a file can only be renewed by a fileUpdate transaction.
- Because cryptoDelete transfers all remaining balance of an account to another account, all accounts that are marked `deleted` will have zero balance. In other words, an account with a non-zero balance will never be marked `deleted`. Step 2 of the implementation does not need to worry about the `autoRenewAccount` being marked `deleted` before.
- At the time of this writing, __only topics__ have an AUTORENEW_GRACE_PERIOD of 7 days being mentioned in the HAPI document. Propose to either __remove__ this grace period or introduce `autorenew.gracePeriod` in `application.properties` to specify the autorenew grace period for __all entities__.
