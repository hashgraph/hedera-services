# Airdrop Transaction

## Purpose

The essence of HIP-904 is introducing a missing functionality in the Hedera ecosystem that enables airdrop transactions. 

Airdrops are frequently used operation in the web3 ecosystem and a lot of blockchains,
dapps, or DAO developers incentivize users to explore and use their products with
this process. Early adopters are often rewarded with so-called airdrops which are
tokens for the system and a dedicated amount of them are distributed in such airdrops.
Hedera will benefit from introducing this functionality and this is something long
requested by the community.

The main operation, which would allow airdrops to be sent is the `TokenAirdropTransaction`.
Using it, an airdrop sender would be able to send airdrop tokens to a specific receiver, no matter if the receiver is associated with the token or not.

## Prerequisite reading
* [HIP-904](https://hips.hedera.com/hip/hip-904)

## Goals

1. Define new `TokenAirdropTransaction` transaction type
2. Implement token airdrop transaction handler logic

## Architecture

The implementation related to the new `TokenAirdropTransaction` transaction will be gated behind a feature flag.

### HAPI updates

Create new transaction type as defined in the HIP:

```protobuf
/**
 * Airdrops one or more tokens to one or more accounts.
 *
 * ### Effects
 * This distributes tokens from the balance of one or more sending account(s) to the balance
 * of one or more recipient accounts. Accounts MAY receive the tokens in one of four ways.
 *
 *  - An account already associated to the token to be distributed SHALL receive the
 *    airdropped tokens immediately to the recipient account balance.<br/>
 *    The fee for this transfer SHALL include the transfer, the airdrop fee, and any custom fees.
 *  - An account with available automatic association slots SHALL be automatically
 *    associated to the token, and SHALL immediately receive the airdropped tokens to the
 *    recipient account balance.<br/>
 *    The fee for this transfer SHALL include the transfer, the association, the cost to renew
 *    that association once, the airdrop fee, and any custom fees.
 *  - An account with "receiver signature required" set SHALL have a "Pending Airdrop" created
 *    and must claim that airdrop with a `claimAirdrop` transaction.<br/>
 *    The fee for this transfer SHALL include the transfer, the association, the cost to renew
 *    that association once, the airdrop fee, and any custom fees. If the pending airdrop is not
 *    claimed immediately, the `sender` SHALL pay the cost to renew the token association, and
 *    the cost to maintain the pending airdrop, until the pending airdrop is claimed or cancelled.
 *  - An account with no available automatic association slots SHALL have a "Pending Airdrop"
 *    created and must claim that airdrop with a `claimAirdrop` transaction.<br/>
 *    The fee for this transfer SHALL include the transfer, the association, the cost to renew
 *    that association once, the airdrop fee, and any custom fees. If the pending airdrop is not
 *    claimed immediately, the `sender` SHALL pay the cost to renew the token association, and
 *    the cost to maintain the pending airdrop, until the pending airdrop is claimed or cancelled.
 *
 * If an airdrop would create a pending airdrop for a fungible/common token, and a pending airdrop
 * for the same sender, receiver, and token already exists, the existing pending airdrop
 * SHALL be updated to add the new amount to the existing airdrop, rather than creating a new
 * pending airdrop.
 *
 * Any airdrop that completes immediately SHALL be irreversible. Any airdrop that results in a
 * "Pending Airdrop" MAY be canceled via a `cancelAirdrop` transaction.
 *
 * All transfer fees (including custom fees and royalties), as well as the rent cost for the
 * first auto-renewal period for any automatic-association slot occupied by the airdropped
 * tokens, SHALL be charged to the account paying for this transaction.
 *
 * ### Record Stream Effects
 * - Each successful transfer SHALL populate that transfer in `token_transfer_list` for the record.
 * - Each successful transfer that consumes an automatic association slot SHALL populate the
 *   `automatic_association` field for the record.
 * - Each pending transfer _created_ SHALL be added to the `pending_airdrops` field for the record.
 * - Each pending transfer _updated_ SHALL be added to the `pending_airdrops` field for the record.
 */
 message TokenAirdropTransactionBody {
/**
     * A list of token transfers representing one or more airdrops.<br/>
     * The sender for each transfer MUST have sufficient balance to complete the transfers.
     * <p>
     * All token transfers MUST successfully transfer tokens or create a pending airdrop
     * for this transaction to succeed.<br/>
     * This list MUST contain between 1 and 10 transfers, inclusive.
     * <p>
     * <blockquote>Note that each transfer of fungible/common tokens requires both a debit and
     * a credit, so each _fungible_ token transfer MUST have _balanced_ entries in the
     * TokenTransferList for that transfer.</blockquote>
     */
     repeated TokenTransferList token_transfers= 1;
}
```

Add new RPC to `TokenService` :

```protobuf
   /**
     * Airdrop one or more tokens to one or more accounts.<br/>
     * This distributes tokens from the balance of one or more sending account(s) to the balance
     * of one or more recipient accounts. Accounts will receive the tokens in one of four ways.
     * <ul>
     *   <li>An account already associated to the token to be distributed SHALL receive the
     *       airdropped tokens immediately to the recipient account balance.</li>
     *   <li>An account with available automatic association slots SHALL be automatically
     *       associated to the token, and SHALL immediately receive the airdropped tokens to the
     *       recipient account balance.</li>
     *   <li>An account with "receiver signature required" set SHALL have a "Pending Airdrop"
     *       created and MUST claim that airdrop with a `claimAirdrop` transaction.</li>
     *   <li>An account with no available automatic association slots SHALL have a
     *       "Pending Airdrop" created and MUST claim that airdrop with a `claimAirdrop`
     *       transaction. </li>
     * </ul>
     * Any airdrop that completes immediately SHALL be irreversible. Any airdrop that results in a
     * "Pending Airdrop" MAY be canceled via a `cancelAirdrop` transaction.<br/>
     * All transfer fees (including custom fees and royalties), as well as the rent cost for the
     * first auto-renewal period for any automatic-association slot occupied by the airdropped
     * tokens, SHALL be charged to the account submitting this transaction.
     */
    rpc airdropTokens (Transaction) returns (TransactionResponse);
```

### Fee types

The airdrop transaction will include several types of fees, a different set of them will be applicable, based on the characteristics of the airdrop. The list of fees is:

- Airdrop fee covering the operation itself
- Association fee covering a pre-paid fee for associating the token being airdropped to the receiver, in case there is a missing relationship between the two
- Custom fees covering any token custom fees, which are required to be paid by the sender of the potential transfer in case of claim
- Transfer fee covering a pre-paid fee for transferring the token being airdropped to the receiver, so that when they potentially claim the token to be free of charge
- Auto-renewal fees covering a pre-paid period for a full auto-renewal cycle
- Fee covering auto-creation/lazy-creation of accounts, if we airdrop to an alias address of non-existing account

Note that for all these fees, a given sender, which is the actual token owner, could be covered by a different payer, which is willing to pay. However, in case the receiver has not claimed an airdrop for a more than a full auto-renew cycle, the sender (token owner) would be required to continue paying future rents.

An update into the `feeSchedule` file would be needed to specify the addition of the new fee values for the airdrop transaction type itself.

### Keys

Since a given sender/token owner could be the payer of the airdrop transaction or they can rely on a separate payer account we have a couple of use cases related to the key verification logic.

If the sender of the airdrop is also the payer of this transaction, we would need only their key to be present in the transaction context.

If the airdrop transaction fees from the list above are covered by a separate payer, then the signatures of both the airdrop sender and the payer must be present in the context. This is so, because the sender is required to authorize sending the airdrop tokens from their balance.

### Service updates

- Update `TokenServiceDefinition` class to include the new RPC method definition for sending airdrops
- Implement new `TokenAirdropHandler` class which should be invoked when the gRPC server handles `TokenAirdropTransaction` transactions. The class should be responsible for:
    - Verify that the pending airdrops list contains between 1 and 10 entries, inclusive
    - Pre-handle:
        - The transaction must be signed by the sender for each entry in the `token_transfers` list
        - In the case where the airdrop fees are covered by a separate payer, the transaction must include also the payer’s signature
    - Handle:
        - Any additional validation depending on config or state i.e. semantics checks
        - Check that the airdrop sender account is a valid account. That is an existing account, which is not deleted or expired.
        - Check that the token being airdropped, does not contain a custom fee, which needs to be covered by the recipient. This includes `fractionalFees` with `net_of_transfers=false` and `royaltyFees`, including `fallbackRoyaltyFees`.
        - Such transactions should be reverted and rejected.
        - The business logic for sending pending airdrops
            - Check the association status and the existence of the recipient:
              - It is an existing one and has `max_auto_associations` set to -1 and is not associated to the token →<br/>
                Тhe token is auto associated and is directly transferred to the recipient and a `TokenTransferList` is added to the `TransactionRecord`, as well as a new `TokenAssociation` entry in the `automatic_token_associations`
              - It is an existing one and has `max_auto_associations`, which is a positive number and there are free auto association slots and the recipient is not associated to the token →<br/>
                Тhe token is auto associated and is directly transferred to the recipient and a `TokenTransferList` is added to the `TransactionRecord`, as well as a new `TokenAssociation` entry in the `automatic_token_associations`
              - It is an existing one and has `max_auto_associations`, which is a positive number but there are no free auto association slots and the recipient is not associated to the token →<br/>
                Тhe token transfer is interpreted as a pending and a `TokenPendingAirdrop` entry is added to the `TransactionRecord`. In addition, the airdrop `PendingAirdropId`/`PendingAirdropValue` entry is added to the `PendingAirdrop` VirtualMap in state
              - It is an existing one and has `max_auto_associations` set to 0 and the recipient is not associated to the token →<br/>
                Тhe token transfer is interpreted as a pending and a `TokenPendingAirdrop` entry is added to the `TransactionRecord`. In addition, the airdrop `PendingAirdropId`/`PendingAirdropValue` entry is added to the `PendingAirdrop` VirtualMap in state
              - It is an existing one and is associated to the token →<br/>
                Тhe airdrop is handled as a regular crypto transfer and a `TokenTransferList` is added to the `TransactionRecord`
              - It’s not existing and the AccountID is a public `ECDSA` key →<br/>
                Тhe airdrop would first auto-create a new account with `maxAutoAssociations` field of -1 and directly transfer the token being airdropped to it. This would include a `TokenTransferList` and the alias of the newly created account in the `TransactionRecord`
              - It’s not existing and the AccountID is a public `ED25519` key →<br/>
                Тhe airdrop would first auto-create a new account with `maxAutoAssociations` field of -1 and directly transfer the token being airdropped to it. This would include a `TokenTransferList` and the alias of the newly created account in the `TransactionRecord`
              - It’s not existing and the AccountID is an `evm_address` →<br/>
                Тhe airdrop would first lazily-create a new account with `maxAutoAssociations` field of -1 and directly transfer the token being airdropped to it. This would include a `TokenTransferList` and the alias of the newly created account in the `TransactionRecord`
    - Fee calculation logic
        - assess all required fees for the airdrop from the payer of the transaction. This includes:
            - airdrop fee (assessed always)
            - first auto-renew fee for the token (assessed always)
            - custom token fees covered by the sender (assessed always if present)
            - crypto transfer fee (pre-paid always)
            - token association fee (pre-paid if account is not associated to the token)
            - auto account creation (assessed if the recipient does not exist and they are referred by their public `ECDSA` key or `evm_address`)
- Interact with the pending airdrop state - if the recipient is not associated to the token, then the `TokenAirdropTransaction` responsibilities in term of interacting with the pending state would be to just add the airdrop information in the state
- Add this new operation type to the `ThroughputLimits` throttle bucket group, so that it's included in the throttling mechanism

## Acceptance Tests

* Verify that an airdrop with a token and existing recipient with `maxAutoAssociations=-1` works correctly and the recipient receives the token directly
* Verify that an airdrop with a token and existing recipient with `maxAutoAssociations` equal to a positive number and there are free `autoAssociations` slots works correctly and the recipient receives the token directly
* Verify that an airdrop with a token and existing recipient with `maxAutoAssociations=0` and missing token association, works correctly and the airdrop lends in the pending state
* Verify that an airdrop with a token and existing recipient with `maxAutoAssociations` equal to a positive number and there are no free `autoAssociations` slots works correctly and airdrop lends in the pending state
* Verify that an airdrop with a token and a missing recipient pointed by its public `ECDSA key alias`, works correctly by auto-creating the recipient with `maxAutoAssociations=-1` and the token is directly transferred to it
* Verify that an airdrop with a token and a missing recipient pointed by its `evm_address` alias, works correctly by lazy-creating the recipient with `maxAutoAssociations=-1` and the token is directly transferred to it
* Verify that an airdrop with a missing TokenID fails
* Verify that an airdrop with an NFT and non-existing serial number fails
* Verify that an airdrop with wrong input data (e.g. negative amount, negative serial number or missing mandatory field) fails
* Verify that an airdrop with a missing signature of the sender fails
* Verify that an airdrop, which fee expenses are covered by a separate payer account but has missing payer signature - fails
* Verify that an airdrop with more than 10 entries in the `token_transfers` list - fails
* Verify that an airdrop with a duplicate entry in the `token_transfers` list - fails