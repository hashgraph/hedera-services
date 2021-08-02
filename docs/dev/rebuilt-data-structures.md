# Rebuilt data structures

The ground truth of the Services world state is the Merkle tree
with `ServicesState` at the root. We can think of `handleTransaction()` 
as a pure reduction that combines the current world state and a consensus 
transaction into a new consensus world state.

```
handleTransaction: (world_state, consensus_transaction) -> new_world_state
```

If this was _literally_ true, then the only stateful objects in the system
would belong to the Merkle type hierarchy. In fact it is an idealization.

To implement `handleTransaction()` efficiently, we must build, and maintain, 
non-Merkle objects that "denormalize" the Merkle tree. These denormalized 
views of state are used to, for example, classify duplicate transactions;
and can thus change the outcome of handling a transaction. 

:information_desk_person:&nbsp;We often call these views 
**rebuilt data structures** to emphasize the restart and reconnect phases 
in the lifecycle of a Services node must give such structures first-class
consideration.

## Lifecycle consistency 

Suppose node `A` has been active for the entire lifetime of a network; while 
node `R` hit a network partition, missed several hours of consensus transactions,
and only reconnected recently.

Both will use a non-Merkle data structure to classify duplicate transactions
(specifically, a map from `TransactionID`s to `TxnIdRecentHistory`s). Clearly,
when `R` reconnects, it must be able to **rebuild from its Merkle state** the 
exact classification map that `A` had when it was handling the next consensus 
transaction given that state.

This is a problem of _consistency_. 

:warning:&nbsp;No matter how a node derives its non-Merkle state 
views, whether one transaction at a time, or all at once from a state learned 
via reconnect or found at restart---all nodes must always see the same views 
for the same state.

## Intersecting transaction types

Let `x` be a rebuilt data structure. In general, not every type of HAPI 
transaction will change `x` when handled; but suppose transaction type 
`f` does change `x` during `handleTransaction()`. Then we say that 
transaction type `f` **intersects** the rebuilt data structure `x`.
The **main effect** of `f` is still on the Merkle world state; but this
**side effect** on `x` must be given equal consideration. 

To achieve consistency for `x`, we must be able to rebuild all the 
side effects of intersecting transactions `f` from their main effects 
on the Merkle state.

## List of rebuilt data structures

