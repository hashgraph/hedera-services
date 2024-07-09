# HIP-632 (Hedera Account Service) `isAuthorized{Raw}` design

This note addresses the implementation of `isAuthorized` and `isAuthorizedRaw` of 
[HIP-632](https://hips.hedera.com/hip/hip-632) - "Hedera Account Service (HAS) 
System Contract".

## Preliminary notice w.r.t. "virtual addresses"

HIP-632 as written refers a lot to 
[HIP-631](https://hips.hedera.com/hip/hip-631) - "Account Virtual Addresses".
However, that HIP, though `Accepted` will not be implemented.  (Or at least, not
at any time soon.)  So HIP-632 needs to be read with that in mind.  Specifically, 
anywhere that there's a provision for dealing with "virtual addresses", of which there
may be several/many per Hedera account, there is in fact only _one_: the _alias_.

* This mainly affects the method `getVirtualAddresses` which will return an array, but
it will have _at most one_ EVM address in it.
* An issue is present to edit HIP-632 to clarify that it is dealing with aliases, not virtual addresses.  
  
## Phased implementation

The first release of HIP-632 will include only `isAuthorizedRaw`.

## Where does this go?

A "hedera account service" system contract is in
`com.hedera.node.app.service.contract.impl.exec.systemcontracts.has`, and also at
`...contract.impl.exec.processors.HasTranslatorsModule` (for the individual methods).

Following the existing pattern for HAS each method will be given a package under 
`...contract.impl.exec.systemcontracts.has` with two classes:
* `[method]Translator` - which first confirms that the call is for the given method, and
  then builds an `IsAuthorizedRawCall` for it.
* `[method]Call` - which has an `execute()` method
  that does the work.
  
## Design for `isAuthorizedRaw`

`isAuthorizedRaw` has two flows: Either the argument address is a Hedera account, or it is
an EVM address.

* If an EVM address then this is simply the `ECRECOVER` precompile.  We have direct
access to the `ECRECOVER` precompile from BESU in the class
`org.hyperledger.besu.evm.precompile.ECRECPrecompiledContract` which will compute
the `ECRECOVER` operation given the message hash + signature (encoded as a calldata for
the precompile) (and a `MessageFrame` which it ignores).  This returns either the
recovered address, if successful, or "empty", if not.  The recovered address must then
match the address given as an argument to `isAuthorizedRaw`.
  * 3000gas will be charged to match `ECRECOVER` behavior
  
* If an Hedera account then it gets the account, looks in it to find a key _which it must
have_ and which must be a _single key_ (otherwise: failure).  Then given the key and the
signature verifies that the signature matches the message hash and is attested by 
the account. Looking up the accounts is an operation provided by `HandleContext` (which can be accessed via
the `HederaWorldUpdater.Enhancement`).  Validating the signature is done by directly using methods
  from the platform.
  * Some fee will be charged to account for the signature verification cost, but it
will have to be hard-coded (at this time) because we have no access to that fine-grained
fee now.
    * At this time a hardcoded 1.5M gas will be charged for an ED signature verification
        * This was determined by a _very rough_ measurement of the amount of resources (CPU time) 
          needed by a single signature verification.

A feature flag will control enabling `isAuthorizedRaw`: `contracts.systemContract.accountService.isAuthorizedRawEnabled`
* This flag will be `true` (i.e., _enabled_) for release 0.52.

## Future considerations

`isAuthorizedRaw` uses the Platform's `Cryptography` crypto-engine, available through a
deprecated-for-removal API (via `CryptographyHolder`).  Additionally, `isAuthorized` (unlike
`isAuthorizedRaw`) needs to grok non-simple keys (i.e., threshold keys, key lists).  This
functionality is present in Service's app-spi module but not exported to other modules (directly
or indirectly from the `HandleContext`).  It needs to be exposed in that way.
* Currently what is exported w.r.t. signatures is only that the signatures on the top-level 
transaction have been verified.  Not the ability to verify an arbitrary signature given hash and
  signature and an account (with keys).

## Testing

E2E testing necessary:

### Positive

(All with sufficient gas:)

All positive tests return a status SUCCESS, and then verification of the signature is in the
boolean output argument.

1. EVM address, valid hash+signature -> SUCCESS + true
1. EVM address, _invalid_ hash+signature -> SUCCESS + false
1. EVM address, some hash (correct size) but signature's `v` value is `>1` and `<27` -> SUCCESS + false
1. EVM address, valid hash+signature but recovered address does not match given address -> SUCCESS + false
   * But I don't know of any such example, so **low priority** 
1. EVM alias of Hedera account w/ single ED key, valid hash+signature -> SUCCESS + true
1. EVM alias Hedera account w/ single ED key, _invalid_ hash+signature -> SUCCESS + false
1. EC and ED validation _with sufficient gas_ succeeds

### Negative

All negative tests return some failure status.

1. EVM address, insufficient gas -> FAILED
1. EVM alias of Hedera account, insufficient gas -> FAILED
1. EVM address, signature length `!= 65` bytes -> FAILED
1. EVM alias of Hedera account, signature length `!= 64` bytes -> FAILED 
1. EVM address, message hash length `!= 32` bytes -> FAILED
1. EVM address but is a long zero -> FAILED
1. EVM alias of Hedera account w/ 1 key which is EC -> FAILED
1. EVM alias of Hedera account w/ (any) threshold key -> FAILED
1. EVM alias of Hedera account w/ (any) "key list" type key -> FAILED
1. EC and ED validation _without sufficient gas_ -> FAILED

__

## TBD


* `isAuthorizedRaw` requires a message hash.  For EVM addresses, with an EC key, 
the message hash is a
KECCAK256 which is provided directly by the EVM and is relatively inexpensive.  But for
Hedera accounts, with an ED key, the message hash is a SHA384 which can, at this time,
only be computed by an expensive EVM script - on the order of ~1.5-2M gas.  We should
provide a way for the contract to compute this hash at the same level of expense as the
KECCAK256 version.
  
* `isAuthorized{Raw}` does a relatively expensive (resource-wise) computation that should
be charged for.  Currently we have no fee schedule for charging fees (gas or otherwise) for
system contract calls, not to mention varying the fee for individual system contract
methods.  (All existing system contract methods are either "cheap" - as in too small to
charge for - or involve child transactions that themselves do incur fees.)  We _can_
charge hard-coded fees.
    
* Need to have a way to get to signature verification via the `HandleContext` (instead of using
  platform classes directly).

* Don't have sufficiently specific status codes (`HederaResponseEnum`) - and the way to get them is
to put them in a HIP. So this is pending edits on HIP-632, tracked elsewhere.



