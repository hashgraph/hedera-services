# Contract Accounts Nonces Externalization

## Purpose

In order to provide more complete EVM account equivalence support and a better developer experience for [eth_getTransactionCount](https://ethereum.org/en/developers/docs/apis/json-rpc/#eth_gettransactioncount) we must externalize the contract account nonce value changes from Consenus Node to Mirror Node.

## Goals

- Fix logic for storing of contract nonces in Consensus Node state
- Externalize contract accounts nonces in transaction records

## Non Goals

- Handle nonce updates for EOAs

## Architecture

The following is a table with general use cases and behavior for Ethereum and Hedera:

| Use case                                                                                | Behavior in Ethereum                                                                                       | Behavior in Hedera (current)                                                                                                                                                     | Behavior in Hedera (desired)                                                                                                                                                     |
|-----------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EOA transaction (`EthereumTransaction`)                                                 | EOA nonce is incremented                                                                                   | EOA nonce is incremented, updates are not externalized to Mirror Node through transaction records, but can be picked up by reading the value from the `EthereumTransaction` body | EOA nonce is incremented, updates are not externalized to Mirror Node through transaction records, but can be picked up by reading the value from the `EthereumTransaction` body |
| EOA transaction (`ContractCall` or `ContractCreate`)                                    | -                                                                                                          | -                                                                                                                                                                                | -                                                                                                                                                                                |
| Contract transaction resulting in `CREATE/CREATE2` (`EthereumTransaction`)              | initial contract nonce value is 1; nonce is incremented for each contract creation initiated by an account | initial contract nonce value is 1; nonce is incremented for each contract creation initiated by an account, updates are not externalized to Mirror Node                          | initial contract nonce value is 1; nonce is incremented for each contract creation initiated by an account, updates are externalized to Mirror Node                              |
| Contract transaction resulting in `CREATE/CREATE2` (`ContractCall` or `ContractCreate`) | -                                                                                                          | initial contract nonce value is 1; nonce is incremented for each contract creation initiated by an account, updates are not externalized to Mirror Node                          | initial contract nonce value is 1; nonce is incremented for each contract creation initiated by an account, updates are externalized to Mirror Node                              |

### Contract Nonce Externalization
- Contract account nonces are updated in Consensus Node state using in the following places:
  - [HederaWorldState](https://github.com/hashgraph/hedera-services/blob/96a85f0e08f82582bbf25328d14ca90fc630c5ef/hedera-node/hedera-mono-service/src/main/java/com/hedera/node/app/service/mono/store/contracts/HederaWorldState.java)
  - [ContractCreateTransitionLogic](https://github.com/hashgraph/hedera-services/blob/develop/hedera-node/hedera-mono-service/src/main/java/com/hedera/node/app/service/mono/txns/contract/ContractCreateTransitionLogic.java#L209)
  - [ContractCallTransitionLogic](https://github.com/hashgraph/hedera-services/blob/develop/hedera-node/hedera-mono-service/src/main/java/com/hedera/node/app/service/mono/txns/contract/ContractCallTransitionLogic.java#L148)
  - [TransactionProcessingResult](https://github.com/hashgraph/hedera-services/blob/96a85f0e08f82582bbf25328d14ca90fc630c5ef/hedera-node/hedera-mono-service/src/main/java/com/hedera/node/app/service/mono/contracts/execution/TransactionProcessingResult.java)
  - [EvmFnResult](https://github.com/hashgraph/hedera-services/blob/96a85f0e08f82582bbf25328d14ca90fc630c5ef/hedera-node/hedera-mono-service/src/main/java/com/hedera/node/app/service/mono/state/submerkle/EvmFnResult.java)


- We keep a `ContractId -> nonce` tree map inside `HederaWorldState`, it is updated on each call of `commit()` (using newly added method `trackContractNonces()`).
- Method `trackContractNonces` in `HederaWorldState` follows the pattern of `trackNewlyCreatedAccounts`.
    - Checks if an account is a new smart contract and externalizes its nonce.
    - Checks if an existing smart contract's nonce is updated and externalizes it.
- Added a `ContractId -> nonce` tree map inside `TransactionProcessingResult`.
- Persists account contract nonces into state in `ContractCreateTransitionLogic` and `ContractCallTransitionLogic` using `setContractNonces` from `TransactionProcessingResult`.
- Created new `ContractNonceInfo` submerkle class with two main entities - `contractId` and `nonce`
- Added new method `serializableContractNoncesFrom` in `EvmFnResult` that builds `List<ContractNonceInfo>` (submerkle) from `Map<ContractID, Long>`
- Added new verison `7` (`RELEASE_0400_VERSION`) and externalized logic for `serialize` and `deserialize` of `contractNonces` in `EvmFnResult`




### Fix Storing Of Nonces Into State

- Fix storing of nonces is done in the following places:
  - [UpdateAccountTracker](https://github.com/hashgraph/hedera-services/blob/96a85f0e08f82582bbf25328d14ca90fc630c5ef/hedera-node/hedera-evm/src/main/java/com/hedera/node/app/service/evm/store/UpdateAccountTracker.java)
  - [UpdateAccountTrackerImpl](https://github.com/hashgraph/hedera-services/blob/96a85f0e08f82582bbf25328d14ca90fc630c5ef/hedera-node/hedera-mono-service/src/main/java/com/hedera/node/app/service/mono/store/UpdateAccountTrackerImpl.java)
  - [UpdateTrackingAccount](https://github.com/hashgraph/hedera-services/blob/96a85f0e08f82582bbf25328d14ca90fc630c5ef/hedera-node/hedera-evm/src/main/java/com/hedera/node/app/service/evm/store/models/UpdateTrackingAccount.java)
  - [HederaEvmEntityAccess](https://github.com/hashgraph/hedera-services/blob/96a85f0e08f82582bbf25328d14ca90fc630c5ef/hedera-node/hedera-evm/src/main/java/com/hedera/node/app/service/evm/store/contracts/HederaEvmEntityAccess.java)
  - [MutableEntityAccess](https://github.com/hashgraph/hedera-services/blob/96a85f0e08f82582bbf25328d14ca90fc630c5ef/hedera-node/hedera-mono-service/src/main/java/com/hedera/node/app/service/mono/store/contracts/MutableEntityAccess.java)
  - [WorldStateAccount](https://github.com/hashgraph/hedera-services/blob/96a85f0e08f82582bbf25328d14ca90fc630c5ef/hedera-node/hedera-evm/src/main/java/com/hedera/node/app/service/evm/store/contracts/WorldStateAccount.java)
  - [HederaLedger](https://github.com/hashgraph/hedera-services/blob/96a85f0e08f82582bbf25328d14ca90fc630c5ef/hedera-node/hedera-mono-service/src/main/java/com/hedera/node/app/service/mono/ledger/HederaLedger.java)
  

- Currently we are not storing contract account nonces into state and this needs a fix similar to `setBalance` in `UpdateTrackingAccount`.
- For all created and updated contracts we should store their nonces in state.
  - We need a way to track a contract account's `nonce` by its `address`.
  - - Added new method `setNonce` in `UpdateAccountTrackerImpl` 
  - - It sets property `ETHEREUM_NONCE` for `address` into `trackingAccounts`
  - Updated `setNonce` in `UpdateTrackingAccount` to use `UpdateAccountTrackerImpl`s implementation
  - `AbstractLedgerWorldUpdater` -> `createAccount` -> `newMutable.setNonce(nonce)`
  - `AbstractStackedLedgerUpdater` -> `commit` -> `mutable.setNonce(updatedAccount.getNonce())`
- We also need a way to read a contract account's nonces from state.
  - Added `getNonce` method in `HederaLedger` that retrieves `nonce` by `AccountID`
  - Added `getNonce` method in `MutableEntityAccess` that uses `HederaLedger`'s `getNonce`
  - Updated `getNonce` in `WorldStateAccount` to return value from state using `entityAccess` instead of `0`

### Feature Flags

We will need to add two feature flags:
- one for toggling the contract nonce externalization in transaction handling e.g. `contracts.nonces.externalization.enabled`

### Merging of contract into hollow account

When contract is merged into an existing hollow account we are correctly setting its nonce value to 1 in `HederaCreateOperationExternalizer.finalizeHollowAccountIntoContract` as specified in [EIP-161](https://github.com/ethereum/EIPs/blob/master/EIPS/eip-161.md).

### New Classes

- `ContractNonceInfo` in submerkle

## Acceptance Tests

### Positive Tests

* Verify that when contract A successfully deploys contract B, the nonce of contract A is incremented by 1 and the nonce of contract B is set to 1.
* Verify that when contact A with nonce 2 calls contract B with nonce 1 that creates contract C and contract D, the nonce of contract A is still 2 and the resulting `ContractFunctionResult` has the following nonce values:
  * B -> 3
  * C -> 1
  * D -> 1
* Verify that when contract A is merged into hollow account H, the nonce of the resulting account is set to 1.
* Verify that when contract A is merged into hollow account H and the init code of A also deploys contract B, the nonce of the resulting account is 2 and the nonce of contract B is set to 1.
* Verify that when feature flag is disabled contract nonces won't be externalized.
### Negative Tests

* Verify that when contract A fails to deploy contract B, the nonce of contract A is still incremented by 1.
