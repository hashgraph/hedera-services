# Virtual Addresses

## Purpose
Introduce the concept of virtual addresses in order to bridge the conceptual gap between Hedera's and Ethereum's view of accounts.
Virtual addresses will resolve the issue of account EVM compatibility and identification and will be responsible for EVM address related logic.

## Goals
- Add list of evmAddress values known as “Virtual Address” to Hedera accounts which govern the address the EVM observes for a given account transaction
- Hedera Accounts can add, disable and remove virtual address entries as desired
- The Hedera network will validate ownership by extracting the public key from the signature and comparing the calculated public address to the evmAddress passed in on addition of the virtual address and will maintain an `evmAddress -> accountId` map thereafter
- Contract accounts may utilize the `evmAddress` to store their address in accordance with `CREATE` and `CREATE2` EVM operations
- Restoring HIP 32’s consistency so that account aliases will have only key based values

## Non Goals
- Support transferring of virtual addresses between accounts

## Architecture

### Account state updates
- Update `HederaAccount` interface and its implementations (`MerkleAccount` & `OnDiskAccount`) to have the ability to read/write virtual addresses and default virtual address
- Update `MerkleAccountState` with matching changes as in `HederaAccount`
- Nonce management
  - Update account state definition to track nonce value for each virtual address
  - Nonce updates will be externalized by adding a map `ContractId -> nonce` in message `ContractFunctionResult` that will be populated in the top-level transaction records.

#### Separate EVM address lookup from aliases
- Introduce a new class for managing `evmAddress -> accountId` in-memory map
- The `evmAddress -> accountId` will be initially filled during the state migration
- Implement logic to rebuild the `evmAddress -> accountId` map from state e.g. similar to `AliasManager.rebuildAliasesMap` implementation
- Update `AliasManager.rebuildAliasesMap` method to work only for EC/ED key bytes aliases 

#### State migration
- Create new class in `com.hedera.node.app.service.mono.state.migration` that implements the migration logic for the accounts and contracts
- During migration all `evmAddress` entries that are currently in `AliasManager` map should be moved to the `evmAddress -> accountId` map
- After migration is done the `AliasManager` map should contain only key based alias entries
- Migration steps are described [here](https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-631.md#alias-to-virtual-account-migration)

### Transactions

#### Update auto & lazy creation logic to work with virtual addresses
- All current implementation in `CryptoCreate`, `CryptoTransfer`, `EthereumTransaction` and `AbstractRecordingCreateOperation` should be updated to work with the new `evmAddress -> accountId` map
- All `evmAddress` account creation logic in `CryptoCreate`, `CryptoTransfer`, `EthereumTransaction` should be updated to create the account with a virtual address and set it as default one

#### Update CryptoUpdate transaction to be able to modify virtual addresses list
- Implement reading the virtual address update from the transaction body in `CryptoUpdateTransitionLogic.asCustomizer`
- Implement additional check in `CryptoUpdateTransitionLogic.sanityCheck` for verifying that transaction is signed by the ECDSA private key that maps to the virtual address that is being updated

#### Update ContractCreate & ContractCall to work with virtual addresses
- `ContractCreate/ContractCall` that result in new contract creation should add an Ethereum public address `evmAddress` for `CREATE` & `CREATE2` to `contract.account.virtualAddresses`
- Update `ContractCreateTransitionLogic/ContractCallTransitionLogic` implementations to parse the `virtual_address_override` value from the transaction body and determine the appropriate Ethereum public address `evmAddress` per transaction as described [here](https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-631.md#transaction-evm-address-value)

### Queries
- Update `GetAccountInfoAnswer.responseGiven` to return the virtual addresses list for an account
- Introduce new `CryptoGetAccountVirtualAddressesQuery` in order to return unbounded list of virtual addresses for an account

## Acceptance Tests

### Positive Tests
TODO

### Negative Tests
TODO
