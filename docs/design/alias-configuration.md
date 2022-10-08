# Alias Configuration

## Purpose
Account aliases allow an account to be referenced via a different id than the `<shard>.<realm>.<num>` accountId format which is native to the Hedera ledger.
As part of [HIP 32](https://github.com/hashgraph/hedera-improvement-proposal/blob/master/HIP/hip-32.md) the functionality was made available via the auto-create flow in which an ED25519 or ECDSA public key was provided and used as the alias.

To support greater scenario especially smart contract related scenarios for ECDSA based key the ledger needs to support additional ways of setting the alias at creation.

This document explains how additional Crypto transaction flows may be used to create an account with an alias.

## Goals

- Validate that an accounts alias and public keys map to each other
- Update alias specification to support ED/ECDSA public key or ethereumAddress styled conforming values
- Update the `CryptoCreate` transaction to support the provision of an alias
- Support the `Lazy Account Creation Flow`
  - Provision of an initial transaction with an alias and no public key
  - Support the receiving of value by the new account
  - Prevent the success of transaction requiring the accounts signature
  - Extract the public key from a future signed transaction that the account submits

## Non Goals

- Updating existing accounts alias fields

## Architecture

### Alias to Public Key Validation

- In case of ED25519 the provided alias must match the public key of the account that we want to create
- In case of the ECDSA the alias must match the public key or be the public address of the ECDSA key provided/set on the account (when the alias is the public address of the ECDSA key and the key is not set on the account we have to create the account with empty public key field and to store the public address as alias)
- We can use MiscUtils.isSerializedProtoKey logic to validate keys
- Additionally we can validate with regex for evm_address (if hex encoded string), otherwise if bytes array check its length
- We can check existing aliases using AliasManager.lookupIdBy method
- To derive public address from public key we can use recoverAddressFromPubKey from hapi-utils

### Support Public Key or Ethereum Address Alias

- For CryptoTransfer we already support alias public key auto create
- To support Ethereum address for CryptoTransfer we will need to: 
  - Change the implementation in AliasResolver.resolveInternalFungible method, it already has some EVM address handling
  - Change the AutoCreationLogic.create to expect alias that might not be key but a public address

### Crypto Create Transaction Alias Logic

- Extract alias from CryptoCreateTransactionBody and validate it in validate() method, checking if the alias matches the key or can be derived from the key (for EVM address alias)
- For the ECDSA key case when creating the account we should set its alias to the value of the key as well. We should also add entries to the alias->accountId map for the ECDSA key and the EVM address derived from it
- For the ETH address case we can't set the key for the account (as it will be missing), that would be done as the final part of the lazy account create. The account alias will be set to the value of the ETH address

### Lazy Account Create Logic

Usually accounts are created with the provision of the public key.
However, in other ledgers where accounts are `ECDSA` based (mostly EVM chains) it is possible to reference an account via the Ethereum account address.
This is the rightmost 20 bytes of the 32 byte `Keccak-256` hash of the `ECDSA` public key of the account. This calculation in the manner described in the Ethereum Yellow Paper. 

To support the use of this format as an alias the `Lazy Account Creation Flow` may be adopted.
![Lazy Account Create Flow](images/lazy-account-create.png)
In this flow
- An initial transaction may supply an accountId using the `<shard>.<realm>.<ethereumAccountAddress>`
- The ledger should create the account using the given alias without the public key
- The ledger should support the receiving of HBAR value but prevent the account from taking part in transactions it needs to sign
- The ledger should extract the public key from a future transaction submitted by the account using its public key and verify it maps to the alias on the account
- The ledger should complete the account creation process by setting the key value of the account

## Non-Functional Requirements

- Support lazy account create initialization and completion TPS (100)

## Open Questions

## Acceptance Tests

## Cases

### CryptoCreate

With ED key:
- CryptoCreate with ED key and no alias specified in the tx body: the resulting account will have `key` = `ED key` and no alias;
- CryptoCreate with ED key and `ED key alias` specified in the tx body: validate that the key and alias match; create an account with `key` = `ED key` and `alias` = `ED key alias`;

With EC key:
**Always validate the 0.0.ECDSA alias format is missing from the alias map as well as the calculated evm_address alias format**

- CryptoCreate with EC key and no alias specified in the tx body: the resulting account will have `key` = `EC key` and `alias` = `evmAddress` derived from the EC key;
- CryptoCreate with EC key and `EC Key` alias specified in the tx body: validate that the key and alias match; the resulting account will have `key` = `EC key`, `alias` = `EC key alias` + we will automatically calculate the `evmAddress` derived from the EC key and store it in the in-memory structure;
- CryptoCreate with EC key and `evmAddress alias` specified in the tx body: first we validate that the alias can be derived from the EC key; the resulting account will have `key` = `EC Key` and `alias` = `evmAddress`;
- CryptoCreate with no key and `evmAddress alias` specified in the tx body: this is lazy create 1/2: a hollow account with only alias = `evmAddress` will be created; lazy create 2/2 is required before the account can sign transactions;

### CryptoTransfer

- Crypto Transfer Transaction to EVM address alias should create an account with its alias field set to the EVM address and its key will be empty (to be populated by a signed transaction as part of lazy account create)
- For any type of signed transaction coming after an initial Crypto Transfer to EVM address e.g. (CryptoTransfer, ContractCreate, ContractCall, EthereumTransaction, TokenAssociation):
  - We need to extract the public key from signature with extractSig
  - After that to use the extracted key for recovering the public address (recoverAddressFromPubKey)
  - Recovered address must match with the alias (public address of the eth account)
  - If the recovered address match with the address stored in the alias we have to update the key property of the account and store it in the ledger
  - With the above step the process of lazy creation will be finished
- Crypto Transfer Transaction to ECDSA key alias should create an account with its alias field set to the ECDSA key, populate the alias map with the EVM address calculated based on the ECDSA key, and the key field set with the value of the ECDSA key (currently implemented) and another Crypto Transfer Transaction to the EVM address corresponding to the ECDSA key should transfer to the account created in the previous transaction
- Crypto Create Transaction with ECDSA key alias:
    
    
  1. Create an account with its alias and key fields set to the ECDSA key, and populate the alias map with the EVM address calculated based on the ECDSA key.
      - Account with:
          - hedera id: 0.0.20
          - admin key: ECDSA key
          - alias: 0.0.ECDSA key
      - AliasManager map with entries:
          - calculated EVM_address alias based on the ECDSA key → hedera id
          - 0.0.ECDSA key → hedera id
  
  2. Attempting another Crypto Create Transaction with the same ECDSA key alias or EVM address corresponding to that key should fail
    
- Crypto Create Transaction with EVM address alias
    1. Create an account with its alias field set to the EVM address and its key will be empty
        - Account with:
            - hedera id: 0.0.20
            - admin key:
            - alias: EVM_address alias
        - AliasManager with:
            - map with the EVM_address alias based on the ECDSA key → hedera id
    2. When a signed transaction referencing the account by the EVM_address alias is received:
        - Account with:
            - hedera id: 0.0.20
            - admin key: ECDSA key
            - alias: EVM_address alias
        - AliasManager with:
            - map with the EVM_address alias based on the ECDSA key → hedera id

- Ethereum Transaction for crypto transfer should create an account with its alias field set to the `to` value of the transaction and its key will be empty (i.e. lazy create hollow account)
  1. Validate that `to` and `value` are set, `to` should be an EVM address and `value` should be non-zero
  2. If there is no existing account with the `to` EVM address execute the same logic as in Crypto Transfer with EVM address

### Notes

- For the EVM address cases we should store the alias value in the state not only in the memory structure
- It is ok for different accounts to have the same key
- For accounts with non-empty aliases if we have a key that is set, it should match the alias (for key aliases the values should be equal and for EVM aliases the key should be the ECDSA key from which the EVM address is derived)