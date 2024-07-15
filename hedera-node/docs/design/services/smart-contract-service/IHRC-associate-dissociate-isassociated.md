# IHRC Facade Associate, Dissociate and isAssociated Functionality

## Purpose

[HIP-218](https://hips.hedera.com/hip/hip-218) proposed a way to enable treating HTS tokens in an equivalent manner
to ERC tokens in the EVM world by creating a proxy redirect facade contract.  We need to extend 
this functionality to include `associate`, `dissociate` and `isAssociated` functions which are part of functionality available to HTS tokens
but not ERC tokens.

## Goals

- Extend the proxy redirect token facade contract to enable calling `associate`, `dissociate` and `isAssociated` functions on tokens.

## Non Goals

- Refactor the current implementation to better support future additions to the token facade contract functionality.

## Architecture

The method in which the redirection to proxy contract is implemented is that during the execution of an EVM request, we examine the contract address to see
if it is an HTS token address and if so we redirect the request to the proxy contract.  

More specifically the algorithm by which we get from (a) the call to the token address to (b) the precompiled contract is as follows:
1. The EVM encounters a call such as this `tokenAddress.<functionName>(<params>);`
2. The EVM calls [TokenEvmAccount.getEvmCode()](https://github.com/hashgraph/hedera-services/blob/f82b34132707755f7aa87e09e2de85ba9d5bfcd2/hedera-node/hedera-smart-contract-service-impl/src/main/java/com/hedera/node/app/service/contract/impl/state/TokenEvmAccount.java#L79) and loads the code stored on `tokenAddress`
3. In [TokenEvmAccount.getEvmCode()](https://github.com/hashgraph/hedera-services/blob/f82b34132707755f7aa87e09e2de85ba9d5bfcd2/hedera-node/hedera-smart-contract-service-impl/src/main/java/com/hedera/node/app/service/contract/impl/state/TokenEvmAccount.java#L79), we intercept the loading of the account code, check if this is actually a contract address, and we return the redirect bytecode for the token with the given address
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
5. This means that _any_ function can be redirected to as long as the HTS precompile handles the redirect call [here](https://github.com/hashgraph/hedera-services/blob/a1ccc19042d577c84076e97ee8485f33e2c9e696/hedera-node/hedera-smart-contract-service-impl/src/main/java/com/hedera/node/app/service/contract/impl/exec/processors/CustomMessageCallProcessor.java#L121). 


The following table describes the function selector for the new `associate`, `dissociate` and `isAssociated` functions and the associated function signatures.

| Function Selector  | Function Signature |
|--------------------|--------------------|
| 0x0a754de6         | associate()        |
| 0x5c9217e0         | dissociate()       |
| 0x4d8fdd6d         | isAssociated()     |

No arguments are necessary because 
- The token address was already determined by looking up the contract address to determine if it is an HTS token address.
- The address to associate/dissociate/isAssociated will be the caller (i.e. msg.sender)

Once the above functionality has been implemented in services, the end user will be able to call the `associate`, `dissociate` and `isAssociated` functions as follows:

```
IHRC(tokenAddress).associate()
IHRC(tokenAddress).dissociate()
IHRC(tokenAddress).isAssociated()
```

The solidity interface for IHRC will be the following

```
interface IHRC {
    function associate() external returns (responseCode);
    function dissociate() external returns (responseCode);
    function isAssociated() external returns (bool associated);
}
```

## Implementation

Extend `AssociationsTranslator` and `AssociationsDecoder` with this new use case of being called via the proxy contract.  
In `AssociationsTranslator` class `matches` and `callFrom` methods will need to be updated to handle the new function selectors for `associate` and `dissociate` functions.  
In `AssociationsDecoder`, methods decodes the given `HtsCallAttempt` into a `TransactionBody` for an HRC association and dissociation call.

Create `IsAssociatedTranslator` and `IsAssociatedCall` for the `isAssociated` function.  
The `IsAssociatedTranslator` will handle the new function selector.  
The `IsAssociatedCall` will override `resultOfViewingToken` method and return the result of the `isAssociated` function.

## Acceptance Tests

### Positive Tests
- Create a contract that performs the `associate`, `dissociate` and `isAssociated` functions on a fungible token and ensure that the functions can be called successfully by an EOA.
- Create a contract that performs the `associate`, `dissociate` and `isAssociated` functions on an NFT and ensure that the functions can be called successfully by an EOA.
- Create a contract that performs the `associate`, `dissociate` and `isAssociated` functions on a fungible token and ensure that the functions can be called successfully by a contract.
- Create a contract that performs the `associate`, `dissociate` and `isAssociated` functions on an NFT and ensure that the functions can be called successfully by a contract.
- Ensure that the `associate` and `dissociate` functions disregard the signature when called via the token facade.

### Negative Tests
- Ensure that the `associate` fails if one tries to associate beyond `tokens.maxPerAccount` on a single account
- Ensure that the `associate` fails if one tries to associate a token to the same account twice.
- Ensure that the `dissociate` fails if one tries to dissociate a token from an account that is not associated with the token.
- Ensure that the `dissociate` fails if balance on the account is not zero.