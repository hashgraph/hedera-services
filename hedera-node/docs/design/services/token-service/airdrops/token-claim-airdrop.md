# Introduce TokenClaimAirdrop transaction

## Purpose

We need to add a new functionality that would make it possible for an airdrop receiver to accept a pending airdrop transfer. This would be the only way for a receiver, which hasn't been associated to a given token to accept an airdropped token that has been in pending airdrops state.

## Goals

1. Define new `TokenClaimAirdrop` HAPI transaction
2. Implement token claim airdrop transaction handler logic

## Non-Goals

- Implement token claim airdrop in system contract functions

## Architecture

The implementation related to the new `TokenClaimAirdrop` transaction will be gated behind a `tokens.airdrops.claim.enabled` feature flag.

### HAPI updates

Create new transaction type as defined in the HIP:

```protobuf
/**
 * Token claim airdrop<br/>
 * Complete one or more pending transfers on behalf of the recipient(s) for each airdrop.<br/>
 * The sender MUST have sufficient balance to fulfill the airdrop at the time of claim. If the
 * sender does not have sufficient balance, the claim SHALL fail.
 *
 * Each pending airdrop successfully claimed SHALL be removed from state and SHALL NOT be available
 * to claim again.
 *
 * Each claim SHALL be represented in the transaction body and SHALL NOT be restated
 * in the record file.<br/>
 * All claims MUST succeed for this transaction to succeed.
 */
message TokenClaimAirdropTransactionBody {
    /**
     * A list of one or more pending airdrop identifiers.<br/>
     * This transaction MUST be signed by the account referenced in the `receiver_id` for
     * each entry in this list.
     * <p>
     * This list MUST contain between 1 and 10 entries, inclusive.<br/>
     * This list MUST NOT have any duplicate entries.
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
     * Claim one or more pending airdrops.<br/>
     * This transaction MUST be signed by _each_ account *receiving* an airdrop to be claimed.<br>
     * If a Sender lacks sufficient balance to fulfill the airdrop at the time the claim is made,
     * that claim SHALL fail.
     */
    rpc claimAirdrop (Transaction) returns (TransactionResponse);
}
```

### Fees

The basic `TokenClaimAirdrop` fee should be proportional to the number of airdrops being claimed in the transaction.

An update into the `feeSchedule` file would be needed to specify that.

### Services updates

- Update `ApiPermissionConfig` class to include a `0-* PermissionedAccountsRange` for the new `TokenClaimAirdrop` transaction type
- Update `TokenServiceDefinition` class to include the new RPC method definition for claiming airdrops
- Implement new `TokenClaimAirdropHandler` class which should be invoked when the gRPC server handles `TokenClaimAirdrop` transactions. The class should be responsible for:
  - Pure checks: validation logic based only on the transaction body itself in order to verify if the transaction is valid one
    - Verify that the pending airdrops list contains between 1 and 10 entries, inclusive
    - Verify that the pending airdrops list does not have any duplicate entries
  - Pre-handle:
    - The transaction must be signed by the account referenced by a `receiver_id` for each entry in the pending airdrops list
  - Handle:
    - Confirm that for the given pending airdrops ids in the transaction there are corresponding pending transfers existing in state
    - Check if the sender has sufficient amount or has enough approved allowance of the tokens being claimed to fulfill the airdrop
    - Check if the token is not frozen, paused, or deleted
      - If the token is frozen or paused the claim transaction should fail
      - For deleted tokens, the claim transaction should fail, but also we should remove the pending airdrop from state
    - Any additional validation depending on config or state i.e. semantics checks
    - The business logic for claiming pending airdrops
      - If token association between each `receiver_id` and `token_reference` does not exist, we need to create it; future rents for token association slot should be paid by `receiver_id`
        - Since we would have the signature of the receiver, even if it's an account with `receiver_sig_required=true`, the claim would implicitly work properly
        - The token association is free at this point because the sender already paid for it when submitting the `TokenAirdrop` transaction
      - Then we should transfer the claimed tokens to each `receiver_id`
        - Reuse any existing logic from `CryptoTransferHandler`, extracting common code into a separate class
        - We must skip the assessment of custom fees
    - Token transfers should be externalized using the `tokenTransferLists` field in the transaction record
  - Fees calculation
- Update throttle definitions to include the new `TokenClaimAirdrop` transaction type
  - Throttle definitions are specified in `throttles.json` files
  - There are different configurations containing throttle definitions under `hedera-node/configuration/` for the different environments e.g. testnet, previewnet, mainnet
  - There is also a default throttle definition file in `resources/genesis/throttles.json` that is used during the genesis
  - Add the new `TokenClaimAirdrop` transaction type to the `ThroughputLimits` bucket

### Zero-Balance accounts

An account with no open auto-association slots can receive airdrops but must send a `TokenClaimAirdrop` transaction, which requires a payer. If the account has zero hbars, then it can still claim the transfer if someone else is willing to pay for that transaction. For example, a Dapp could be the payer on the transaction. Both the Dapp and the account must sign the transaction.

### Hollow accounts

Any existing hollow accounts that were created before [HIP-904](https://hips.hedera.com/hip/hip-904) will have no or limited number of `maxAutoAssociations` depending on if they were created with HBAR or token transfer respectively. That means an airdrop of unassociated tokens to such accounts will result in a pending transfer.
Performing `TokenClaimAirdrop` for such hollow account will also complete the account by setting its key which will obtained from the required transaction signature. Completing the hollow account should not modify the `maxAutoAssociations` on the account. That should always be an explicit step by a user.

## Acceptance Tests

All of the expected behaviour described below should be present only if the new `TokenClaimAirdrop` feature flag is enabled.

- Given existing pending airdrop in state when valid `TokenClaimAirdrop` transaction containing entry for the same pending airdrop is performed then the `TokenClaimAirdrop` should succeed resulting in:
  - the tokens being claimed should be automatically associated with the `receiver_id` account
  - the tokens being claimed should be transferred to the `receiver_id` account
  - the pending airdrop should be removed from state
  - the transaction record should contain the transferred tokens in `tokenTransferLists` field
- Given existing pending airdrop in state when valid `TokenClaimAirdrop` transaction containing entry for the same pending airdrop is performed and the token in the pending airdrop is frozen or paused then the `TokenClaimAirdrop` should fail without modifying the pending airdrop state
- Given existing pending airdrop in state when valid `TokenClaimAirdrop` transaction containing entry for the same pending airdrop is performed and the token in the pending airdrop is deleted then the `TokenClaimAirdrop` should fail and the pending airdrop should be removed from state
- Given a successful `TokenClaimAirdrop` transaction having a hollow account as `receiver_id` should also complete the account without modifying its `maxAutoAssociations` value
- Given successful `TokenClaimAirdrop` when another `TokenClaimAirdrop` for the same airdrop is performed then the second `TokenClaimAirdrop` should fail
- `TokenClaimAirdrop` transaction with no pending airdrops entries should fail
- `TokenClaimAirdrop` transaction with more than 10 pending airdrops entries should fail
- `TokenClaimAirdrop` transaction containing duplicate entries should fail
- `TokenClaimAirdrop` transaction containing pending airdrops entries which do not exist in state should fail
- `TokenClaimAirdrop` transaction not signed by the account referenced by a `receiver_id` for each entry in the pending airdrops list should fail
- `TokenClaimAirdrop` transaction with a `sender_id` account that does not have sufficient balance or not enough allowance of the claimed token should fail
- Given the feature flag for `TokenClaimAirdrop` is disabled then any `TokenClaimAirdrop` transaction should fail with `NOT_SUPPORTED`
