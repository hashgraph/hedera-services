# System accounts and files

The Hedera network reserves the first 
[`ledger.numReservedSystemEntities=1000`](../hedera-node/src/main/resources/bootstrap.properties#L37) 
entity numbers for use by the system. 
An account with a number in the reserved range is called a **system account**. 
A file with a number in the reserved range is called a **system file**. 

## System account roles

Certain system accounts have predefined roles in the network. For the purposes of this document,
we primarily care about the the following:
 - The **treasury account**, which upon network creation receives all minted ℏ except those
 explicitly designated for a network node account. 
 - The **address book admin account**, used to manage metadata on network nodes 
 such as their id numbers, IP addresses, TLS certificate information, and signing keys.
 - The **fee schedules admin account**, used to set the prices of resources consumed 
 by HAPI operations.
 - The **exchange rates admin account**, used to set the network's active conversion
 ratio between USD and ℏ.
 - The **freeze admin account**, used to schedule maintenance periods during which the 
 network stops accepting new transactions.
 - The **system delete admin account**, used to delete files or contracts which may 
 have been created on the network with illicit storage contents. (Note that crypto 
 accounts, topics, and tokens are untouchable.)
 - The **system undelete admin account**, used to reverse provisional actions of the 
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
(It is crucial to understand the relevant system account must be the _payer_ of 
the transaction for privileges to be granted; that is, it must be the `AccountID`
designated in the transaction's `TransactionID`.)

There are two kinds of privileges, 
  1. _Authorization_ - some transaction types, such as `Freeze`, require authorization to submit to the network. All such transactions will be rejected with the status `UNAUTHORIZED` unless they are privileged.
  2. _Waived signing requirements_ - all unprivileged `CryptoUpdate` and `FileUpdate` transactions must be signed with the target entity's key, or they will fail with status `INVALID_SIGNATURE`. The network waives this requirement for certain privileged updates.

This document lists all cases of privileged transactions 
currently recognized by the Hedera network. 

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
| [`accounts.feeSchedulesAdmin=56`](../hedera-node/src/main/resources/bootstrap.properties#L21) |   |   | X |   |
| [`accounts.exchangeRatesAdmin=57`](../hedera-node/src/main/resources/bootstrap.properties#L20) |   | X |   | X |

## Waived signature privileges


