# Rebuilt data structures

The ground truth of the Services world state is exactly the Merkle tree
with `ServicesState` at the root. We can think of `handleTransaction()` 
as a pure reducer that combines the current world state and a consensus 
transaction into the new consensus world state.

```
handleTransaction: (world_state, consensus_transaction) -> new_world_state
```

If this was _literally_ true, then the only stateful objects in the system
would belong to the Merkle type hierarchy. In fact it is an idealization.

To implement `handleTransaction()` efficiently, we must build, and maintain, 
non-Merkle objects that "denormalize" the Merkle tree. These denormalized 
views of state are used to, for example, classify duplicate transactions;
and can thus change the outcome of handling a transaction. 

We often call these views **rebuilt data structures** to emphasize they need 
first-class consideration during the restart and reconnect phases in the 
lifecycle of a Services node.

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

:warning:&nbsp;That is, no matter how a node derives its non-Merkle state 
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
accumulated side effects of intersecting transactions `f` from the main 
effects of those `f` on the Merkle state.

## List of known rebuilt data structures

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

This structure provides a small performance boost when checking if a 
given `AccountID` and `TokenID` are associated, as it avoids both (1)
converting from gRPC to Merkle types; and (2), acquiring a read lock 
on the `tokenAssociations` FCM. 

It will be removed as part of the ongoing HTS refactor, likely even in 
the 0.17.0 release.

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
