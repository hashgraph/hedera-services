# Alias Configuration

## Purpose
Account aliases allow an account to be referenced via a different id than the `<shard>.<realm>.<num>` account ID format which is native to the Hedera ledger.
As part of [HIP 32](https://github.com/hashgraph/hedera-improvement-proposal/blob/master/HIP/hip-32.md) the functionality was made available via the auto-create flow in which an `ED25519` or `ECDSA` public key was provided and used as the alias.

To support greater scenarios, especially smart contract related scenarios for ECDSA-based key, the ledger needs to support additional ways of setting the alias at creation.

This document explains how additional crypto and smart contract service transaction flows may be used to create an account with an alias.

## Goals

- Update the `CryptoCreate` transaction to support the provision of an `alias` and `evm address`
- Update the `CryptoTransfer` transaction to support auto creation via `evm address`
- Support the `Lazy Account Creation Flow`
  - Provision of an initial transaction with an alias and no public key
  - Support the receiving of value by the new account (hbar and already associated tokens)
  - Prevent the success of transactions requiring the account's signature
  - Extract the public key from a future signed transaction that the account submits
- Support the `Lazy Account Creation Flow` via the EVM
- Validate that an account's alias and public keys map to each other
- Update alias specification to support ED/ECDSA public key or ethereumAddress styled conforming values

## Non-Goals
- Updating existing accounts' alias fields
- Updating the logic of account deletion to include clearing up the aliases of deleted accounts

## Architecture

### Alias to Public Key Validation

- In the case of `ED25519`, the provided alias must match the public key of the account that we want to create
- In the case of `ECDSA`, the alias must match the public key or be the public address of the `ECDSA` key provided/set on the account
  - When the alias is the public address of the `ECDSA` key and the key is not set on the account, we have to create the account with an empty public key field and store the public address as an alias, i.e. create a `hollow/lazy account`
- For ensuring alias validity, we can use `MiscUtils.isSerializedProtoKey` logic to validate key aliases and a length check for public address aliases (any sequence of 20 bytes can be a public address)
- We can check existing aliases using the `AliasManager.lookupIdBy()` method
- To derive the public address from a public key, we can use `EthSigsUtils.recoverAddressFromPubKey()` from `hapi-utils`

### Lazy Account Creation

Usually, accounts are created with the provision of the public key.
However, in other ledgers where accounts are `ECDSA` based (mostly EVM chains) it is possible to reference an account via the Ethereum account address.
This is the rightmost 20 bytes of the 32-byte `Keccak-256` hash of the `ECDSA` public key of the account. This calculation is in the manner described in the Ethereum Yellow Paper.

To support the use of this format as an alias the `Lazy Account (hollow account) Creation Flow` may be adopted.
![Lazy Account Create Flow](/docs/design/images/lazy-account-create.png)
In this flow
- An initial transaction may supply an accountId using the `<shard>.<realm>.<ethereumAccountAddress>`
- The ledger should create the account using the given alias without the public key
- The ledger should support the receiving of HBAR value but prevent the account from taking part in transactions it needs to sign
- The ledger should extract the public key from a future transaction submitted by the account using its public key and verify it maps to the alias on the account
- The ledger should complete the account creation process by setting the key value of the account

### CryptoCreate Transaction With Alias and Ethereum address
- Extend the `CryptoCreateTransactionBody` protobuf definition with new `alias` and `evmAddress` fields, corresponding to a public key and public address aliases respectively
- Extract `alias` and/or `evmAddress` from `CryptoCreateTransactionBody` and validate in `CryptoCreateChecks`:
  - add new logic to ensure the given `alias`/`evmAddress` is not already linked to another account
  - add new logic to ensure that the `key`, `alias`, and `evmAddress` all point to the same key
- All the possible combinations of creating a new account via `CryptoCreate` are denoted in the table below. A couple of scenarios, which should be paid more attention to:
  - In the scenario where only the `key` field is set to an `ECDSA` key, we automatically set its alias to the value of the key as well. We also add an entry in the alias map for the EVM address.
  - In the scenario where only an `evmAddress` is given, we can't set the key for the account (as it will be missing). That is done as the final part of the lazy account creation. The account alias will be set to the value of the ETH address.

| CryptoCreateTransactionBody                                                                 | Resulting account in the consensus node state                                                                | Resulting entries in consensus node in-memory map     | Expected transaction record entries                                                                                                                                                                                                                                                                                                             |
|---------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|-------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `key` =  ED key <br/> `alias` = - <br/> `evm_address` =  -                                  | `admin key` = ED key <br/> `alias` = -                                                                       | -                                                     | `transaction_record.alias` = - <br/> `transaction_record.evm_address` = - <br/> <br/> The key is present in the transaction body and not present in the transaction record.                                                                                                                                                                     |
| `key` = -  <br/> `alias` = ED Key alias <br/> `evm_address` = -                             | `admin key` = ED key <br/> `alias` = ED key alias                                                            | ED key alias → hedera id                              | `transaction_record.alias` = - <br/> `transaction_record.evm_address` = - <br/> <br/>The key and alias are present in the transaction body and not present in the transaction record.                                                                                                                                                           |
| `key` = ED key <br/> `alias` = ED Key alias <br/> `evm_address` = -                         | `admin key` = ED key <br/> `alias` = ED key alias                                                            | ED key alias → hedera id                              | `transaction_record.alias` = - <br/> `transaction_record.evm_address` = - <br/> <br/> The key and alias are present in the transaction body and not present in the transaction record.                                                                                                                                                          |
| `key` = EC key <br/>`alias` = - <br/>`evm_address` = -                                      | `admin key` = EC key <br/> `alias` = EVMAddress alias                                                        | EVMAddress alias → hedera id                          | `transaction_record.alias` = - <br/> `transaction_record.evm_address` = EVMAddress alias <br/><br/>The key is present in the transaction body and not present in the transaction record.                                                                                                                                                             |
| `key` = - <br/> `alias` = EC key alias <br/> `evm_address` = -                              | `admin key` = EC key <br/> `alias` = EC key alias                                                            | EC key alias → hedera id EVMAddress alias → hedera id | `transaction_record.alias` = - <br/> `transaction_record.evm_address` = EVMAddress alias <br/><br/>The key and alias are present in the transaction body and not present in the transaction record.                                                                                                                                             |
| `key` = EC key <br/>`alias` = - <br/>`evm_address` = EVMAddress                             | `admin key` = EC key <br/> `alias` = EVMAddress alias                                                        | EVMAddress alias → hedera id                          | `transaction_record.alias` = - <br/> `transaction_record.evm_address` = - <br/><br/> The key and evm_address are present in the transaction body and not present in the transaction record.                                                                                                                                                     |
| `key` = EC key <br/>`alias` = EC key alias <br/>`evm_address` = -                           | `admin key` = EC key <br/> `alias` = EC key alias                                                            | EC key alias → hedera id EVMAddress alias → hedera id | `transaction_record.alias` = - <br/> `transaction_record.evm_address` = EVMAddress alias <br/><br/>The key and alias are present in the transaction body and not present in the transaction record.                                                                                                                                             |
| `key` = EC key <br/>`alias` = EC key alias<br/> `evm_address` = EVMAddress                  | `admin key` = EC key <br/> `alias` = EC key alias                                                            | EC key alias → hedera id EVMAddress alias → hedera id | `transaction_record.alias` = - <br/> `transaction_record.evm_address` = - <br/><br/>The key, evm_address, and alias are present in the transaction body and not present in the transaction record.                                                                                                                                              |
| Hollow account 1 of 2 <br/>`key` = - <br/> `alias` = - <br/> `evm_address` = EVMAddress     | `admin key` = - <br/> `alias` = EVMAddress alias                                                             | EVMAddress alias → hedera id                          | `transaction_record.alias` = - <br/> `transaction_record.evm_address` = - <br/><br/>The key and evm_address are present in the transaction body and not present in the transaction record. = - transaction_record.evm_address = - The key, evm_address, and alias are present in the transaction body and not present in the transaction record. |
| Hollow account 2 of 2<br/> Triggers a synthetic `CryptoUpdateTransaction`: `key` = <EC Key> | Existing account in Hedera is updated with:<br/> `admin key` = EC key <br/> `alias` = EVMAddress (no change) | EVMAddress alias → hedera id (no change)              | synthetic CryptoUpdate txn record with: <br/>`transaction_record.alias` = - <br/> `transaction_record.evm_address` = - <br/><br/>The key is externalized in the synthetic `CryptoUpdateTransactionBody.key` field and not present in the synthetic transaction record.                                                                          |

### CryptoTransfer Ethereum address support 

- For `CryptoTransfer` we already support  auto creation with a public key alias
- To support an Ethereum address for `CryptoTransfer` we will need to:
    - Change the implementation in the `AliasResolver.resolveInternalFungible` method, it already has some EVM address handling
    - Change the `AutoCreationLogic.create` to expect an alias that might not be key but a public address, and to charge all related hollow account created fees
- The following table sums up all the ways a user will be able to create an account via `CryptoTransfer` to an alias (the last row is the newly added way of creating lazy accounts):

| CryptoTransfer target      | Resulting synthetic CryptoCreateTransactionBody                                  | Resulting account in consensus node state        | Resulting entries in consensus node in-memory map     | Expected transaction record entries                                                                                                                                                                                                         |
|----------------------------|----------------------------------------------------------------------------------|--------------------------------------------------|-------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ED25519` public key alias | `admin key` = ED key <br/>`alias` = ED key alias <br/>`evm_address` = -          | `admin key` = ED key <br/>`alias` = ED key alias | ED key alias → hedera id                              | The `key` and `alias` are externalized in the `CryptoCreateTransactionBody.key` and `CryptoCreateTransactionBody.alias` fields and not present in the transaction record.                                                                   |
| `ECDSA` public key alias   | `admin key` = EC key <br/>`alias` = EC key alias <br/>`evm_address` = EVMAddress | `admin key` = EC key <br/>`alias` = EC key alias | EC key alias → hedera id EVMAddress alias → hedera id | The `key`, the `alias`, and the `evm address` are externalized in the `CryptoCreateTransactionBody.key` , `CryptoCreateTransactionBody.alias` , `CryptoCreateTransactionBody.evm_address` fields and not present in the transaction record. |
| `EVMAddress` alias         | `admin key` = - <br/> `alias` = - <br/>`evm_address` = EVMAddress alias          | `admin key` = - <br/>`alias` = EVMAddress alias  | EVMAddress alias → hedera id                          | The `evm address` is externalized in the `CryptoCreateTransactionBody.evm_address` field and not present in the transaction record.                                                                                                         |


### Lazy Account Creation Through the EVM
- Implement a new version of the EVM (see [the evm versioning design doc](/docs/design/services/smart-contract-service/evm-versioning.md)) that will allow transfers of value to non-existing addresses. In this way, instead of halting the execution frame with `INVALID_SOLIDITY_ADDRESS`, the transfer will be considered a `lazy account` creation attempt. 
- The account creation fees must be charged from the available gas in the frame
  - Any EVM execution that cannot pay for a lazy account creation through the available gas will halt with `INSUFFICIENT_GAS` exceptional halt reason
- The EVM can plug into the pre-existing logic in `AutoCreationLogic.create()` in order to create the lazy accounts. 
- The EVM module's `HederaEvmMessageCallProcessor` will be extended with a `executeLazyCreate()` method, implemented by the service's `HederaMessageCallProcessor`
- Supported scenarios will include:
  - Top-level `EthereumTransaction` crypto transfers
    - `ContractCallTransitionLogic` will need to be updated to allow such transactions to pass, instead of failing with `INVALID_CONTRACT_ID`
  - Nested EVM calls with value via `.call()` 
    - Will require a new implementation of `HederaCallOperation` which will consider value transfers to non-existing addresses as a lazy creation attempt
    - `.send()`, `.transfer()` can also be used for value transfers in smart contracts. However, they cap their `gasLimit` at 2300, which won't be sufficient for a lazy creation, and are considered bad practice, so we won't add any specific logic in order to support them.  
  - `TransferPrecompile` will also need to be altered to allow transfers to a non-existing address. All of the following precompile functions must be supported:
    - `cryptoTransferV1`
    - `cryptoTransferV2`
    - `transferToken`
    - `transferTokens`
    - `transferNFT`
    - `transferNFTs`
    - `ERC20.transfer`
    - `ERC20.transferFrom`
    - `ERC721.transferFrom`
    - `IHederaTokenService.transfer`
    - `IHederaTokenService.transferFrom`

### Lazy Account Fee Charging
- All lazy account fees (`CryptoCreate` for creation and `CryptoUpdate` for finalization) are to be charged upon **creation**
  - `CryptoCreateTransitionLogic` will need to be updated to charge additional `CryptoUpdate` fees, separate from the already charged `CryptoCreate` fees 
  - `AutoCreationLogic.create()` will need to be updated to charge additional `CryptoUpdate` fees, separate from the already charged `CryptoCreate` fees

### Lazy Account Finalization
- For any type of signed transaction coming after the creation of a `hollow/lazy account`, we will need to check whether the payer is a hollow account. If so, we will try to finalize the `lazy account`:
    - `StateChildrenSigMetadataLookup` will return `SigningMetadata` for the hollow account, consisting of a new `JHollowKey`, containing the bytes of the `evm address`
    - During ingestion/prechecks:
      - `PrechekVerifier` will check whether the payer key is of type `JHollowKey`
      - If so, scan through the sig map for a full-prefix `ECDSA` key signature from an `ECDSA` key that maps to the evm address from the `JHollowKey`
        - If there is a match, replace the `JHollowKey` with the `JECDSASecpk2561Key` in the required keys list before the payer signature verification
        - If there is no match, do not accept the transactions
    - During handle:
      - After signature rationalization, `SigsAndPayerKeyScreen` will check whether the `payerReqSig` in the `RationalizedSigMeta` is `JHollowKey`
      - If so, find the corresponding `ECDSA` key in the sig map and replace the `JHollowKey` with a `JECDSASecp256k1Key`, constructed from this ECDSA key
      - Update the account with the `ECDSA` key from the sig meta 
      - Export a synthetic `CryptoUpdate` child record for the account completion with its new key
- An `EthereumTransaction`'s wrapped sender can also be a hollow account. In these cases, if the Ethereum transaction signature verification is valid, we should also finalize the sender and create a `CryptoUpdate` child record in `SigsAndPayerKeyScreen`.
  -  Note that the finalization of the sender of an `EthereumTransaction` is independent of the finalization of the payer of a transaction. In other words, we can have 2 hollow account finalizations in this case - 1 for the payer of the Hedera `EthereumTransaction`, and another for the sender of the wrapped Ethereum transaction.
- `CREATE2` contract creation to an address already occupied by a hollow account must finalize it:
  - `AbstractRecordingCreateOperation` must be altered to check whether the targeted address is occupied by a hollow account, allow the creates in those cases, and, upon completion, convert the hollow account into a contract, setting its key to the default `JContractIDKey`.

## Non-Functional Requirements

- Support lazy account create initialization and completion TPS (100)

## Open Questions

## Acceptance Tests

## Cases

See the scenarios described in the [HIP-583 test plan](/docs/test-plans/hip-583.md)

### Notes

- For the EVM address cases we should store the alias value in the state not only in the memory structure
- It is ok for different accounts to have the same key
- For accounts with non-empty aliases if we have a key that is set, it should match the alias (for key aliases the values should be equal and for EVM aliases the key should be the ECDSA key from which the EVM address is derived)