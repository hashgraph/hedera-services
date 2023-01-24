# Virtual Addresses

## Purpose
Introduce the concept of virtual addresses in order to bridge the conceptual gap between Hedera's and Ethereum's view of accounts.
Virtual addresses will resolve the issue of account EVM compatibility and identification and will be responsible for EVM address related logic.

## Goals
- Add list of evmAddress values known as “Virtual Address” to Hedera accounts which govern the address the EVM observes for a given account transaction
- Hedera Accounts can add, disable and remove virtual address entries as desired
- The Hedera network will:
  - Validate ownership by extracting the public key from the signature and comparing the calculated public address to the `evmAddress` passed in on addition of the virtual address
  - Maintain an `evmAddress -> accountId` map thereafter
- Contract accounts may utilize the `evmAddress` to store their address in accordance with `CREATE` and `CREATE2` EVM operations
- Restore HIP 32's consistency
  - The values of the account aliases will only contain EC/ED key bytes (as in HIP 32)
  - The values of the account aliases will no longer contain EVM addresses
- Perform seamless state migration of accounts and contracts so that by default they benefit from the introduction of virtual addresses

## Non Goals
- Support removal of virtual addresses between accounts
- Support transferring of virtual addresses between accounts

## Architecture

### Account state updates
- Update `HederaAccount` interface to have the ability to:
  - Read/write virtual addresses
  - Read/write a default virtual address
  - Track nonce values for each virtual address
```
public interface HederaAccount {
...

	Set<ByteString> getVirtualAddresses();

	void setVirtualAddresses(Set<ByteString> virtualAddresses);

	ByteString getDefaultVirtualAddress;

	void setDefaultVirtualAddress(ByteString virtualAddress);

	void setVirtualAddressNonce(ByteString virtualAddress, long nonce);

	long getVirtualAddressNonce(ByteString virtualAddress);

}
```
- Implement methods from `HederaAccount` interface in `MerkleAccount` and `OnDiskAccount` classes
- Update `MerkleAccountState` with matching changes as in `HederaAccount`
  - Define a field to store the virtual address to nonce map of type `Map<ByteString, Long>`

#### Separate EVM address lookup from aliases
- Introduce a new class for managing `evmAddress -> accountId` in-memory map
- The `evmAddress -> accountId` will be initially filled during the state migration
- Implement logic to rebuild the `evmAddress -> accountId` map from state e.g. similar to `AliasManager.rebuildAliasesMap` implementation
- Update `AliasManager.rebuildAliasesMap` method to work only for EC/ED key bytes aliases 

#### Introduce special system account
- Create new system account for exposed keys/addresses from Ethereum tools and external chains that will be used to ensure that no such keys/addresses will be allowed 
- The limitation for accounts to have a maximum limit of virtual addresses will not be applicable to this system account i.e. this account will have unbounded list of virtual addresses 

#### State migration
- Create new class in `com.hedera.node.app.service.mono.state.migration` that implements the migration logic for the accounts and contracts
- During migration all `evmAddress` entries that are currently in `AliasManager` map should be moved to the `evmAddress -> accountId` map
- After migration is done the `AliasManager` map should contain only key based alias entries
- The migration is not reversible so if data expected in the new version of the state is missing we should fall back to the way we retrieve data from the old version of the state
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

