# System accounts and files

The Hedera network reserves the first 
[`ledger.numReservedSystemEntities=1000`](../hedera-node/src/main/resources/bootstrap.properties#L38) 
entity numbers for its own uses. 
An account with a number in the reserved range is called a **system account**. 
A file with a number in the reserved range is called a **system file**. 

## System account roles

Certain system accounts have predefined roles in the network. 

For the purposes of this document, we care about the the following:
 - The **treasury**, which upon network creation receives all minted ℏ except those
 explicitly designated for a network node account. 
 - The **address book admin**, used to manage metadata on network nodes 
 such as their id numbers, IP addresses, TLS certificate information, and signing keys.
 - The **fee schedules admin**, used to set the prices of resources consumed 
 by HAPI operations.
 - The **exchange rates admin**, used to set the network's active conversion
 ratio between USD and ℏ.
 - The **freeze admin**, used to schedule maintenance periods during which the 
 network stops accepting new transactions.
 - The **system delete admin**, used to delete files or contracts which may 
 have been created on the network with illicit storage contents. (Note that crypto 
 accounts, topics, and tokens are untouchable.)
 - The **system undelete admin**, used to reverse provisional actions of the 
 system delete admin account under certain conditions. 
 - The **system admin**, used primarily to manage the keys of the above admin accounts;
 or substitute for them in circumstances where they have been compromised or rendered
 unusable.

The account number that plays each role is set, once, on network startup, by consulting
the [_bootstrap.properties_](../hedera-node/src/main/resources/bootstrap.properties) 
resource. For example, using the mainnet configuration, the treasury account is account
`0.0.2` because we have `accounts.treasury=2` in the _bootstrap.properties_.

# Privileged transactions

When a system account is the designated payer for a transaction, there 
are cases in which the network grants special **privileges** to the transaction. 
(There is one exception in which authorization privileges may be granted based on a 
valid _non-payer_ signature; this occurs for  a `CryptoUpdate` targeting an account 
with number no greater than [`ledger.numReservedSystemAccounts=100`](../hedera-node/src/main/resources/bootstrap.properties#L37). 
But in general, it is crucial to understand the relevant system account must be the _payer_ of 
the transaction for any privileges to be granted; that is, it must be the `AccountID`
designated in the transaction's `TransactionID`.)

There are two kinds of privileges, 
  1. _Authorization_ - some transaction types, such as `Freeze`, require authorization to submit to the network. All such transactions will be rejected with the status `UNAUTHORIZED` unless they are privileged.
  2. _Waived signing requirements_ - all unprivileged `CryptoUpdate` and `FileUpdate` transactions must be signed with the target entity's key, or they will fail with status `INVALID_SIGNATURE`. The network waives this requirement for certain privileged updates.

This document lists all cases of privileged transactions currently recognized by the Hedera network. 

## Authorization privileges

First we consider the four transaction types that always require authorization to execute. These include:
  1. The `Freeze` transaction that schedules a maintenance window in which the network 
  will stop accepting transactions for a specified period.
  2. The `SystemDelete` transaction that deletes a file or contract (even an immutable
  file or contract), without requiring the target entity's key to sign the transaction.
  3. The `SystemUndelete` transaction that reverses the action of a `SystemDelete` 
  transaction, if within the window during which such a reversal is possible.
  4. The `UncheckedSubmit` transaction that submits a transaction to the network 
  without enforcing standard prechecks. (The only real use cases for `UncheckedSubmit`
  are in development environments, where it can be invaluable for testing.)

| Payer | `Freeze` | `SystemDelete` | `SystemUndelete` | `UncheckedSubmit` |
| --- | :---: | :---: | :---: | :---: | 
| [`accounts.treasury=2`](../hedera-node/src/main/resources/bootstrap.properties#L28) | X | X | X | X |
| [`accounts.systemAdmin=50`](../hedera-node/src/main/resources/bootstrap.properties#L23) | X | X | X | X |
| [`accounts.freezeAdmin=58`](../hedera-node/src/main/resources/bootstrap.properties#L22) | X |   |   |   |
| [`accounts.systemDeleteAdmin=59`](../hedera-node/src/main/resources/bootstrap.properties#L24) |   | X |   |   |
| [`accounts.systemUndeleteAdmin=60`](../hedera-node/src/main/resources/bootstrap.properties#L24) |   |   | X |   |

Next we consider `FileUpdate` and `FileAppend` transactions when targeting one of the system files.

| Payer | [`files.addressBook=101`](../hedera-node/src/main/resources/bootstrap.properties#L29)/[`files.nodeDetails=102`](../hedera-node/src/main/resources/bootstrap.properties#L35) | [`files.networkProperties=121`](../hedera-node/src/main/resources/bootstrap.properties#L31)/[`files.hapiPermissions=122`](../hedera-node/src/main/resources/bootstrap.properties#L34)| [`files.feeSchedules=111`](../hedera-node/src/main/resources/bootstrap.properties#L33) | [`files.exchangeRates=112`](../hedera-node/src/main/resources/bootstrap.properties#L32)|
| --- | :---: | :---: | :---: | :---: | 
| [`accounts.treasury=2`](../hedera-node/src/main/resources/bootstrap.properties#L28) | X | X | X | X |
| [`accounts.systemAdmin=50`](../hedera-node/src/main/resources/bootstrap.properties#L23) | X | X | X | X |
| [`accounts.addressBookAdmin=55`](../hedera-node/src/main/resources/bootstrap.properties#L19) | X | X | |   |
| [`accounts.feeSchedulesAdmin=56`](../hedera-node/src/main/resources/bootstrap.properties#L21) |   |   | X |   |
| [`accounts.exchangeRatesAdmin=57`](../hedera-node/src/main/resources/bootstrap.properties#L20) |   | X |   | X |

For the `CryptoUpdate` transaction, we have the table below. (In words, it says the following: the treasury can
update _any_ system account; the system admin can update a smaller subset of system accounts; and all other system 
accounts can update themselves.) 

Here we have the exception mentioned above. That is, no matter the payer, if the treasury or system admin sign a
`CryptoUpdate` targeting a system account with number up to [`ledger.numReservedSystemAccounts=100`](../hedera-node/src/main/resources/bootstrap.properties#L37),
the necessary authorization will be granted.

| Payer | All accounts [`<= ledger.numReservedSystemEntities=1000`](../hedera-node/src/main/resources/bootstrap.properties#L38) | Accounts [`<= ledger.numReservedSystemAccounts=100`](../hedera-node/src/main/resources/bootstrap.properties#L37) | Account `0.0.N` with `N <= ledger.numReservedSystemEntities=1000` | 
| --- | :---: | :---: | :---: | :---: | 
| [`accounts.treasury=2`](../hedera-node/src/main/resources/bootstrap.properties#L28) | X | X | X |
| [`accounts.systemAdmin=50`](../hedera-node/src/main/resources/bootstrap.properties#L23) |   | X |  |
| `0.0.N` |   |  | X |
| `0.0.M` for any `M` |  | X _if treasury or system admin also sign_ |  |

## Waived signing requirements

The next class of privileges apply to certain `CryptoUpdate` and `FileUpdate`/`FileAppend`
operations whose target is a system account or file. These privileges waive the normal 
requirement that the key associated to an entity sign any transaction that updates it.
The network grants these privileges so the admin accounts can never be "locked 
out" of performing their system roles. For example, even if we lose the key to the exchange 
rates file, the exchange rates admin can still issue a `FileUpdate` transaction to change 
this file.

The waived signature privileges for `FileUpdate`, `FileAppend`, and `CryptoUpdate` 
operations are identical to the corresponding authorization privileges in the tables above; 
_except_ that non-payer signatures on a `CryptoUpdate` do not suffice to waive 
signing requirements. (So the last row of the `CryptoUpdate` table doesn't apply.)

# Miscellanea

- The network charges no fees to privileged transactions. 
- With the default settings of `accounts.systemAdmin=50` and `accounts.systemAdmin.firstManaged=51`, 
the system admin account is unique in being unable to update itself.

