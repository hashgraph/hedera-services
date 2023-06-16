# Hollow Account Finalization

**NOTE** The subsequent text assumes you are already familiar with the hollow account concept from [HIP-583](https://hips.hedera.com/hip/hip-583). 
If that's not the case, please refer to [HIP-583](https://hips.hedera.com/hip/hip-583) and [alias-configuration.md](./alias-configuration.md) before continuing. Otherwise, some aspects may sound confusing.

## Purpose

The [alias-configuration.md](./alias-configuration.md) already established the _Lazy Account Creation Flow_ - 
creation of a "hollow" account with no key and only an evm address, derived from an `ECDSA` key.

Support for the finalization of such hollow accounts must be added, given that a subsequent transaction supplies
a corresponding signature from the `ECDSA` key, from which the evm address of the hollow account was derived.

This document details how to alter the existing code in order to achieve this functionality.


## Goals
Expand the ingestion, pre-handle, and handle workflows in such a way that:
- the network allows ingestion of transactions with a hollow account payer, provided that a corresponding ECDSA signature is present in the sig map.
- modify the key and signature verification logic to recognize and verify hollow accounts.
- hollow accounts are completed when a corresponding `ECDSA` signature is present and child records are exported

## Non-Goals
Support for finalization of a hollow account by **any** signature present in the sig map, not only the required ones for the transaction.

## Non-Functional Requirements
**Perform the least amount of work in handle()** as possible.
Ideally, we would want all the iteration through the transaction sigs, verification, and collection of targeted hollow completions to be done in the multi-threaded context of `preHandle()`.
In `handle()`, we would want to only receive a list of mappings from an account id to key, from which we will directly update the state of the accounts and export the child records.

## Architecture

### New types

---
**Define a `PendingCompletion` record, that encapsulates the info needed to finalize a hollow account (*id* and new *key*):**

```public record PendingCompletion(EntityNum hollowAccountNum, JECDSASecp256k1Key key,  {}```

---
**Expand `SwirldsTxnAccessor` API:**
```java 
/* --- Used to track the hollow account completions linked to a transaction --- */
void setPendingCompletions(List<PendingCompletion> pendingCompletions);

List<PendingCompletion> getPendingCompletions();
```
---
**Define a new `HollowAccountFinalizationLogic` type, which:**
- obtains the list of hollow completions via  `SwirldsTxnAccessor#getPendingCompletions`
    - if the current transaction is an `EthereumTransaction`, also checks if the wrapped sender is a hollow account, and possibly adds it to the list of completions
- for each `PendingCompletion`:
    - updates the state
    - exports child record
    - notifies `SigImpactHistorian`
- must be executed during `handle()`, after the key activation validation has passed successfully
---
**Expand `PubKeyToSigBytes` interface:**
```java
    /**
     * Checks for the presence of an ECDSA signature in all of the public-key-to-signature mappings.
     *
     * @return true if there is at least one ECDSA signature; false if there is none
     */
     default boolean hasAtLeastOneEcdsaSig() {
        return false;
     } 
```
- hollow accounts can be completed only via an `ECDSA` sig
- we have to iterate through all signatures in the sig map in order to find any potential hollow finalizations, 
- **we want to start iterating through all of the signatures for possible hollow completions only if there is an `ECDSA` signature present**; otherwise, we will be doing unnecessary work.

---
**Define a new `JWildcardEcdsaKey` key:**
- **what** — wraps an `evmAddress`, derived from an `ECDSA` key. Represents an unknown/wildcard `ECDSA` key, whose actual key bytes are unknown,
but the _evm address_ that is derived from the key is known.
- **why** — we require a signature from a specific `ECDSA` key in order to finalize a hollow account, but the hollow account contains only the `evmAddress` derived from that key, and not the key itself. We need a way to indicate to the key/sig verification logic to look for a matching `ECDSA` key in the sig map.
- ⚠️ this key will only be used as a placeholder in the req keys list; cannot be verified for activation from key activation infrastructure; should be replaced with its corresponding `JECDSASecp2561k1Key`, if such is found in the sig map, **before key activation checks**
- ⚠️ this key will only be used internally in the node; it does not map to a `Key` protobuf 

---
### Ingestion/Prechecks Changes
**Change the payer signature checks in the following way:**

If `txnPayer` is a hollow account:

1. Even though hollow accounts have no key set in state, instead of returning an empty key for the required payer key, return a `JWildcardEcdsaKey`, constructed from the `evmAddress` the hollow account was created with
2. Go through the sig map, and try to find a corresponding `ECDSA` key to the `JWildcardEcdsaKey#evmAddress`
  1. if there is a match, replace the `JWildcardEcdsaKey` with a `JECDSASecp2561k1Key`, before continuing with key validations.
  2. if there is no match, pass the `JWildcardEcdsaKey` to the key validation. When the key validation logic sees a `JWildcardEcdsaKey`, it will immediately fail and return `INVALID_SIGNATURE`.

### Pre-handle Changes
After we have collected all required keys and expanded the sigs:

- make sure we have `JWildcardECDSAKey` keys in the req keys and there is at least 1 `ECDSA` sig in the sig map
- construct an index of evmAddress ↔ ECDSA keys from the expanded sigs
- go through each req key of type `JWildcardECDSAKey`, check if there is a match in the index, and replace it, if possible, in the req keys list and construct a `PendingCompletion`
- add any created `PendingCompletion`s to the transaction accessor
- **pseudocode:**

  ```
  If any of the required keys == JWildcardEcdsaKey && pkToSigFn#hasAtLeastOneEcdsaSig:
          Map<Bytes, JKey> evmAddressToKeysIndex
          for ecdsaSig in sigs:
              key = extractKeyFrom(ecdsaSig)
              address = extracctAddressFrom(key)
              evmAddressToKeys.add(address, key)
          
          for key in reqKeys:
               if key is JWildcardEcdsaKey && evmAddressToKeysIndex.contains(JWildcardEcdsaKey#evmAddress):
                  key = get key from evmAddressToKeysIndex
                  add a new PendingCompletion (AliasManager.lookupIdFor(JWildcardEcdsaKey#evmAddress), key) to pending completion list
                  replace JWildcardEcdsaKey with corresponding JECDSASecp2561k1Key in the list of req keys
          
         if pending completions are not empty:
             add the list to the txn accessor
  ```

  - We have to go through all the sigs, since a hollow account’s `evmAddress` may be activated via a signature by an already set `JEcdsaSecp2561k1Key` to another account, which is a req signer for the txn. 
  - There may be a case, where a `JWildcardEcdsaKey` with the same `evmAddress` are present both in `payerKey` and `otherKeys`. Example — `CryptoCreate` , where `payer` is hollow account & also `alias` is set as `evmAddress` from the same `ECDSA` key that the hollow account was created by.


### Handle Changes
1. If any of the required accounts' sig info has changed since pre-handle, perform the same algorithm as in pre-handle section.
2. After successful required key activation checks, execute `HollowAccountFinalizationLogic`

## Acceptance Tests

* Verify that a transaction submitted by a hollow payer without a corresponding `ECDSA` signature in the sig map gets **rejected**
* Verify that a transaction submitted by a hollow payer with a corresponding `ECDSA` signature in the sig map gets **submitted**
* Verify that a submitted transaction with hollow payer **completes** the payer account and exports child records
* Verify that a submitted transaction with a non-hollow payer, **another required** hollow signature, and a corresponding `ECDSA` signature for that hollow account, **completes** the account and exports the record (e.g. `CryptoTransfer` sending funds from a hollow account)
* Verify that a submitted transaction with a non-hollow payer, **another required** hollow signature, but without a corresponding `ECDSA` signature for that hollow account, fails with `INVALID_SIGNATURE`
* Verify that a submitted transaction with a hollow payer and **another required** hollow signature, and corresponding `ECDSA` signatures for both accounts, completes both accounts and exports child records
* Verify that a valid `EthereumTransaction`, whose wrapped sender is a hollow account, completes the wrapped sender's account 
* Verify that a submitted transaction with more hollow account completions than current max preceding child record limit fails
* Verify that an `ECDSA` signature in the sig map that does not relate to any required signature for the transaction does not complete a linked hollow account