#### Externalize changes through transaction records
- All virtual address values will be exposed in record files as described in the table from [HAPI changes section](https://github.com/hashgraph/hedera-improvement-proposal/blob/main/HIP/hip-631.md#hapi-changes) of HIP-631
- Contract nonce updates will be externalized by adding a map `ContractId -> nonce` in `ContractFunctionResult` message that will be populated in the top-level transaction records
  - We can keep a `ContractId -> nonce` map inside `TxnAwareRecordsHistorian`, it would be updated on each call to `AbstractRecordingCreateOperation` even if the contract creation operation does not succeed
  - When the ledgers `commit()` are executed, and we go to `TxnAwareRecordsHistorian.saveExpirableTransactionRecords` method we can save the constructed map to the top-level `RecordStreamObject`
  - This would be done via setting it inside it's `ExpirableTxnRecord` in the corresponding `EvmFnResult` field
    - `contractCreateResult` if the top level transaction was `ContractCreate`
    - `contractCallResult` if the top level transaction was `ContractCall`
- Tracking nonces for EOAs for EthereumTransactions is not changed

### Queries
- Update `GetAccountInfoAnswer.responseGiven` to return the virtual addresses list for an account
- Introduce new `CryptoGetAccountVirtualAddressesQuery` in order to return unbounded list of virtual addresses for an account

### Global properties
- Create the following properties to configure the virtual addresses' behavior:
  - `virtualAddresses.maxNumber` - the maximum number of virtual addresses per account
  - `virtualAddresses.canDisable` - toggle for the ability to disable virtual accounts
  - `virtualAddresses.canRemove` - toggle for the ability to remove virtual accounts (will not be needed initially since removal is not a goal)

## Phases of development

The development will be done in iterative phases that build on previous ones. Protobuf changes needed for a given phase should be introduced in the preceding phase.

- Phase 0
  - Protobuf changes:
    - Add virtual addresses list to `AccountInfo` proto (already added)
    - Add a `ContractId -> nonce` map in message `ContractFunctionResult` in order to externalize contract nonce updates
  - Create design doc and test plan
- Phase 1
  - Implement changes to state
    - Add virtual addresses list account state (capped to 1)
    - Track nonces by `evmAddress` and support incrementing a specific address increment
  - Account aliases map split
    - Introduce the `evmAddress -> accountId` map
    - Update alias resolution logic to work with `alias -> accountId` map for public key aliases and with `evmAddress -> accountId` map for `evmAddress` aliases
    - Add support for `CryptoGetInfoQuery` with `evmAddress` to use the `evmAddress -> accountId` map
  - Create new system account for exposed keys from Ethereum tools and external chains
- Phase 2
  - Protobuf changes
    - Add `virtual_address_override` to `ContractCall` and `ContractCreate` transactions
  - Support virtual address addition on `CryptoUpdate`
  - Account migration
    - All ECDSA accounts with an alias get a single virtual address
    - All ECDSA accounts with `evmAddress` stored in alias path get a single virtual address 
    - All ECDSA accounts which calculated addresses are not present in the map will get a single virtual address
    - All contracts with valid 20 byte addresses will get a single virtual address
  - Accounts to utilize virtual addresses on creation
    - EOAs set virtual address for regular `CryptoCreate` with ECDSA key, auto-create with alias and lazy-crate with `evmAddress`
    - Contracts set virtual address for Smart contract `new()` (`CREATE` & `CREATE2`) and `EthereumTransaction CREATE` with empty `to` address
    - Creation logic verifies `evmAddress` uniqueness and fails/doesn't set if entry already exists
- Phase 3
  - Protobuf changes
    - Add disable to `CryptoUpdateTransactionBody.virtual_address_update`
  - Expand virtual address limit per account to 3
  - Virtual address addition to existing accounts with signature verification logic and verification for `evmAddress` uniqueness
  - Support `virtual_address_override` logic for `ContractCreate` and `ContractCall` transaction
- Phase 4
  - Support virtual address disabling on `CryptoUpdate`
  - Design virtual address removal & transfer

## Acceptance Tests

### Positive Tests
- `CryptoCreate` with `ECDSA key` should create an account with single virtual address and make it the default virtual address
- `CryptoCreate` with `ECDSA key alias` should create an account with single virtual address and make it the default virtual address
- `CryptoCreate` with `evmAddress alias` should create an account with single virtual address and make it the default virtual address
- `CryptoCreate` with `evmAddress` should create a hollow account with single virtual address and make it the default virtual address
- `CryptoTransfer` with `ECDSA key alias` to a non-existing account should auto-create an account with single virtual address and make it the default virtual address
- `CryptoTransfer` with `evmAddress alias` to a non-existing account should auto-create an account with single virtual address and make it the default virtual address
- `CryptoTransfer` with `evmAddress` to a non-existing account should lazy-create a hollow account with single virtual address and make it the default virtual address
- `EthereumTransaction` to a non-existing account with `tx.to` EVM address value should lazy-create a hollow account with single virtual address and make it the default virtual address
- `ContractCreate/ContractCall` for an account with a default virtual address should use that address in the EVM
- `ContractCreate/ContractCall` with `virtual_address_override` address value should use that address in the EVM
- `ContractCreate/ContractCall` resulting in creation of a new contract should add the CREATE/CREATE2 EVM address value to `contract.account.virtualAddresses`
- `CryptoUpdate` with `virtual_address_update.add.address` for an existing account should add a new virtual address, if `virtual_address_update.add.is_default` is set to `true` the added address should become the default virtual address
- `CryptoUpdate` with `virtual_address_update.disable` value that is present in the virtual address list for an existing account should disable the virtual address
- `CryptoUpdate` with `virtual_address_update.remove` value that is present in the virtual address list for an existing account should remove the virtual address from the list
- `CryptoGetInfoQuery` for an existing account should return the virtual addresses list for the account

### Negative Tests
- Virtual address update for a contracts should fail, ensuring contract accounts immutability
- Any transaction using an `evmAddress` that is in the not-allowed list should fail
- `CryptoUpdate` with `virtual_address_update.disable/remove` value that matches the default virtual address should fail