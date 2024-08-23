# Introduce TokenReject operation

## Purpose

As part of [HIP-904](https://hips.hedera.com/hip/hip-904), we would like to have the option of rejecting a token that has become part of an account's balance (no matter how e.g. via regular crypto transfer or airdrop).

This would mean transferring the token (the whole amount if fungible) or the NFT (concrete serial number from a unique token type) back to its treasury account, without assessing custom fees. Using token reject an account could get rid of potential “ransom” tokens which have very expensive custom fees and only pay for the cheaper token reject operation.

Token reject transaction can be performed only for tokens which are part of the persistent state of the account. No tokens from the pending state that is introduced with HIP-904 could be rejected. This remains only a right for the owner, who can cancel a pending airdrop.

## Goals

1. Define new `TokenReject` HAPI transaction
2. Implement token reject transaction handler logic

## Non-Goals

- Implement token rejection in system contract functions

## Architecture

The implementation related to the new `TokenReject` transaction will be gated behind a `tokens.reject.enabled` feature flag.

### HAPI updates

Create new transaction type as defined in the HIP:

```protobuf
/**
 * Reject undesired token(s).<br/>
 * Transfer one or more token balances held by the requesting account to the treasury for each
 * token type.<br/>
 * Each transfer SHALL be one of the following
 * - A single non-fungible/unique token.
 * - The full balance held for a fungible/common token type.
 *
 * A single tokenReject transaction SHALL support a maximum of 10 transfers.
 */
message TokenRejectTransactionBody {
    /**
     * An account holding the tokens to be rejected.
     */
    AccountID owner = 1;

    /**
     * A list of one or more token rejections.<br/>
     * On success each rejected token serial number or balance SHALL be transferred from
     * the requesting account to the treasury account for that token type.
     */
    repeated TokenReference rejections = 2;
}

/**
 * A union token identifier.<br/>
 * Identify a fungible/common token type, or a single non-fungible/unique token serial.
 */
message TokenReference {
    oneof token_identifier {
        /**
         * A fungible/common token type.
         */
        TokenID fungible_token = 1;

        /**
         * A single specific serialized non-fungible/unique token.
         */
        NftID nft = 2;
    }
}
```

Add new RPC to `TokenService` :

```protobuf
service TokenService {

//    ...

    /**
     * Reject one or more tokens.<br/>
     * This transaction SHALL transfer the full balance of one or more tokens from the requesting
     * account to the treasury for each token. This transfer SHALL NOT charge any custom fee or
     * royalty defined for the token(s) to be rejected.<br/>
     * <h3>Effects on success</h3>
     * <ul>
     *   <li>If the rejected token is fungible/common, the requesting account SHALL have a balance
     *       of 0 for the rejected token. The treasury balance SHALL increase by the amount that
     *       the requesting account decreased.</li>
     *   <li>If the rejected token is non-fungible/unique the requesting account SHALL NOT hold
     *       the specific serialized token that is rejected. The treasury account SHALL hold each
     *       specific serialized token that was rejected.</li>
     * </li>
     */
    rpc rejectToken (Transaction) returns (TransactionResponse);
}
```

### Fees

In essence token rejection is very special case of one-way token transfer and the fees paid for it should be the same as for basic crypto transfer without any custom fees.

An update into the `feeSchedule` file would be needed to specify that.

### Services updates

- Update `TokenServiceDefinition` class to include the new RPC method definition for token rejection.
- Implement new `TokenRejectHandler` class which should be invoked when the gRPC server handles `TokenReject` transactions. The class should be responsible for:
  - Pure checks: validation logic based only on the transaction body itself in order to verify if the transaction is valid one
  - Pre-handle: additional validation that will that can be done using a read-only underlying state
    - We should have a signature verification logic validating that we have the signature of the payer of the transaction
    - We should have a signature verification logic validating that we have the signature of the sender/owner of a given token
    - Does the account have a positive balance (in case of FT) of the token or is the account owner of the token (in case of NFT) they try to reject
  - Handle:
    - Any additional validation depending on config or state i.e. semantics checks
    - The business logic for token reject
      - We should not decrement `used_auto_associations` field
      - We should not dissociate the token from the account after token reject is performed
      - In case `receiver_sig_required` is set on a treasury account it should be ignored and the token reject should succeed
      - In case the token is frozen or paused the token reject should fail
    - Update state
  - Fees calculation
    - Custom fees are not applied and no tokens are deducted from the sender balance
    - Token dissociate fees are not applied

### Token allowances

An owner can provide a token allowance for non-fungible and fungible tokens. The owner is the account that owns the tokens and grants the allowance to the spender. The spender is the account that spends tokens authorized by the owner from the owners account.

If `TokenReject` is executed on the owner of a token allowance, then the allowance for that token should be canceled.

If `TokenReject` is executed on the spender of a token allowance, then the allowance should not be affected. It's the responsibility of the owner to clean up any unnecessary allowances (or continue to pay rent for them).
That would mean that after performing `TokenReject` on a spender account, then the spender can still perform `TokenAirdrop` for a given allowance even though the token was rejected.

## Acceptance Tests

All of the expected behaviour described below should be present only if the new `TokenReject` feature flag is enabled. No custom fees should be assessed and the tokens should not be dissociated from the account after token reject is performed.

- Given account with some fungible token in its balance when `TokenReject` for the same fungible token is performed then the `TokenReject` should succeed and the whole amount of the fungible token from the account should be transferred to the token treasury
- Given account with some NFT in its balance when `TokenReject` for the same NFT is performed then the `TokenReject` should succeed, the NFT should be transferred from the account to the token treasury and any other NFTs from the same collection should be left in the account
- Given account with enough fungible tokens and NFTs in its balance when `TokenReject` for up to 10 transfers of fungible or NFTs is performed then the `TokenReject` should succeed and the whole amount of the fungible tokens, the specified NFTs from the account should should be transferred to the token treasury and any other NFTs from the same collection should be left in the account
- Given account with some token in its balance and treasury account with `receiver_sig_required` enabled when `TokenReject` for the same token is performed then the `TokenReject` should succeed
- Given account with some fungible token in its balance that is frozen when `TokenReject` for the same token is performed then the `TokenReject` should fail
- Given account with some fungible token in its balance that is paused when `TokenReject` for the same token is performed then the `TokenReject` should fail
- Given account with some NFT in its balance that is frozen when `TokenReject` for the same NFT is performed then the `TokenReject` should fail
- Given account with some NFT in its balance that is paused when `TokenReject` for the same NFT is performed then the `TokenReject` should fail
- Given token allowance from owner to sender account when `TokenReject` for the same token is performed on the owner account then the token allowance should be canceled
- Given token allowance from owner to sender account when `TokenReject` for the same token is performed on the sender account then the token allowance should not be affected
- Given token allowance from owner to sender account when `TokenReject` for the same token is performed on the sender account then the sender account should be able to perform `TokenAirdrop` for the same token within the existing allowance
- Given `TokenReject` reject transaction that has payer different from the sender/owner of a given token, and the transaction has both signatures of the payer and the sender/owner account then the `TokenReject` should succeed
- Given `TokenReject` reject transaction that does not have the signature of the sender/owner of a given token then the `TokenReject` should fail
- Given account with no fungible token in its balance when `TokenReject` for the same fungible token is performed then the `TokenReject` should fail
- Given account with no NFT in its balance when `TokenReject` for the same NFT is performed then the `TokenReject` should fail
- Given account with enough fungible tokens and NFTs in its balance when `TokenReject` for more than 10 transfers of fungible or NFTs is performed then the `TokenReject` should fail
- Given treasury account with its token in balance then `TokenReject` for the same token is performed from the treasury account then the `TokenReject` should fail