The following rebuilt data structures exist in Services:
1. The [`existingRels`](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/ledger/accounts/BackingTokenRels.java#L46) set of non-dissociated token-account relationships.
2. The [`existingAccounts`](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/ledger/accounts/BackingAccounts.java#L35) set of non-removed accounts.
3. The [`extantSchedules`](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/store/schedule/HederaScheduleStore.java#L70) map from each extant scheduled transaction to its schedule entity id.
4. The [`knownTreasuries`](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/store/tokens/HederaTokenStore.java#L138) map from each token treasury account to the set of tokens which it serves.
5. The [`uniqueTokenAssociations`](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/ServicesState.java#L462) one-to-many relation from each unique token type to its minted NFTs.
6. The [`uniqueOwnershipAssociations`](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/ServicesState.java#L466) one-to-many relation from each account to its owned NFTs.
7. The [`uniqueTreasuryOwnershipAssociations`](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/ServicesState.java#L470) one-to-many relation from each unique token type to its treasury-owned NFTs.
8. The [`txnHistories`](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/context/ServicesContext.java#L1599) map from the id of each transaction handled in the last 180s of consensus time to the "recent history" of that id.
9. The [`payerRecordExpiries`](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/state/expiry/ExpiryManager.java#L74) queue of the pending expirations of the payer records in state.
10. The [`shortLivedEntityExpiries`](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/state/expiry/ExpiryManager.java#L76) priority queue of the pending expirations of the scheduled transactions in state.

Here we document how the side effects of each structure's intersecting
transactions can be consistently rebuilt from the main effects in state.

### The `existingRels` set

```
Set<Pair<AccountID, TokenID>> existingRels = new HashSet<>();
```

This structure gives a small performance boost when checking if an
`AccountID` and `TokenID` are associated, as it avoids both (1) 
converting from gRPC to Merkle types; and (2), acquiring a read lock 
on the `tokenAssociations` FCM. 

It will be removed as part of the ongoing HTS "layered architecture" 
refactor, likely in the 0.17.0 release.

:gear:&nbsp;Any transaction that associates or dissociates tokens will 
change the `existingRels` structure. These include the following HAPI 
functions: `TokenCreate`, `TokenAssociateToAccount`, and 
`TokenDissociateFromAccount`.

Notice that `TokenUpdate` and `TokenDelete` do _not_ change the set of 
token associations at this time.

#### Consistency audit

:building_construction:&nbsp;**Rebuilding** this structure consists of 
[iterating over associations in state and adding each to the set](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/ledger/accounts/BackingTokenRels.java#L57). 

:memo:&nbsp;**Maintaining** this structure works as below.
1. For `TokenCreate`, all accounts are auto-associated using the
[`HederaTokenStore.associate()` method](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/store/tokens/HederaTokenStore.java#L201). Since this method delegates to the
`TransactionalLedger.create()` method, its side-effect on `existingRels` 
is guaranteed by the consistency of the [`BackingTokenRels.put()` method](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/ledger/accounts/BackingTokenRels.java#L74) itself.
2. For `TokenAssociateToAccount` and `TokenDissociateFromAccount`, 
associations change via `TokenRelationship`s persisted via the 
[`TypedTokenStore.persistTokenRelationships()` method](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/store/TypedTokenStore.java#L172). New associations propagate to 
`existingRels` via the [`BackingTokenRels.addToExistingRels()`](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/store/TypedTokenStore.java#L201) method; and 
removed associations propagate via the [`BackingTokenRels.removeFromExistingRels()`](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/store/TypedTokenStore.java#L179) method.

### The `existingAccounts` set

```
Set<AccountID> existingAccounts = new HashSet<>();
```

This structure provides a small performance boost when checking if a 
given `AccountID` refers to a non-removed account, as it avoids both (1)
converting from gRPC to Merkle types; and (2), acquiring a read lock 
on the `accounts` FCM. 

It will be removed as the ongoing "layered architecture" refactor extends
to non-token operations, possibly even in the 0.17.0 release.

:gear:&nbsp;Only the `CryptoCreate` HAPI operation can change the 
`existingAccounts` structure (since a `CryptoDelete` just marks an account
as deleted, but does not remove it from state). _If_ auto-removal is active,
then the [`RenewalHelper.removeLastClassifiedAccount()` method](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/state/expiry/renewal/RenewalHelper.java#L129) can also
affect `existingAccounts`.

#### Consistency audit

:building_construction:&nbsp;**Rebuilding** this structure consists of 
[iterating over accounts in state and adding each to the set](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/ledger/accounts/BackingAccounts.java#L46). 

:memo:&nbsp;**Maintaining** this structure works as below.
1. For `CryptoCreate`, accounts are created via the `TransactionLedger.create()`;
thus the problem reduces to consistency of the 
[`BackingAccounts.put()` method](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/ledger/accounts/BackingAccounts.java#L58).
2. For auto-removal, changes are propagated from the `RenewalHelper` 
via the [`BackingAccounts.remove()` method](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/state/expiry/renewal/RenewalHelper.java#L145).

### The `extantSchedules` map

```
Map<MerkleSchedule, MerkleEntityId> extantSchedules = new HashMap<>();
```

This structure allows a node to efficiently detect 
[re-creation of an existing schedule](https://github.com/hashgraph/hedera-services/blob/master/docs/scheduled-transactions/revised-spec.md#duplicate-creations), resolving a `ScheduleCreate` to `IDENTICAL_SCHEDULE_ALREADY_CREATED` 
instead of `SUCCESS`.

:gear:&nbsp;The only HAPI operation that can change `extantSchedules`
is `ScheduleCreate`. But this map must be updated whenever a schedule 
entity is expired from state, which can happen as a consequence of 
handling any transaction type.

#### Consistency audit

:building_construction:&nbsp;**Rebuilding** this structure consists of 
[iterating over the schedules in state and updating the map with each](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/store/schedule/HederaScheduleStore.java#L184). 

:memo:&nbsp;**Maintaining** this structure works as below.
1. For `ScheduleCreate`, the [`HederaScheduleStore.commitCreation()` method](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/store/schedule/HederaScheduleStore.java#L152) 
maintains consistency between the state FCM and `extantSchedules`.
2. For schedule expiration, again consistency is achieved by
updating state and view together in the [`HederaScheduleStore.expire()` method](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/store/schedule/HederaScheduleStore.java#L222).

### The `knownTreasuries` map

```
Map<AccountID, Set<TokenID>> knownTreasuries = new HashMap<>();
```

This structure has three uses:
1. It allows the [`CryptoDelete` transition logic](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/txns/crypto/CryptoDeleteTransitionLogic.java#L77) to efficiently
resolve to `ACCOUNT_IS_TREASURY` when the target account is the 
treasury of a non-deleted token.
2. It allows the `GetAccountNftInfos` query logic to find all the
NFTs an account owns by [virtue of its role as token treasury](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/store/tokens/views/TreasuryWildcardsUniqTokenView.java#L98).
3. It helps the [`HederaTokenStore.changeOwner()` method](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/store/tokens/HederaTokenStore.java#L342) determine when an
NFT is exiting from or returning to its token treasury.

Note the second and third uses are both suspect, and should be 
eliminated.

In the second use, because `knownTreasuries` is not fast-copyable, concurrent 
`GetAccountNftInfos` queries could return different answers from the 
`FCOneToManyRelation`s in the _same signed state_, if those queries 
happen to be "racing" a `TokenUpdate` that affects the target account's 
treasury role. (But this can wait until state proofs are enabled.)

In the third use, it is safer to simply retrieve from state the tokens 
involved in the NFT ownership change and identify their treasuries directly.

:gear:&nbsp;The `TokenCreate`, `TokenUpdate`, `TokenDelete` HAPI operations 
can all change `knownTreasuries`. (Even though a deleted token remains
in state, and a `GetTokenInfo` query will return its final treasury account,
that account is no longer _functionally_ a treasury.)

#### Consistency audit

:building_construction:&nbsp;**Rebuilding** this structure consists of 
[iterating over the non-deleted tokens in state and updating the map from each](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/store/tokens/HederaTokenStore.java#L163). 
The "non-deleted" qualifier is critical, since the sole _necessary_
use of `knownTreasuries` in guarding `CryptoDelete` logic no longer 
applies for deleted tokens.

:memo:&nbsp;**Maintaining** this structure works as below.
1. For `TokenCreate`, the [`HederaTokenStore.commitCreation()` method](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/store/tokens/HederaTokenStore.java#L627) 
simultaneously updates the state FCM and `knownTreasuries`.
2. For `TokenUpdate`, the [`HederaTokenStore.update()` method](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/store/tokens/HederaTokenStore.java#L771) ensures
`knownTreasuries` reflects any treasury change.
3. For `TokenDelete`, the [`HederaTokenStore.delete()` method](https://github.com/hashgraph/hedera-services/blob/master/hedera-node/src/main/java/com/hedera/services/store/tokens/HederaTokenStore.java#L645) removes
the mapping for a deleted token's treasury from `knownTreasuries`.


