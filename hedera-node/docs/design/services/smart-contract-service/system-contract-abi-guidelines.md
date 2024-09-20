# System Contract API Design Guidelines

(**A start at answering [#14244](https://github.com/hashgraph/hedera-services/issues/14244)**)

This document contains design guidelines for system contract ABIs.

## Motivation

- We’ve learned over time that the APIs we build for system contracts have an impact on
    - usability,
    - maintainability,
    - security.
- API design shouldn’t be *ad hoc* - there should be consistency that supports the goals above
  (and avoids mistakes).
- System contracts are *contract* interfaces that  expose HAPI logic in the equivalent manner of an 
  EVM precompile. System contracts preserve the behaviour of an EVM smart contract but capitalize of
  the power of native services. System contracts are intended to be used by smart contract 
  *developers* in accordance with solidity development best practices: Therefore the APIs should be 
  designed with those developer’s common expectations and practices in mind.

## Related

- The question of how to _version_ system contracts.
  - Which is being worked on in a different issue, and when there's a PR for it will be referenced 
    here.

## Guidelines

*(An unorganized list at the moment, and needing comments, additions, and review!)*

- Do not use *unsigned integral* types in arguments or return values.
    - Unsigned integral types are not supported in Java and the interface (Besu) EVM↔Java of these 
      types has been buggy in the past (including leading to security problems)
- All non-view methods and *most* view methods should return a `ResponseCode` as their first output 
  value (and that should be an `int64`).  This ensures any network level response code types can be 
  exposed to developers for greater insights during troubleshooting. View methods that don’t return 
  a response code return no output (0 bytes returned) on failure.
    - The view methods that don’t return a response code should be only those for which invalid 
      input arguments or invalid processing are easy - trivial! - for the developer to figure out 
      *without* a response code.  Any failure that could be attributed to input arguments being 
      Hedera entities (accounts, tokens, contracts, etc.) but having incorrect *properties* for the
      method to succeed should return a response code so that the particular semantic problem can be
      highlighted.
      - Avoid inventing new response codes if there's an existing one that will serve. New response
        codes must be mentioned in the related HIP.
- System contract methods should not revert - instead signal failure via the response code (as above)
    - Reverting messes up calling contracts.
- Protobuf encoding of arguments must only be used when the only use case is for the input argument 
  to be provided from an external source (e.g., a DApp) and it is to be passed through the called 
  contract into the system contract method without needing interpretation or alteration (same for 
  returns).  Our APIs must not use a protobuf-encoded argument if there's a reasonable use case that
  calls for a (user's) contract to serialize into a protobuf and then call a system contract method.
    - There is no *native* support of protobufs in Solidity.  There is limited support for decoding
      or encoding protobufs via open-source libraries:
        - Limited: There’s a reasonable open source protobuf codec ([`protobuf3-solidity-lib`](https://github.com/celestiaorg/protobuf3-solidity-lib)) 
          and `protoc` plugin ([`protobuf3-solidity`](https://github.com/celestiaorg/protobuf3-solidity)), 
          both from [Celestia](https://celestia.org); and then Solidity itself limits how much of the 
          protobuf spec that codec/plugin supports.
            - If you *intend* to use this codec/plugin you must design your protobufs accordingly.
              (As an example, our existing HAPI protobufs already use features that are unavailable 
              for this codec.)
              - Significant documented limitations (at this time) are: no repeated `string` or `bytes`,
                no `oneof`, no `map`s, and no nested `enum` or `message`. 
    - Example of it used this way: HIP-632 (Hedera Account Service) `isAuthorized` method where the
      smart contract caller packages a set of public key/signature pairs into a protobuf and passes
      that into its smart contract which just passes it, uninspected and untouched, into the system
      via `isAuthorized`.
- When a method is to be available both for direct call by a contract and also available via
  indirect call through a proxy (e.g., calling a Hedera Token type which thunks through a proxy)
  then:
  - The argument list types and order should match, the only difference being that the direct
    call must pass the entity address as the first argument.  (It's supplied by the system 
    implicitly in the proxy call case).
  - In the case where the _proxy_ call is for an Ethereum ERC/EIP defined method (e.g., ERC-20
    `approve`) then the proxy's method return type should match the Ethereum-defined method's
    return type but the _direct_ call method return type should return a `ResponseCode` prepended
    (if necessary) to the Ethereum-defined method's return type.
    
    E.g., from [HIP-514](https://hips.hedera.com/hip/hip-514) and [HIP-376](https://hips.hedera.com/hip/hip-376:
    - Proxy signature:  `function approve(address spender, uint256 amount) external returns (bool)`
    - Direct signature: `function approve(address token, address spender, uint256 amount) external returns (int64 responseCode)`
      
    Here the ERC-20 defined method simply returns a `bool`.  The direct call version returns
    instead a `ResponseCode` and doesn't return a `bool` also, as the `ResponseCode` suffices
    for success or failure.
        
## Notes

### For the defining HIP

The defining HIP should include a table listing:
* method signature + returns
  * _without_ argument names
* method selector
  * And after the HIP is approved: Add the selector to `4byte.directory`.
* the method signature + returns with Solidity struct names if used for arguments
  * The struct should _also_ be defined in the HIP
* (optionally) the "expanded" method signature with structs expanded out to their fields, recursively,
  as used to calculate the method selector
* attributes (e.g., pure, view, etc)
* checkmark on whether or not it is a proxy method
* HAPI transaction(s) it exposes (if any)
* reference to ERC/EIP (if any)

And then each method should have a subsection defining it that starts with the method signature +
returns, unexpanded, this time _with_ argument names.  This section should include more information
on what the arguments actually represent (especially in the case of `string` or `bytes` arguments).

There should also be a section listing new _response codes_ that the newly defined methods may
return.
* These are defined in a HIP becuase they must be added to the HAPI protobufs and thus they affect 
  the permanent system state and record/block streams.
  
### For the smart contract repo

In the smart contract repo each system contract is defined by an interface, and a helper/wrapper.
For a system contract HFoo (e.g., HTS, HAS) there is a:
- `IHFoo.sol` - an interface which defines the actual system contract ABI
- `HFoo.sol` - a class that is a helper or wrapper over `IFoo.sol`, for the convenience of smart
  contract developers.  (This helper/wrapper is pretty thin; you can look at the existing system
  contracts to see how it's done.)

(Further structure of the smart contract repo is TBD, awaiting agreement on the way we version
system contracts.)

## **TODO:** To Be Incorporated Into These Guidelines In Some Way

- How the *return values* aren’t part of the method selector and how that affects API design (it 
  mainly affects versioning/extending, but maybe it has impacts here).  Will be added once the
  versioning scheme is settled on.
- How to define structs given that a) structs, expanded fully, are part of the method signature; 
  and b) Solidity doesn’t have inheritance of structs
