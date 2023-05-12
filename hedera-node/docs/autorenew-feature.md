# The Entity Auto Renewal feature

## Goal
- Prior to the implementation of the Entity Auto Renewal feature, the expiration time of a Hedera entity, which is specified in the HAPI document, has not been checked or enforced. An entity remains active in the ledger even after its expiration time, without additional fees being charged. Upon implementation of this feature, Hedera Services will __begin to charge rent__ for automatically renewed entities; and will remove from the ledger expired entities which are either deleted or have an admin/autorenew account with zero balance at the time renewal fees are due.

## Design
- Introduce new settings in `application.properties`:
  * `autorenew.isEnabled`
  * `autorenew.numberOfEntitiesToScan`
  * `autorenew.maxNumberOfEntitiesToRenewOrDelete`
  * `autorenew.gracePeriod`
- Each Hedera entity has an `expirationTime` which is the effective consensus timestamp at (and after) which the entity is set to expire.
- Each Hedera entity also has an `autoRenewAccount` which is the account to pay for the fees at renewal. A crypto account or a smart contract is its own `autoRenewAccount` account.
- When a Hedera entity is created, the payer account is charged enough hbars (as a rental fee) for the entity to stay active in the ledger state until consensus time passes its `expirationTime`. At its expiration time, Hedera Services will try to extend an entity's expiration time by another `autoRenewPeriod` if the `autoRenewAccount` has enough balance to do so, or as much extension as the remaining balance permits.
- `autoRenewPeriod` is a field to customize the extension period in seconds for a crypto account, a topic, a smart contract, or a token type. For a file, the extension period will be three months. Future protobuf changes will permit customizing this extension period as well. Entities associated with new services in the future will also have an `autoRenewPeriod` and an associated autorenew account.
- A Hedera entity that lacks a funded `autoRenewAccount`, namely `autoRenewAccount` is not specified or has zero balance at the time renewal fees are due, will be marked as expired. The entity will then have a grace period (defaulted to 7 days) before removal (permanent deletion). At the end of the grace period, it will again try to autorenew, but if there is no account, or the balance is zero, then it will be permanently removed at that time.
- A Hedera entity can have its expiration time extended by anyone, not just by the owner or admin account. The expiration time is the only field that can be changed in an update without being signed by the owner or the admin. It is also the only field that can be changed while expired (during the grace period).
- Every token type has an associated account as its treasury account. An expired account that is permanently removed (not being renewed after the grace period) will have its tokens transfer to the treasury account. The treasury account always has an expiration time the same as or later than its token type, because extending the latter will automatically extend the former, if needed. So the treasury account can’t expire unless the token type itself expires, in which case, all tokens of that type expire.
- After handling a transaction, all Hedera Services nodes will perform a synchronous scanning of the next `autorenew.numberOfEntitiesToScan` for up to `autorenew.maxNumberOfEntitiesToRenewOrDelete` entities that expired, then try to autorenew these entities.
- Records of autorenew charges and autodeletion of entities will appear in the record stream, and will be available via mirror nodes. No receipts or records for autorenewal/autodeletion actions will be available via HAPI queries.
- Crypto accounts will be prioritized for autorenewal, followed by consensus topics, tokens, smart contracts and files. Schedule entities do not autorenew, and are always removed from the ledger when they expire.
- There is __no change in existing protobufs__. Account and entity owners must ensure that linked autorenew and admin accounts have sufficient balances for autorenewal fees, or risk permanent removal of their entity!
- Accounts who leverage omnibus entities for services including wallets, exchanges, and custody will need to account for the deduction of hbar from any Hedera Entities used in their system at time of autorenewal.
- Every entity will receive one free auto renewal at implementation of this feature. This will have the effect of extending the initial period for autorenewal ~92 days.

## Implementation
Hedera Services will perform a circular scanning of entities, meaning after we reach the last entity in the ledger, we will go back scanning from the first entity in the ledger.

