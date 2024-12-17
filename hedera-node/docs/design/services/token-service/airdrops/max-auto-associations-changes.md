# Adapt existing logic with the new maxAutoAssociations behaviour

## Purpose

One of the main points of friction in Hedera regarding token airdrops is the need for a receiver to associate with a token before the sender of the token attempts the transfer. Currently a way to reduce this friction is to use the ability of accounts to automatically associate with tokens. This is controlled by their `maxAutoAssociations` property which is the number of automatic token associations the account can support (i.e. slots). When an account is created or the property value is updated the account prepays for all of the `maxAutoAssociations`.

We can reduce this friction by further expanding on the functionality around auto associations by having the ability to have unlimited number of auto association slots and removing the requirement for an account to prepay for unused token association slots. Instead we can have the sender pay for the initial number of auto association slots needed for a transfer.

Additionally, in order to support behaviour similar to other EVM-compatible chains, we will want to initialize auto- and lazy-created accounts to have unlimited number of auto association slots.

In order to implement [HIP-904](https://hips.hedera.com/hip/hip-904) we need to adapt part of the existing Services logic, so that we are aligned with the new approach.

## Goals

1. Implement the ability for accounts to have unlimited number of auto associations
2. Accounts shouldn’t prepay for unused token auto associations slots. Accounts should pay for slots only when they are used
3. Senders should cover the initial fees related to auto association
4. Initialize auto- and lazy-created accounts to have unlimited number of auto associations

## Non-Goals

- Updating existing accounts’ `maxAutoAssociations` values

## Architecture

The implementation related to the new `maxAutoAssociations` behaviour will be gated behind a `tokens.airdrops.unlimitedMaxAutoAssociations.enabled` feature flag.

### CryptoCreate

For regular accounts we should have the `maxAutoAssociations` value default to 0. Additionally users should be able to create accounts with unlimited number of auto association slots by setting the `maxAutoAssociations` value to -1.

The charged fees for `CryptoCreate` transaction should not depend on the value of `maxAutoAssociations` since we don’t want users to prepay for unused auto association slots.

We should update any validation logic in the transaction handlers for `CryptoCreate` so that the value of -1 is permitted for `maxAutoAssociations` and also update the fee calculation logic so that it does not depend on the value of `maxAutoAssociations`.

**Classes to be updated:**
- `CryptoCreateHandler`

### Auto- & Lazy-creation

When we create an account with `auto-create`/ `lazy-create` logic, the default `maxAutoAssociations` value for the newly created account should be `-1`.

Those types of account creation are achieved through transfers resulting in synthetic `CryptoCreate` transactions, so the fees should not depend on the value of the `maxAutoAssociations` of the resulting account.

In the case for which the auto-/ lazy-create is from a transfer of fungible token or an NFT, the fees should include the auto association fee and the first auto-renewal period rent for the token association, and should be paid by the sender.

**Classes to be updated:**
- `AutoAccountCreator`

### Contract creation

**ContractCreate**

For those contract accounts we should have the `maxAutoAssociations` value default to 0. Additionally users should be able to create accounts with unlimited number of auto association slots by setting the `maxAutoAssociations` value to -1.

The charged fees for `ContractCreate` transaction should not depend on the value of `maxAutoAssociations` since we don’t want users to prepay for unused auto association slots.

We should update any validation logic in the transaction handlers for `ContractCreate` so that the value of -1 is permitted for `maxAutoAssociations` and also update the fee calculation logic so that it does not depend on the value of `maxAutoAssociations`.

**EthereumTransaction**

Contract accounts created by a contract create `EthereumTransaction` should have their `maxAutoAssociations` value default to 0.

**EVM creations (`CREATE` & `CREATE2` opcodes)**

Contract accounts by default will be spam-free in nature i.e. with `maxAutoAssociations` value default to 0.

**`CREATE2` merge into existing hollow account**

The current Services behaviour for this case is that `maxAutoAssociations` will be limited to the number of already associated tokens in balance if the hollow account was created with token transfer or no `maxAutoAssociations` (0) if the hollow account was created with HBAR transfer.

For the purposes of [HIP-904](https://hips.hedera.com/hip/hip-904) those contract accounts should have limited number of `maxAutoAssociations` that is equal to the number of associations of the hollow account so that any associated tokens with the hollow account could be still transferred to the same address but no new tokens can be airdropped.

That way if the hollow account was created with token transfer then the resulting contract will still be associated with only those tokens and can participate in further transfers with them. If the hollow account was created with HBAR transfer then the resulting contract should have no `maxAutoAssociations` (0) and will be spam-free.

**Classes to be updated:**
- `HevmTransactionFactory`
- `HandleHederaOperations`
- `SynthTxnUtils`

### CryptoUpdate & ContractUpdate

`CryptoUpdate` & `ContractUpdate` should be able to set `maxAutoAssociations` on a specific account including to the value of -1.

We should update any validation logic in the transaction handlers for `CryptoUpdate` & `ContractUpdate` so that the value of -1 is permitted for `maxAutoAssociations` and also update the fee calculation logic so that it does not depend on the value of `maxAutoAssociations`.

**Classes to be updated:**
- `CryptoUpdateHandler`
- `ContractUpdateHandler`

### CryptoTransfer

`CryptoTransfer` fee logic should be changed so that:

- in case token auto association is triggered the sender should cover the fee for it
- in case token auto association is triggered the sender should cover the first auto-renewal period’s rent for that token association

**Classes to be updated:**
- `CryptoTransferHandler`

### **Grandfathering existing accounts**

Currently if an account has `maxAutomaticAssociations` set to max integer value, then it will behave the same as an unlimited-auto-associations one unless the account needs to use values that are bigger than the max integer value.

We will not perform state migration for now but at some later stage we can do it just to have the unlimited-auto-associations accounts in state consistent.

## Acceptance Tests

All of the expected behaviour described below should be present only if the new `maxAutoAssociations` feature flag is enabled.

- `CryptoCreate`
    - A successful `CryptoCreate` transaction that does not specify value for `maxAutoAssociations` should result in an account with `maxAutoAssociations = 0`
    - `CryptoCreate` transaction with `maxAutoAssociations = -1` should successfully create an account
    - `CryptoCreate` transaction with `maxAutoAssociations = -2` or any other negative value should fail
    - `CryptoCreate` transaction fees should not depend on the value of `maxAutoAssociations`
- `ContractCreate`
    - A successful `ContractCreate` transaction that does not specify value for `maxAutoAssociations` should result in a contract account with `maxAutoAssociations` equal to 0
    - `ContractCreate` transaction with `maxAutoAssociations = -1` should successfully create a contract account
    - `ContractCreate` transaction with `maxAutoAssociations = -2` or any other negative value should fail
    - `ContractCreate` transaction fees should not depend on the value of `maxAutoAssociations`
- EVM contract creations (`CREATE` & `CREATE2`)
    - Contract accounts by default will be spam-free in nature i.e. with `maxAutoAssociations` value default to 0.
    - Contract accounts resulting from `CREATE2` merge into existing hollow account should have `maxAutoAssociations` value equal to the number of token associations of the hollow account
- `ContractCall`
    - An `ContractCall` that calls method of `Contract A` which creates `Contract B` should result in `Contract B` having `maxAutoAssociations` equal to 0
- `EthereumTransaction`
    - An `EthereumTransaction` that explicitly creates a contract should result in a contract account with `maxAutoAssociations` equal to 0
    - An `EthereumTransaction` that calls method of `Contract A` which creates `Contract B` should result in `Contract B` having `maxAutoAssociations` equal to 0
- `CryptoUpdate`
    - An account should be able to update its `maxAutoAssociations` value to -1, 0 or any positive integer
    - `CryptoUpdate` transaction with `maxAutoAssociations = -2` or any other negative value should fail
    - `CryptoUpdate` transaction fees should not depend on the value of `maxAutoAssociations`
- `ContractUpdate`
    - A contract account should be able to update its `maxAutoAssociations` value to -1, 0 or any positive integer
    - `ContractUpdate` transaction with `maxAutoAssociations = -2` or any other negative value should fail
    - `ContractUpdate` transaction fees should not depend on the value of `maxAutoAssociations`
- `CryptoTransfer`
    - `CryptoTransfer` transaction resulting in automatic token association should charge the fee for the auto association and the first auto-renewal period’s rent from the sender of the token; no fees should be charged from the receiver of the transfer
- Auto- / Lazy-creation
    - Any transaction which results in auto-creation should have its resulting account with `maxAutoAssociations = -1`
    - Any transaction which results in lazy-creation should have its resulting hollow account with `maxAutoAssociations = -1`
