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

### Support Public Key or Ethereum Address Alas

### Crypto Create Transaction Alias Logic

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