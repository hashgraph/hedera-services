# Scheduled transactions 

An ordinary transaction must be submitted to the network with the signatures 
of enough Ed25519 keys to activate all the Hedera keys required to sign it. 
Otherwise it will resolve to `INVALID_SIGNATURE`. 

Here we describe a new kind of _scheduled_ transaction that is not directly 
submitted to the network, but rather created as part of a _schedule entity_ in 
the network's action queue. An Ed25519 key "signs" a scheduled transaction
by signing an ordinary transaction that either creates or affirms the 
schedule entity.

Along with a scheduled transaction, a schedule entity (or simply _schedule_) also contains,
  1. An optional memo. 
  2. An optional admin key that can be used to delete the schedule.
  3. An optional account to be charged the service fee for the scheduled transaction.
  4. A list of the Ed25519 keys that the network deems to have signed the scheduled transaction.

The schedule entity type is managed by four new HAPI operations,
  1. The `ScheduleCreate` transaction creates a new schedule (possibly with a non-empty list of signing keys).
  2. The `ScheduleSign` transaction adds one or more Ed25519 keys to a schedule's list of affirmed signers.
  3. The `ScheduleDelete` transaction marks a schedule as deleted; its transaction will not be executed.
  4. The `GetScheduleInfo` query gets the current state of a schedule.

It is important to understand that the bytes of the inner scheduled transaction are never 
directly signed by an Ed25519 key. Only `ScheduleCreate` or `ScheduleSign` bytes
are ever signed, in the ordinary way. 

## Ready-to-execute status

