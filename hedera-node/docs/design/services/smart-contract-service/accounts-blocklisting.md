# Accounts Blocklisting

## Purpose

In Ethereum, everyone that knows a private key is able to authorize a transaction from the corresponding EVM public address, and the first to send a transaction would be eligible to withdraw any funds from that address.
In Hedera, it is possible for an account with associated EVM address alias to have a key that does not correspond to the alias. When an account is created with EVM address alias but has a non-matching account key, this is what we refer to as “EVM address reserving”.

It is possible to “reserve” an EVM address in a couple of ways:
1. Create an account with an alias and then update the account key.
2. Create an account with an alias and a key that’s different from the key that’s used to derive the alias from.

In both cases, this results in the private key losing control over the account with EVM address derived from the corresponding private-public key pair.

This means that there is a difference in how some situations, like accidental transfers to known EVM addresses, will be handled in Ethereum and in Hedera:
- In Ethereum, everyone that knows the corresponding private key will be able to authorize a transaction from the known address, and the first to send a transaction would be eligible to withdraw the funds.
- In Hedera, only the key that controls an account can be used to withdraw the funds. This key may not correspond to the account alias.

For more information about EVM addresses, aliases and how they are used in Hedera see [Auto Account Creation](https://docs.hedera.com/hedera/core-concepts/accounts/auto-account-creation).

### Example user story

As a dApp developer, I have written some contracts and tests for these contracts.

In Ethereum:
- While executing tests against different environments, using some default private test keys (e.g. the ones configured with Hardhat) to create and interact with accounts, I accidentally executed my tests on Mainnet and transferred some funds to an account with public address derived from one of the default test keys.
- As those default private test keys are well-known, anyone can use them to withdraw the funds from the account, so in effect, the funds are lost.

In Hedera (current behavior):
- While executing tests against different environments, using some default private test keys (e.g. the ones configured with Hedera Local Node) to create and interact with accounts, I accidentally executed my tests on Mainnet and transferred some funds to an account with alias derived from one of the Hedera Local Node keys.
- As those default private test keys are well-known, anyone could have used them to create an account with the same alias but with different account admin key, leaving me with no control over the funds.

In Hedera (desired behavior):
- While executing tests against different environments, using some default private test keys (e.g. the ones configured with Hedera Local Node) to create and interact with accounts, I accidentally executed my tests on Mainnet and transferred some funds to an account with alias derived from one of the Hedera Local Node keys.
- As those default private test keys are well-known, the ledger can be configured to own those EVM addresses and block any transfers to them, so that I cannot accidentally send funds to them.

## Goals

- We want to block a list of known addresses, so that no one can “reserve” them and exercise full control on any accidentally sent and blocked funds. Sending funds to such blocked addresses should not be successful as these accounts are considered “compromised”.

## Non Goals

- We don't consider unblocking already blocked accounts.
- We don't consider what happens to a blocked account when it expires.

## Architecture

The implementation related to blocked accounts will be gated by a feature flag.

### File containing the list of blocked accounts

We will store the list of blocked accounts in a regular CSV resource file.
Each row in the file will have a hex encoded ECDSA private key that corresponds to the EVM address that we want to block and an optional memo field that will be used to indicate the reason for blocking the account.

### Creating the blocked accounts in state

After the node starts (on handling the first transaction), for each of the EVM addresses that we want to block create a regular account with the following properties:
- `receiverSigRequired` equal to `true`, thus blocking all future transfers to these accounts
- `key` equal to the `GENESIS` key
- `alias` equal to the EVM address derived from the corresponding private key
- `memo` if specified for the blocked EVM address in the resource file, indicating the reason for blocking the account

Anyone with access to the `GENESIS` key will have sole control on the accounts and the fact that a given alias can be associated only to a single account prevents reserving of the EVM addresses.
The EVM already has checks integrated for accounts configured with `receiverSigRequired`.
This approach does not require us to introduce a property file with random addresses and to have custom checks and logic executed in the EVM.

### Externalizing the blocked accounts

All created blocked accounts will be externalized as synthetic account creations.

### New Classes

`BlocklistAccountCreator` class will encapsulate the logic for reading blocked accounts from file and creating them in state.

## Acceptance Tests

* Verify that blocked accounts are created in state and that synthetic records are externalized for them when the node starts after the first transaction is handled.
* Verify that the externalization of synthetic records is done only _once_ per blocked account.
* Verify that funds cannot be transferred to blocked accounts unless the transaction is initiated by `GENESIS` account.

## Alternative Approaches Considered

* Having the list of blocked accounts in a proper Hedera file so that updates to the file automatically take effect without needing to restart or upgrade the network.
  * This is not applicable to our case because we create the accounts on upgrade and perform a migration for the mirror nodes.
  * An additional thing to consider is that removing a private key from the file will not uncreate the account, so we can add more private keys to the list, but not remove already created ones - or more precisely, we can remove already created ones, but that will only remove them from the file, not from the network.