After handling a transaction, when trying to renew an entity:
1. Calculate the fee to extend the entity's `expirationTime` for another `autoRenewPeriod`.
2. If the `autoRenewAccount` of the entity has enough balance to cover this fee:
  - extend the entity's `expirationTime` for another `autoRenewPeriod`.
  - otherwise, translate the remaining balance of the `autoRenewAccount` into an extension, preferably proportional to the fee calculated in step 1, then extend accordingly.
3. If the grace period also passes, permanently delete the entity from the ledger.
4. The consensus timestamp of the autorenewal or autodeletion of the first entity will be 1 nanosecond after the consensus timestamp of the handled transaction. If Hedera Services autorenew or autodelete more than one entity in the same handled transaction, the consensus timestamp of the autorenewal or autodeletion of the next entity will be 1 nanosecond after that of the previous entity.

For restart, reconnect and for controlling the pace of auto renewal/deletion, the following (long) values will be stored in the ledger state:
1. The last scanned entity
2. The consensus timestamp of the last handled transaction
3. The number of entities scanned in this second
4. The number of entities changed (autorenewed/autodeleted) in this second

## Autorenewal record
After autorenewing an entity, Hedera Services will generate a `TransactionRecord` that serves as an autorenewal record and contains the following:

| Field | Type | Label | Description |
|---|---|---|---|
| receipt | TransactionReceipt | | receipt will contain either an accountID, a fileID, a contractID, a topicID or a tokenID that got autorenewed |
| transactionHash | bytes | | empty |
| consensusTimestamp | Timestamp | | The consensus timestamp of the autorenewal |
| transactionID | TransactionID | | { empty transactionValidStart, autoRenewAccount } |
| memo | string | | "Entity {ID} was automatically renewed. New expiry: {newExpiry}" |
| transactionFee | uint64 | | The fee charged for the autorenewal of the entity |
| contractCallResult | ContractFunctionResult | | empty |
| contractCreateResult | ContractFunctionResult | | empty |
| transferList | TransferList | | {(autoRenewAccount, -transactionFee), (defaultFeeCollectionAccount, transactionFee)} |
| tokenTransferLists | TokenTransferList | repeated | empty |
| scheduleRef | ScheduleID | | empty |

The main difference between an autorenewal record and a regular `TransactionRecord` is that it has an empty `transactionHash` and an empty `transactionID.transactionValidStart`. There were older versions of Hedera Services that did not have `transactionHash` in a `TransactionRecord`, but an empty `transactionID.transactionValidStart` will guarantee that the `TransactionRecord` was generated in place of an autorenewal record.

## Entity removal record
After permanently deleting an entity from the ledger due to the zero balance of the `autoRenewAccount`, Hedera Services will generate a `TransactionRecord` that serves as an entity removal record and contains the following:

| Field | Type | Label | Description |
|---|---|---|---|
| receipt | TransactionReceipt | | receipt will contain either an accountID, a fileID, a contractID, a topicID or a tokenID that got autodeleted |
| transactionHash | bytes | | empty |
| consensusTimestamp | Timestamp | | The consensus timestamp of the autodeletion |
| transactionID | TransactionID | | { empty transactionValidStart, autoRenewAccount } |
| memo | string | | "Entity {ID} was automatically deleted." |
| transactionFee | uint64 | | 0 |
| contractCallResult | ContractFunctionResult | | empty |
| contractCreateResult | ContractFunctionResult | | empty |
| transferList | TransferList | | empty |
| tokenTransferLists | TokenTransferList | repeated | empty unless the removed entity was an account that owned units of a non-deleted token, in which case this will record those units being returned to the token's treasury |
| scheduleRef | ScheduleID | | empty |

An entity removal record will be very similar to an autorenewal record. The `memo` will reflect that the entity was automatically deleted. Due to the zero balance of the `autoRenewAccount`, the `transactionFee` is zero and the `transferList` is empty.

## Special notes
- At the time of this writing, __only topics__ have an AUTORENEW_GRACE_PERIOD of 7 days being mentioned in the HAPI document.  `autorenew.gracePeriod` setting, defaulted to 7 days, will be added in `application.properties` to specify the autorenew grace period for __all entities__.
