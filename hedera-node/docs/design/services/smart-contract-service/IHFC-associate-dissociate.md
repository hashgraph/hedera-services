# IHRC Facade Associate and Dissociate Functionality

## Purpose

[HIP-218](https://hips.hedera.com/hip/hip-218) proposed a way to enable treating HTS tokens in an equivalent manner
to ERC tokens in the EVM world by creating a proxy redirect facade contract.  We need to extend 
this functionality to include `associate` and `dissociate` functions which are part of functionality available to HTS tokens
but not ERC tokens.

## Goals

- Extend the proxy redirect token facade contract to enable calling `associate` and `dissociate` functions on tokens.

## Non Goals

- Refactor the current implementation to better support future additions to the token facade contract functionality.

## Architecture

The method in which the redirection to proxy contract is implemented is that during the execution of an EVM request, we examine the contract address to see
if it is an HTS token address and if so we redirect the request to the proxy contract.  

More specifically the algorithm by which we get from (a) the call to the token address to (b) the precompiled contract is a follows:
1. The EVM encounters a call such as this `tokenAddress.<functionName>(<params>);`
2. The EVM calls [HederaStackedWorldStateUpdater.get()](https://github.com/hashgraph/hedera-services/blob/29e49604eff059c6bc0c0a4dd2a738f194b32c04/hedera-node/hedera-mono-service/src/main/java/com/hedera/node/app/service/mono/store/contracts/HederaStackedWorldStateUpdater.java#L202) and loads the code stored on `tokenAddress`
3. In [HederaStackedWorldStateUpdater.get()](https://github.com/hashgraph/hedera-services/blob/29e49604eff059c6bc0c0a4dd2a738f194b32c04/hedera-node/hedera-mono-service/src/main/java/com/hedera/node/app/service/mono/store/contracts/HederaStackedWorldStateUpdater.java#L202), we intercept the loading of the account code, check if this is actually a contract address, and, if yes, we return an instance of `HederaEvmWorldStateTokenAccount` which wraps the _redirect token bytecode_
4. The redirect bytecode is obtained by compiling the following contract, which accepts all the inputs the user provided (including function selector and function arguments), precedes the argument list with the token address, and _delegatecalls_ the HTS precompile:
```
// SPDX-License-Identifier: Apache-2.0

pragma solidity 0.5.5;
contract Assembly {
	fallback() external {
		address precompileAddress = address(0x167);
		assembly {
			mstore(0, 0xFEFEFEFEFEFEFEFEFEFEFEFEFEFEFEFEFEFEFEFEFEFEFEFE)
			calldatacopy(32, 0, calldatasize())
			let result := delegatecall(gas(), precompileAddress, 8, add(24, calldatasize()), 0, 0)
			let size := returndatasize()
			returndatacopy(0, 0, size)
			switch result
				case 0 { revert(0, size) }
				default { return(0, size) }
		}
	}
}
```
5. This means that _any_ function can be redirected-to as long as the HTS precompile handles the redirect call [here](https://github.com/hashgraph/hedera-services/blob/29e49604eff059c6bc0c0a4dd2a738f194b32c04/hedera-node/hedera-mono-service/src/main/java/com/hedera/node/app/service/mono/store/contracts/precompile/HTSPrecompiledContract.java#L557). 

In particular, the mechanism described above will be extended to include calls to `associate` and `dissociate` functions in the HTSPrecompileContact class method which handles ABI_ID_REDIRECT_FOR_TOKEN function selector.

The following table describes the function selector for the new `associate` and `dissociate` functions and the associated function signatures.

| Function Selector | Function Signature |
|-------------------|--------------------|
| 0x0a754de6        | associate()        |
| 0x5c9217e0        | dissociate()       |

No arguments are necessary because 
- The token address was already determined by looking up the contract address to determine if it is an HTS token address.
- The address to associate/dissociate will be the caller (i.e. msg.sender)

Once the above functionality has been implemented in services, the end user will be able to call the `associate` and `dissociate` functions as follows:

```
IHRC(tokenAddress).associate()
IHRC(tokenAddress).dissociate()
```

The solidity interface for IHRC will be the following

```
interface IHRC {
    function associate() external returns (responseCode);
    function dissociate() external returns (responseCode);
}
```

## Implementation

Override the existing `AbstractAssociatePrecompile` and `AbstractDissociatePrecompile` classes to handle this new use case
of being called via the proxy contract.  The new overwritten classes will differ from the existing classes in that they will
be passed the token id and the sender address as constructor arguments and construct the associate and dissociate operator 
classes from these arguments.  It will also need to send a value into the constructor of the parent class to signal that
signature checking will not be required. 

The `getGasRequirement` method can be moved to the parent class as the functionality will be
common to both the existing and new classes. 

The class `RedirectViewExecutor` will also be updated to include the cost to perform the `associate` and `dissociate` functions.

## Open Questions

Does a feature gate need to be added to control the accessibility to this functionality?

## Acceptance Tests

### Positive Tests
- Create a contract that performs the `associate` and `dissociate` functions on a fungible token and ensure that the functions can be called successfully by an EOA.
- Create a contract that performs the `associate` and `dissociate` functions on an NFT and ensure that the functions can be called successfully by an EOA.
- Create a contract that performs the `associate` and `dissociate` functions on a fungible token and ensure that the functions can be called successfully by a contract.
- Create a contract that performs the `associate` and `dissociate` functions on an NFT and ensure that the functions can be called successfully by a contract.
- Ensure that the `associate` and `dissociate` functions disregards the signature when called via the token facade.

### Negative Tests
- Ensure that the `associate` fails if one tries to associate beyond `tokens.maxPerAccount` on a single account
- Ensure that the `associate` fails if one tries to associate a token to the same account twice.
- Ensure that the `dissociate` fails if one tries to dissociate a token from an account that is not associated with the token.
- Ensure that the `dissociate` fails if balance on the account is not zero.