A scheduled transaction is _ready-to-execute_ if its schedule has a list of 
Ed25519 signing keys which, taken together, meet the _signing requirements_ of all the
Hedera keys _prerequisite_ to the scheduled transaction. (See the 
[next section](#prerequisite-signing-keys) for more discussion on prerequisite keys.)

Because Hedera keys can include key lists and threshold keys, sometimes there are 
many different lists of Ed25519 signing keys which could meet a Hedera key's signing 
requirements. 

For example, suppose Alice has an account whose key is a 1-of-3 threshold made up of 
Ed25519 keys `KeyA`, `KeyB`, and `KeyC`.  As long as any of `KeyA`, `KeyB`, or `KeyC` have signed, 
the signing requirement for the Hedera key on Alice's account is met.

(We often say a Hedera key has "signed a transaction" when enough Ed25519 keys
have signed to meet the Hedera key's signing requirements; but notice this is 
a bit imprecise---only Ed25519 keys ever "sign" in the cryptographic sense.)

### Prerequisite signing keys

First, if a schedule lists an account to be charged the service fee for
its scheduled transaction, the Hedera key of that account is prerequisite 
to the scheduled transaction.

Second, if some non-payer Hedera key would need to sign a scheduled transaction 
if it was submitted directly to the network, that non-payer key is prerequisite
to the scheduled transaction. Consider three examples.
  1. A scheduled `CryptoTransfer` of 1ℏ from account `0.0.X` to account `0.0.Y`,
     which has `receiverSigRequired=true`. The keys on accounts `0.0.X` and
     `0.0.Y` are both prerequisite.
  2. A scheduled `SubmitMessage` to a topic `0.0.Z` which has a submit key. The
     submit key on topic `0.0.Z` is prerequisite.
  3. A scheduled `TokenMint` for a token `0.0.T` with a supply key. The supply
     key on token `0.0.T` is prerequisite. (Although `TokenMint` is 
     not currently in the [scheduling whitelist](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/resources/bootstrap.properties#L64),
     eventually all transaction types will be.)

## Triggered execution

A schedule is _triggered_ when the network handles a `ScheduleCreate` 
or `ScheduleSign` that adds enough signing keys to make its scheduled
transaction ready-to-execute. (You may create a schedule whose initial 
signing keys are already triggering.) A scheduled transaction executes 
immediately after its schedule is triggered. 

It is crucial to understand that in Hedera Services 0.13.0, a scheduled 
transaction **only** executes when its schedule is triggered by a 
`ScheduleCreate` or `ScheduleSign`! The network does not proactively monitor 
how changes to Hedera keys may impact the ready-to-execute status of 
existing scheduled transactions.
 
For example, suppose the Hedera key on account `0.0.X` is a key list of two
Ed25519 keys `KeyA` and `KeyB`; and is the sole prerequisite to a scheduled transaction 
whose schedule `0.0.S` already lists `A` as a signer. Say we now update account 
`0.0.X` to remove `KeyB` from its key. Schedule `0.0.S` is **not** automatically
triggered! We need to submit a `ScheduleSign` for `0.0.S` to trigger it. (This
`ScheduleSign` can be signed by just a payer key, since we made the scheduled
transaction ready-to-execute by weakening the signing requirement for the key
on account `0.0.X`.)

## The `ScheduleCreate` transaction
  
```  
message ScheduleCreateTransactionBody {  
  SchedulableTransactionBody scheduledTransactionBody = 1; // The scheduled transaction
  string memo = 2; // An optional memo with a UTF-8 encoding of no more than 100 bytes
  Key adminKey = 3; // An optional Hedera key which can be used to sign a `ScheduleDelete` and make the schedule un-triggerable
  AccountID payerAccountID = 4; // An optional id of the account to be charged the service fee for the scheduled transaction at the consensus time that it executes (if ever); defaults to the `ScheduleCreate` payer if not given
}  
```  

The new `SchedulableTransactionBody` message is a strict subset of the `TransactionBody` message which omits the
top-level `TransactionID`, `nodeAccountID`, and `transactionValidDuration` fields; and does not allow the 
`ScheduleCreateTransactionBody` and `ScheduleSignTransactionBody` messages in its `data` element. Any 
unknown fields in the submitted `SchedulableTransactionBody` will be ignored, although they _will_ affect
the "identity" of the schedule (see [below](#receipts-and-duplicate-creations)).

As with all other entity types, a schedule remains in network state until it expires, even if its scheduled 
transaction has already been executed or it has been marked deleted.

### Paying for scheduled transactions

If the `ScheduleCreate` gives a `payerAccountID`, the network will charge this payer the service fee 
for the scheduled transaction at the consensus time that it executes (if ever). If no such payer is specified, 
the network will charge the payer of the originating `ScheduleCreate`.

### Receipts and duplicate creations

When a `ScheduleCreate` resolves to `SUCCESS`, its receipt includes a new `ScheduleID` field
with the id of the created schedule. Its receipt _also_ includes a new 
`scheduledTransactionID` field with the `TransactionID` that can be used to query for the 
record of the scheduled transaction's execution. 

This `scheduledTransactionID` will be the `TransactionID` of the `ScheduleCreate`, 
with a new additional field `scheduled=true`.  However, clients should always use the id 
from the receipt instead of relying on this correspondence.

#### Duplicate creations
There is a special case in which a `ScheduleCreate` transaction tries to re-create a
schedule that already exists in state. When this happens, the `ScheduleCreate` resolves 
to a new status of `IDENTICAL_SCHEDULE_ALREADY_CREATED`, and the `ScheduleID` in its receipt points
to the existing schedule. (It also contains the `TransactionID` used to query for the
record of the existing scheduled transaction.) A client receiving `IDENTICAL_SCHEDULE_ALREADY_CREATED` 
can then submit a `ScheduleSign` (see below) with the given `ScheduleID`, signing with
the same Ed25519 keys it used for its own create attempt. 

Two <tt>ScheduleCreate</tt> transactions are <i>identical</i> if they are equal in all their fields 
other than, possibly, <tt>payerAccountID</tt>. (Here "equal" should be understood in the sense of 
gRPC object equality in the network software runtime. In particular, a gRPC object with 
[unknown fields](https://developers.google.com/protocol-buffers/docs/proto3#unknowns)
is not equal to a gRPC object without unknown fields, even if they agree on all known fields.)
  
### Enforced checks and the scheduling whitelist

The only body-specific precheck enforced for a `ScheduleCreate` transaction is that the 
`memo` field is valid. At consensus, the following checks are enforced:
  1. The `memo` must be valid.
  2. The type of the `scheduledTransactionBody` must be in the `scheduling.whitelist`; and it 
     must reference only non-deleted entities that exist in state at the time the `ScheduleCreate` 
     reaches consensus. 
  3. The `adminKey` must be valid, if present.
  
## The `ScheduleSign` transaction
  
```  
message ScheduleSignTransactionBody {  
  ScheduleID scheduleID = 1; // The id of an existing schedule to affirm signing keys for
}  
```  

Suppose we use the `scheduleID` field to point to schedule `0.0.12345`, which contains a 
scheduled transaction `X`. If any of the Ed25519 keys required to sign `X` sign 
our `ScheduleSign` ordinary transaction, then the network will update the state of 
schedule `0.0.12345` with these keys.

(It is permissible, though pointless, to sign the `ScheduleSign` with keys not 
required for the execution of `X`.)

### Receipts

When a `ScheduleSign` resolves to `SUCCESS`, its receipt includes a new 
`scheduledTransactionID` field which is the `TransactionID` that can be used to query 
for the record of the execution of the scheduled transaction in the signed schedule.
  
## The `ScheduleDelete` transaction
  
```  
message ScheduleDeleteTransactionBody {  
  ScheduleID scheduleID = 1; // The id of an existing schedule to delete
}  
```  

When a `ScheduleDelete` resolves to `SUCCESS`, its target schedule is marked as 
as deleted. Any future attempts to trigger this schedule will result in `SCHEDULE_WAS_DELETED`.
However, the schedule will remain in network state until it expires. (Note that if we try 
to delete a schedule that already executed, our `ScheduleDelete` will resolve
to `SCHEDULE_WAS_EXECUTED` and have no effect.)
  
## The `ScheduleGetInfo` query
  
```  
message ScheduleGetInfoQuery {  
  QueryHeader header = 1; // Standard query metadata, including payment
  ScheduleID schedule = 2; // The id of an existing schedule
}  
  
message ScheduleGetInfoResponse {  
  ScheduleID scheduleID = 1; // The id of the schedule
  Timestamp deletionTime = 2; // If the schedule has been deleted, the consensus time when this occurred
  Timestamp executionTime = 3; // If the schedule has been executed, the consensus time when this occurred
  Timestamp expirationTime = 4; // The time at which the schedule will expire
  SchedulableTransactionBody scheduledTransactionBody = 5; // The scheduled transaction
  string memo = 6; // The publicly visible memo of the schedule
  Key adminKey = 7; // The key used to delete the schedule from state
  KeyList signers = 8; // The Ed25519 keys the network deems to have signed the scheduled transaction
  AccountID creatorAccountID = 9; // The id of the account that created the schedule
  AccountID payerAccountID = 10; // The id of the account responsible for the service fee of the scheduled transaction
  TransactionID scheduledTransactionID = 11; // The transaction id that will be used in the record of the scheduled transaction (if it executes)
}  
```  
  
## Records of scheduled transactions

Just as an ordinary transaction, a scheduled transaction that achieves execution has a unique consensus timestamp.
It also has a record in the record stream. However, there _are_ two items that distinguish the record of a 
scheduled transaction.
  1. Its `TransactionID` will contain the new `scheduled=true` flag.
  2. It will have a new `scheduleRef` field with the `ScheduleID` of the schedule that managed its execution.
