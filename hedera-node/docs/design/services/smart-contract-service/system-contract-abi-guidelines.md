# System Contract API Design Guidelines

(**A start at answering [#14244](https://github.com/hashgraph/hedera-services/issues/14244)**)

This document contains design guidelines for system contract ABIs.

## Motivation

- We’ve learned over time that the APIs we build for system contracts have an impact on
    - usability
    - maintainability
    - security.
- API design shouldn’t be *ad hoc* - there should be consistency that supports the goals above
  (and avoids mistakes)
- System contracts are *contract* interfaces that  expose HAPI logic in the equivalent manner of an EVM precompile. System contracts preserve the behaviour of an EVM smart contract but capitalize of the power of native services. System contracts are intended to be used by smart contract *developers* in accordance with solidity development best practices: Therefore 
  the APIs should be designed with those developer’s common expectations and practices in mind.

## Related

- The question of how to _version_ system contracts.
  - Which is being worked on in a different issue, and when there's a PR for it it will be referenced here

## Guidelines

*(An unorganized list at the moment, and needing comments, additions, and review!)*

- Do not use *unsigned integral* types in arguments or return values.
    - Unsigned integral types are not supported in Java and the interface (Besu) EVM↔Java of these types
      has been buggy in the past (including leading to security problems)
- All non-view methods and *most* view methods should return a `ResponseCode` as their first output 
  value (and that should be an `int64`).  This ensures any network level response code types can be exposed to developers for greater insights during troubleshooting. View methods that don’t return a response code return no 
  output (0 bytes returned) on failure.
    - The view methods that don’t return a response code should be only those for which invalid 
      input arguments or invalid processing are easy - trivial! - for the developer to figure out 
      *without* a response code.  Any failure that could be attributed to input arguments being 
      Hedera entities (accounts, tokens, contracts, etc.) but having incorrect *properties* for the
      method to succeed should return a response code so that the particular semantic problem can be
      highlighted.
      - Avoid inventing new response codes if there's an existing one that will serve. New response
        codes must be mentioned in the related HIP.
- System contract methods should not revert - instead signal failure via the response code (as above)
    - Reverting messes up calling contracts
- Protobuf encoding of arguments must only be used when the only use case is for the input argument 
  to be provided from an external source (e.g., a DApp) and it is to be passed through the called 
  contract into the system contract method without needing interpretation or alteration (same for 
  returns).
    - There is no *native* support of protobufs in Solidity.  There is limited support for decoding
      or encoding protobufs via open-source libraries:
        - Limited: There’s a reasonable open source protobuf codec ([`protobuf3-solidity-lib`](https://github.com/celestiaorg/protobuf3-solidity-lib)) 
          and `protoc` plugin ([`protobuf3-solidity`](https://github.com/celestiaorg/protobuf3-solidity)), 
          both from [Celestia](https://celestia.org); and then Solidity itself limits how much of the 
          protobuf spec that plugin supports. If you *intend* to use this plugin you must design
          your protobufs accordingly, and our existing HAPI protobufs already use features that are 
          unavailable for this codec.
    - Example of it used this way: HIP-632 (Hedera Account Service) `isAuthorized` method where the
      smart contract caller packages a set of public key/signature pairs into a protobuf and passes
      that into its smart contract which just passes it, uninspected and untouched, into the system
      via `isAuthorized`.

## **TODO:** To Be Incorporated Into These Guidelines In Some Way

- Difference in method signature for same method functionality between system contract and proxy 
  contract
- A section for what should be included in the HIP defining the system contract and its methods
    - E.g., selector, unexpanded argument list (i.e.., with Solidity struct names), expanded 
      argument list (i.e., the signature you use to compute the selector with), new response codes
      needed (which need to be added to the protobufs repo), …
- How the *return values* aren’t part of the method selector and how that affects API design (it 
  mainly affects versioning/extending but maybe it has impacts here)
- How to define structs given that a) structs, expanded fully, are part of the method signature; 
  and b) Solidity doesn’t have inheritance of structs
- Relationship between the `IFoo` interfaces and the `Foo` wrappers in the smart contract repo
