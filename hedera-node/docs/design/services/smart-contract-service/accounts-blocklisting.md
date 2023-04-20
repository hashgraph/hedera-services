# Accounts Blocklisting

## Purpose

In Ethereum, everyone that knows a private key is able to authorize a transaction from the corresponding EVM public address, and the first to send a transaction would be eligible to withdraw any funds from that address.
In Hedera, it is possible for an account with associated EVM address alias to have a key that does not correspond to the alias. This is what we refer to as “EVM address squatting” i.e. using a key that’s different from the key that’s used to derive the EVM address alias from.

It is possible to “squat” on an EVM address in a couple of ways:
1. Create an account with an alias and then update the account key.
2. Create an account with an alias and a key that’s different from the key that’s used to derive the alias from.

In both cases, this results in the private key losing control over the EVM address derived from the corresponding public key.

This means that there is a difference in how some situations, like accidental transfers to known EVM addresses, will be handled in Ethereum and in Hedera:
- In Ethereum, everyone that knows the corresponding private key will be able to authorize a transaction from the known address, and the first to send a transaction would be eligible to withdraw the funds.
- In Hedera, only the account with the associated corresponding alias would be eligible to withdraw the funds. If another key controls this account, then only that key can be used to withdraw the funds.

For more information about EVM addresses, aliases and how they are used in Hedera see [Auto Account Creation](https://docs.hedera.com/hedera/core-concepts/accounts/auto-account-creation).

## Goals

- We want to block a list of known addresses, so that no one can “squat” on them and exercise full control on any accidentally sent and blocked funds. Sending funds to such blocked addresses should not be successful as these accounts are considered “compromised”.

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

Anyone with access to the `GENESIS` key will have sole control on the accounts and the fact that a given alias can be associated only to a single account prevents squatting on the EVM addresses.
The EVM already has checks integrated for accounts configured with `receiverSigRequired`.
This approach does not require us to introduce a property file with random addresses and to have custom checks and logic executed in the EVM.

### Externalizing the blocked accounts
All created blocked accounts will be externalized as synthetic account creations.

### New Classes
`BlocklistAccountCreator` class will encapsulate the logic for reading blocked accounts from file and creating them in state.
