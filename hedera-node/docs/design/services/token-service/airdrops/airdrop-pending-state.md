# Pending state for airdrops

## Purpose

In order for the `TokenAirdropTransaction` to work we should define a new section of state, where airdrops to be stored. It should be a special storage of incoming airdrop information, that could potentially be used to build `CryptoTransfer` transactions upon it.

The pending airdrop state would be part of the token state, since the airdrops are tightly connected to tokens and we can keep them at the same place.

The additional new `TokenClaimAirdrop` and `TokenCancelAirdrop` transactions would operate tightly with the pending state and it would serve as a mediator between them and the persisted state. These operations would trigger a removal of entities from the pending state. The `claim` operation would delegate a crypto transfer operation and remove the airdrop information, that is being claimed from the pending state and the `cancel` would directly remove the airdrop from the pending state.

## Prerequisite reading
* [HIP-904](https://hips.hedera.com/hip/hip-904)

## Goals

1. Define state structure where to keep the information for incoming airdrops, so that they could be canceled or claimed
2. Define what state transitions inside the pending state would be allowed in the scope of airdrop operations and how changes would be processed
3. Describe how this new state would be serialized and kept after restarts
4. Define Readable and Writable stores for accessing and manipulating the pending airdrops state

## Architecture

The newly defined entity state won’t be able to persist anything in the state directly. If we want to commit something from the pending state to the persisted state, we would have to delegate a given pending airdrop by creating a synthetic crypto transfer transaction, send it to a suitable handler and delete it from the pending state. The writable nature of the pending state would be limited only to adding new entries for a pending airdrop to be transferred or to remove already added ones.

The `WritableStore` of the airdrops, should contain a handler method and if a given pending airdrop is claimed, it could be delegated to the `CryptoTransferHandler` and executed.

Details for `TokenClaimAirdrop` and `TokenCancelAirdrop` would be covered in their own design, docs but basically the `claim` operation would take a pending airdrop and submit it to the delegate function for actual handling and execution. The `cancel` operation would clear an added pending airdrop from the state and it won’t be executed at all.

### Structure

We should define the structure of pending changes that would be kept in the `InitialModServiceTokenSchema` state, so we would reuse the token’s serialized state and add pending airdrops records in it. We could use the type defined in the protobuf records for the airdrop information itself. We should use the protobuf types since they are used for state serialization. So, the structure could look like this:

`StateDefinition.*onDisk*(*AIRDROPS_KEY*, PendingAirdropId.PROTOBUF, PendingAirdropValue.PROTOBUF, MAX__AIRDROPS);`

We can have a case, where we have multiple airdrops for the same fungible token. In this case the  `PendingAirdropValue` would be updated with the new aggregated value.

### Stores

We need to define a special `WritableStore` for the airdrops, so that we can manipulate the content in them. This would be the `WritableAirdropStore`.

It would extend `ReadableAirdropStoreImpl` that implements the following interface:

```java
public interface ReadableAirdropStore {
	PendingAirdropValue get(@NonNull final PendingAirdropId airdropId);
	long sizeOfState();
	void warm(@NonNull final PendingAirdropId airdropId);
    List<PendingAirdropId> getPendingAirdropIdsBySender(final AccountId sender);
}
```

The `ReadableAirdropStoreImpl` would keep an instance of `ReadableKVState<PendingAirdropId, PendingAirdropValue> airdropsState`.

An additional method `getPendingAirdropIdsBySender` would be added to the `ReadableAirdropStore` to get all PendingAirdropIds for a specific sender. This would be useful for traversing all sender's airdrops when their account expires or gets deleted. Then we should iterate over all of their pending airdrops and cancel them.

The `WritableAirdropStore` would add additional methods for operating over the airdrops state, so it would have the following methods:

```java
public class WritableAirdropStore {

    void put(@NonNull final PendingAirdropId airdropId, @NonNull final PendingAirdropValue airdropValue) {
        // invoked by the `TokenAirdropTransaction`
    }

    void remove(@NonNull PendingAirdropId airdropId) {
        // invoked by the `TokenCancelAirdrop`
    }
}
```

### Operations

1. Fetching airdrop values from the collections. This would be applicable only for fungible airdrops (for both `TokenAirdrop` and `TokenClaimAirdrop` transactions).
   For a specific `PendingAirdropId`, we would get the corresponding `PendingAirdropValue`.
2. Handling a new airdrop in state. We can have several cases:
   - Only a single fungible or non-fungible airdrop for a specific token defined - we just put a new entry in the map
   - An airdrop for an existing `PendingAirdropId` is already defined - we should replace the existing `PendingAirdropValue` for this `PendingAirdropId` with the aggregated new fungible amount
3. Handling an airdrop `cancel` in state - we remove the `PendingAirdropId` key for the airdrop we want to cancel
4. Handling an airdrop `claim` in state - we remove the `PendingAirdropId` key for the airdrop after we claim it and create a synthetic crypto transfer for it

## Acceptance tests

The airdrop state correctness would be validated by acceptance tests performed by the `TokenAirdropTransaction`, `TokenCancelAirdrop` and `TokenClaimAirdrop`.

The airdrop state itself could be covered by a set of unit tests, covering different corner cases, as well as positive and negative scenarios.

The main behaviour that should be validated for the airdrop state and its storage is the following:

Note: For all cases the recipient account shouldn’t be associated with the airdropped token.

### State management on airdrop (populate pending airdrop state only)

1. Airdrop a fungible token with a negative amount to Alice → the airdrop should fail and nothing should be saved in the pending state
2. Airdrop a fungible token with a `Long.MAX_VALUE` amount to Alice and then make a second airdrop with the same fungible token and amount with a value of 1 again to Alice → the second airdrop attempt should fail and it’s data shouldn’t be saved in the pending state
3. Airdrop an NFT with a negative serial number to Alice → the airdrop should fail and nothing should be saved in the pending state
4. Airdrop an NFT with a serial number 1 to Alice and then perform another airdrop with the same token and a serial number 2 again to Alice → the airdrop should succeed and all of the airdrop data should be saved in the pending state
5. Airdrop a fungible token with amount X to Alice and the same fungible token with amount Y to Bob → the airdrops should succeed and all airdrop data should be saved in the pending state
6. Airdrop a fungible token with amount X to Alice and the same fungible token with amount Y again to Alice → the airdrops should succeed and the airdrop data should be aggregated having an amount of X + Y and should be saved in the pending state

### State management on cancel (erase pending airdrop state only)

1. Cancel a fungible token airdrop with token X and amount Y to Alice → the state shouldn’t keep the airdrop for Bob with token X at all
2. Cancel an NFT airdrop with token X and serial number 1 to Alice →  the state shouldn’t keep the airdrop for Bob with token X and serial number 1
3. Airdrop an NFT with tokenId X and serial numbers 1 and 2 to Alice. Cancel the airdrop with NFT X and serial number 1 →  the state shouldn’t keep the airdrop for Alice with NFT X and serial number 1 but should continue to keep the airdrop for Alice with NFT X and serial number 2
4. Airdrop a fungible token X with amount Y to Alice and an NFT Z and serial number 1 again to Alice. Cancel the airdrop for fungible token X and for NFT Z with serial number 1 → pending state shouldn’t keep the airdrops for Alice
5. Airdrop a fungible token X with amount Y to Alice and an NFT Z and serial number 1 to Bob. Cancel the airdrop for fungible token X to Alice and for NFT Z and serial number 1 to Bob → pending state shouldn’t keep the airdrops for Alice and Bob
6. Airdrop a fungible token X with amount Y to Alice, an NFT Z and serial number 1 to Bob and the same NFT with serial number 2 to Carol. Cancel the airdrop for NFT Z and serial number 2 to Carol → pending state shouldn’t keep the airdrop to Carol, but should keep the airdrops to Alice and Bob

### Synthetic transfer and execution on claim (perform transfers and change persisted state)

We should also validate that the pending state properly creates and propagates the synthetic transfer in case of airdrop claims.

1. Claim an airdrop with a fungible token X with amount Y airdropped from Alice to Bob
2. Claim an airdrop with an NFT with token X and a serial number 1 airdropped from Alice to Bob
3. Claim multiple fungible airdrops - a fungible token X with amount Y airdropped from Alice to Bob and a fungible token Z with amount Y from Carol to Bob 
4. Claim multiple nft airdrops with the same sender and TokenId but different serial numbers - an NFT X with serial numbers 1 and 2 airdropped to Bob
5. Claim multiple nft airdrops with the same TokenId but different senders and serial numbers - NFT X with serial number 1 airdropped from Alice to Bob and NFT X with serial number 2 airdropped from Carol to Bob
6. Claim multiple nft airdrops with different TokenIds but same sender and serial numbers - NFT X with serial number 1 airdropped from Alice to Bob and NFT Y with serial number 1 airdropped from Alice to Bob
7. Claim multiple nft airdrops with different TokenIds, different serial numbers but same sender - NFT X with serial number 1 airdropped from Alice to Bob and NFT Y with serial number 2 airdropped from Alice to Bob
8. Claim multiple nft airdrops with different TokenIds, different serial numbers and different senders - NFT X with serial number 1 airdropped from Alice to Bob, NFT Y with serial number 2 airdropped from Carol to Bob

For all the cases the WritableStore should create a synthetic crypto transfer with information from the airdrop entry and delegate it to the `CryptoTransferHandler` for execution. The pending state should delete the airdrop entry after the transfer is executed. The operations should be atomic and no pending airdrop should be left after a synthetic transfer.