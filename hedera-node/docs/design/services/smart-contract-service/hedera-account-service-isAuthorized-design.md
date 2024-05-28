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
  
## Phased implementation

The first release of HIP-632 will include only `isAuthorizedRaw`.

## Where does this go?

A "hedera account service" system contract is being set up in
`com.hedera.node.app.service.contract.impl.exec.systemcontracts.has`, and also at
`...contract.impl.exec.processors.HasTranslatorsModule` (for the individual methods).

Following the existing pattern for HAS each method will be given a package under 
`...contract.impl.exec.systemcontracts.has` with two classes:
* `[method]Translator` - which first confirms that the call is for the given method, and
  then builds a `TransactionBody` for it.
* `[method]Call` - which takes that `TransactionBody` and then has an `execute()` method
  that does the work.
  
## Design for `isAuthorizedRaw`

[TBD shortly as I fix up some problems with understanding HAS]

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
the account. This is an operation provided by `HandleContext` (which can be accessed via
the `HederaWorldUpdater.Enhancement`).
  * Some fee will be charged to account for the signature verification cost, but it
will have to be hard-coded (at this time) because we have no access to that fine-grained
fee now.

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
  * TBD #1: A fee schedule
mechanism to charge fees for individual system contract methods 
  * TBD #2: How do we choose the fee for the Hedera signature verification part of
`isAuthorized` and `isAuthorizedRaw`?



