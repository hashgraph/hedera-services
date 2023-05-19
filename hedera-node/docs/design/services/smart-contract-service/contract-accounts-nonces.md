# Contract Accounts Nonces Externalization

## Purpose

In order to provide more complete EVM account equivalence support and a better developer experience for [eth_getTransactionCount](https://ethereum.org/en/developers/docs/apis/json-rpc/#eth_gettransactioncount) we must externalize the contract account nonce value changes from Consenus Node to Mirror Node.

## Goals

- Externalize contract accounts nonces in transaction records
- Migrate all existing contract nonce values to Mirror Node

## Non Goals

- Update existing logic for storing contract nonce updates in Consensus Node state
- Handle nonce updates for EOAs

## Assumptions

- Account nonces are updated in Consensus Node state using in the following places:
  - [ContractCreateTransitionLogic](https://github.com/hashgraph/hedera-services/blob/develop/hedera-node/hedera-mono-service/src/main/java/com/hedera/node/app/service/mono/txns/contract/ContractCreateTransitionLogic.java#L209)
  - [ContractCallTransitionLogic](https://github.com/hashgraph/hedera-services/blob/develop/hedera-node/hedera-mono-service/src/main/java/com/hedera/node/app/service/mono/txns/contract/ContractCallTransitionLogic.java#L148)
  - [AbstractEvmRecordingCreateOperation](https://github.com/hashgraph/hedera-services/blob/develop/hedera-node/hedera-evm/src/main/java/com/hedera/node/app/service/evm/contracts/operations/AbstractEvmRecordingCreateOperation.java#L122)
- Mirror Node is able to process nonce updates through transaction records 

## Architecture

The following is a table with general use cases and behavior for Ethereum and Hedera:

| Use case                                                                                | Behavior in Ethereum                                                                                       | Behavior in Hedera (current)                                                                                                                                                     | Behavior in Hedera (desired)                                                                                                                                                     |
|-----------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EOA transaction (`EthereumTransaction`)                                                 | EOA nonce is incremented                                                                                   | EOA nonce is incremented, updates are not externalized to Mirror Node through transaction records, but can be picked up by reading the value from the `EthereumTransaction` body | EOA nonce is incremented, updates are not externalized to Mirror Node through transaction records, but can be picked up by reading the value from the `EthereumTransaction` body |
| EOA transaction (`ContractCall` or `ContractCreate`)                                    | -                                                                                                          | -                                                                                                                                                                                | -                                                                                                                                                                                |
| Contract transaction resulting in `CREATE/CREATE2` (`EthereumTransaction`)              | initial contract nonce value is 1; nonce is incremented for each contract creation initiated by an account | initial contract nonce value is 1; nonce is incremented for each contract creation initiated by an account, updates are not externalized to Mirror Node                          | initial contract nonce value is 1; nonce is incremented for each contract creation initiated by an account, updates are externalized to Mirror Node                              |
| Contract transaction resulting in `CREATE/CREATE2` (`ContractCall` or `ContractCreate`) | -                                                                                                          | initial contract nonce value is 1; nonce is incremented for each contract creation initiated by an account, updates are not externalized to Mirror Node                          | initial contract nonce value is 1; nonce is incremented for each contract creation initiated by an account, updates are externalized to Mirror Node                              |

### Contract Nonce Externalization

- We can keep a `ContractId -> nonce` map inside `TxnAwareRecordsHistorian`, it would be updated on each call to `AbstractRecordingCreateOperation` (possibly using its `createOperationExternalizer` field) even if the contract creation operation does not succeed
- When the ledgers `commit()` are executed, and we go to `TxnAwareRecordsHistorian.saveExpirableTransactionRecords` method we can save the constructed map to the top-level `RecordStreamObject`
- This would be done via setting it inside it's `ExpirableTxnRecord` in the corresponding `EvmFnResult` field
    - `contractCreateResult` if the top level transaction was `ContractCreate`
    - `contractCallResult` if the top level transaction was `ContractCall`

### Migration Of Nonces For Existing Contracts

- We need perform a migration for all existing contract nonces from Consensus Node state to Mirror Node e.g. similar to traceability migration performed in the past
- For all contracts in state we should produce synthetic transaction records that would contain the contract id and it's corresponding nonce value

### Feature Flags

We will need to add two feature flags:
- one for toggling the contract nonce externalization in transaction handling
- one for triggering the migration of contract nonces for existing contracts

### Merging of contract into hollow account

When contract is merged into an existing hollow account we are correctly setting its nonce value to 1 in `HederaCreateOperationExternalizer.finalizeHollowAccountIntoContract` as specified in [EIP-161](https://github.com/ethereum/EIPs/blob/master/EIPS/eip-161.md).

### New Classes

No new classes expected to be created as part of this design.

## Acceptance Tests

### Positive Tests

* Verify that when contract A successfully deploys contract B, the nonce of contract A is incremented by 1 and the nonce of contract B is set to 1.
* Verify that when contact A with nonce 2 calls contract B with nonce 1 that creates contract C and contract D, the nonce of contract A is still 2 and the resulting `ContractFunctionResult` has the following nonce values:
  * B -> 3
  * C -> 1
  * D -> 1
* Verify that when contract A is merged into hollow account H, the nonce of the resulting account is set to 1.
* Verify that when contract A is merged into hollow account H and the init code of A also deploys contract B, the nonce of the resulting account is 2 and the nonce of contract B is set to 1.

### Negative Tests

* Verify that when contract A fails to deploy contract B, the nonce of contract A is still incremented by 1.
