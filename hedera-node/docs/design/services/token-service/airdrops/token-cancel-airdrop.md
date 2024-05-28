# Introduce TokenCancelAirdrop
## Purpose

We want to provide the option for an airdrop sender to cancel an airdrop that have been sent previously by them. That would be useful if the airdrop sender has made a mistake or the receiver hasn't claimed the airdrop for too long. This would be a way to remove ongoing fees for auto renewal that would be charged from the airdrop sender account.

## Goals

1. Define new `TokenCancelAirdrop` HAPI transaction
2. Implement token cancel airdrop transaction handler logic

## Non-Goals

- Implement token cancel airdrop in system contract functions

## Architecture

The implementation related to the new `TokenCancelAirdrop` transaction will be gated behind a `tokens.airdrops.cancel.enabled` feature flag.

### HAPI updates

Create new transaction type as defined in the HIP:

```protobuf
/**
 * Token cancel airdrop<br/>
 * Remove one or more pending airdrops from state on behalf of the sender(s)
 * for each airdrop.<br/>
 *
 * Each pending airdrop canceled SHALL be removed from state and SHALL NOT be available to claim.
 *
 * Each cancellation SHALL be represented in the transaction body and SHALL NOT be restated
 * in the record file.
 * All cancellations MUST succeed for this transaction to succeed.
 */
message TokenCancelAirdropTransactionBody {
    /**
     * A list of one or more pending airdrop identifiers.<br/>
     * This transaction MUST be signed by the account referenced by a `sender_id` for
     * each entry in this list.
     * <p>
     * This list MUST contain between 1 and 10 entries, inclusive.
     * This list MUST NOT have any duplicate entries.<br/>
     */
    repeated PendingAirdropId pending_airdrops = 1;
}

/**
 * A unique, composite, identifier for a pending airdrop.
 *
 * Each pending airdrop SHALL be uniquely identified by a PendingAirdropId.
 * A PendingAirdropId SHALL be recorded when created and MUST be provided in any transaction
 * that would modify that pending airdrop (such as a `claimAirdrop` or `cancelAirdrop`).
 */
message PendingAirdropId {
    /**
     * A sending account.<br/>
     * This is the account that initiated, and SHALL fund, this pending airdrop.<br/>
     * This field is REQUIRED.
     */
    AccountID sender_id = 1;

    /**
     * A receiving account.<br/>
     * This is the ID of the account that SHALL receive the airdrop.<br/>
     * This field is REQUIRED.
     */
    AccountID receiver_id = 2;

    oneof token_reference {
        /**
         * A token ID.<br/>
         * This is the type of token for a fungible/common token airdrop.<br/>
         * This field is REQUIRED for a fungible/common token and MUST NOT be used for a
         * non-fungible/unique token.
         */
        TokenID fungible_token_type = 3;

        /**
         * The id of a single NFT, consisting of a Token ID and serial number.<br/>
         * This is the type of token for a non-fungible/unique token airdrop.<br/>
         * This field is REQUIRED for a non-fungible/unique token and MUST NOT be used for a
         * fungible/common token.
         */
        NftID non_fungible_token = 4;
    }
}
```

Add new RPC to `TokenService` :

```protobuf
service TokenService {

//    ...

    /**
     * Cancel one or more pending airdrops.<br/>
     * This transaction MUST be signed by _each_ account *sending* an airdrop to be canceled.
     */
    rpc cancelAirdrop (Transaction) returns (TransactionResponse);
}
```

### Fees

A sender may cancel pending transfers for a low nominal fee using the new `TokenCancelAirdrop` transaction. The fee should be proportional to the number of airdrops being canceled in the transaction.

An update into the `feeSchedule` file would be needed to specify that.

### Services updates

- Update `ApiPermissionConfig` class to include a `0-* PermissionedAccountsRange` for the new `TokenCancelAirdrop` transaction type
- Update `TokenServiceDefinition` class to include the new RPC method definition for cancelling airdrops
- Implement new `TokenCancelAirdropHandler` class which should be invoked when the gRPC server handles `TokenCancelAirdrop` transactions. The class should be responsible for:
    - Pure checks: validation logic based only on the transaction body itself in order to verify if the transaction is valid one
        - Verify that the pending airdrops list contains between 1 and 10 entries, inclusive
        - Verify that the pending airdrops list does not have any duplicate entries
    - Pre-handle:
        - The transaction must be signed by the account referenced by a `sender_id` for each entry in the pending airdrops list
    - Handle:
        - Confirm that for the given pending airdrops ids in the transaction there are corresponding pending transfers existing in state
        - Any additional validation depending on config or state i.e. semantics checks
        - The business logic for cancelling pending airdrops
            - Should boil down to clearing up the pending airdrops entries from the pending airdrops state
    - Fees calculation
- Update throttle definitions to include the new `TokenCancelAirdrop` transaction type
  - Throttle definitions are specified in `throttles.json` files
  - There are different configurations containing throttle definitions under `hedera-node/configuration/` for the different environments e.g. testnet, previewnet, mainnet
  - There is also a default throttle definition file in `resources/genesis/throttles.json` that is used during the genesis
  - Add the new `TokenCancelAirdrop` transaction type to the `ThroughputLimits` bucket
- Additional considerations:
    - All pending transfers sent by an account must be canceled before the account can be deleted
    - If the sender’s account expires and cannot be renewed, then all pending transfers for that sender are canceled

## Acceptance Tests

All of the expected behaviour described below should be present only if the new `TokenCancelAirdrop` feature flag is enabled.

- Given existing pending airdrop in state when valid `TokenCancelAirdrop` transaction containing entry for the same pending airdrop is performed then the `TokenCancelAirdrop` should succeed and the pending airdrop should be removed from state
- Given successful  `TokenCancelAirdrop` when `TokenClaimAirdrop` for the same airdrop is performed then the `TokenClaimAirdrop` should fail
- Given account with pending airdrops in state that cannot be deleted when successful `TokenCancelAirdrop` is performed for all of the account's pending airdrops then the account can be successfully deleted
- `TokenCancelAirdrop` transaction containing pending airdrops entries which do not exist in state should fail
- `TokenCancelAirdrop` transaction with no pending airdrops entries should fail
- `TokenCancelAirdrop` transaction with more than 10 airdrops entries should fail
- `TokenCancelAirdrop` transaction containing duplicate entries should fail
- `TokenCancelAirdrop` transaction not signed by the account referenced by a `sender_id` for each entry in the pending airdrops list should fail
- Given the feature flag for `TokenCancelAirdrop` is disabled then any `TokenCancelAirdrop` transaction should fail with `NOT_SUPPORTED`
