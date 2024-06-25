# hbar Allowance and Approval

## Purpose

The current IHederaTokenService interface within the Hedera Smart Contract Service provides support for approving an allowance to a `spender` account for tokens. This document outlines the implementation of hbar allowance and approval functionality in the Hedera Smart Contract Service, extending this capability to an account's hbars.

## References
[HIP-632](https://hips.hedera.com/hip/hip-632) - HIP that introduces the Hedera Account Service (HAS) system contract.

[HIP-906](https://hips.hedera.com/hip/hip-906) - HIP that proposes  the `hbarAllowance` and `hbarApprove` functions discussed in this document.

## Goals

The goal is to define the necessary classes for implementing the `hbarAllowance` and `hbarApprove` functionality within the Hedera Smart Contract Service. We'll focus on the implementation details in the modularized code base.

## Non Goals

The implementation for crypto allowance and approval in the Token Service Module will only be described at a high level, as this implementation is already completed. The smart contract service will dispatch the transaction to the Token Service Module for processing.

## Architecture

From an end-user perspective, we introduce two new interfaces to support the `hbarAllowance` and `hbarApprove` functionality:

### IHRC632
For externally owned account (EOA) callers of these functions, a new interface called `IHRC632` will be implemented. 
We'll follow the pattern established for token proxy contracts, as discussed in the `Specification` section of [HIP-719](https://hips.hedera.com/hip/hip-719). 
However, there's a key difference: `IHRC632` will act on an account address rather than a token address. It will allow for management of the owners allowance to a `spender` account for a specified amount of hbars.

To achieve this, we'll create a new proxy contract that wraps and forwards requests to the Hedera Account Service (HAS) system contract. Similar logic to the token implementation described in HIP-719 will be applied. For security purposes, the EOA account sender must also sign the transaction.

### IHederaAccountService
To enable smart contract calls to these functions, we will introduce a new interface called `IHederaAccountService`, along with the function signatures for the new functions described below.
The `HederaAccountService` class will implement this interface, forwarding all requests to the `HAS` system contract address (`0x16a`) using the pattern established by the HTS system contract

## Implementation

### Base Classes and Interfaces
Some classes in the current implementation of the HTS system contract need to be renamed or refactored in order to support code reuse between `HTS` and `HAS` system contracts. Additionally, marker interfaces and/or base classes may be necessary to add. 
Candidate interfaces and classes for refactoring are as follows:
- HtsCall
- HtsCallAddressChecks
- AbstractHtsCall
- AbstractHtsCallTranslator
- HTSCallTranslator


In addition, `HAS` specific implementation of the following classes will be necessary as the entity in focus is an account rather than a token:
- HasCallAttempt
- HasCallFactory

### New Function Implementations

The following table describes the function selectors for the `hbarAllowance` and `hbarApprove` functions and the associated function signatures and responses in `IHederaAccountService`.

| Function Selector Hash   | Function Signature                                           | Response                 |                                                                                                      | 
|--------------------------|--------------------------------------------------------------|--------------------------|------------------------------------------------------------------------------------------------------|
| `0xfec46666`             | `hbarAllowance(address owner, address spender)`              | `(ResponseCode, int256)` | The response code from the call and the amount of hbar allowances currently available to the spender | 
| `0xa0918464`             | `hbarApprove(address owner, address spender, int256 amount)` | `ResponseCode`           | The response code from the call                                                                      |

Similar to implementation that exist for `HTS` function calls, the following new classes will be introduced:

- HbarAllowanceTranslator
- HbarAllowanceCall
- HbarApproveTranslator
- HbarApproveCall

### Support for Proxy Contract
The following code path will be introduced in order to support calls to the `hbarAllowance` and `hbarApprove` functions for EOA accounts via the `IHRC632` interface.
The following pseudo code describes the logic during the `MessageFrame` construction for top level calls:

```java
if (messageFrame.to is an EOA account address) {
    if (transaction.functionSelector is contained in {hbarAllowance, hbarApprove}) {
        frame.proxyWorldUpdater.accountBytecodeType = RETURN_PROXY_CONTRACT_BYTECODE;
    }
}

if (frame.proxyWorldUpdater.accountBytecodeType == RETURN_PROXY_CONTRACT_BYTECODE) {
    codeToExecute = REDIRECT_FOR_ACCOUNT_PROXY_CONTRACT_BYTECODE;
} else {
    codeToExecute = 0x;  // this results in a no-op success
}
```

Internal calls to the `hbarAllowance` and `hbarApprove` functions will only be allowed via the `IHederaAccountService` interface from contracts and thus does not require special handling.

### Hedera Token Service

For `hbarApprove`, the system must add the approval for the given account or contract to the spender account.  This is achieved via dispatching a request to the Token Service.
Once the smart contract service dispatches the transaction to the Token Service it performs the following steps:

1. Validate semantic correctness of the transaction.  Signature verification is performed in `prehandle`.
2. Grant approval to the spender for the allowance amount from the effective owner which is performed in `handle` after additional checks (existance of the account, sufficient balance, negative numbers etc.).

The implementation can be found in the `CryptoApproveAllowanceHandler` class.

## Acceptance Tests
In addition to unit tests and xTests, the following acceptance tests will be implemented to verify the functionality and security of the `hbarAllowance` and `hbarApprove` functions.

### BDD Tests

#### Positive Tests
- Test that an EOA can call the `hbarApprove` function and grant an allowance to another account when an EOA signs the transaction.  The negative case is the first negative case below.
- Test that a contract can call the `hbarApprove` function and grant an allowance to another account for value (hbars) that the contract owns. The negative case is the second negative case below.
- Test that an EOA can call the `hbarAllowance` function and retrieve the allowance granted to the EOA, another account or contract.
- Test that a contract can call the `hbarAllowance` function and retrieve the allowance granted to the calling contract, another account or contract.

#### Negative Tests
- Test that an EOA calling the `hbarApprove` function without the EOA signature will fail.
- Test that a contract calling the `hbarApprove` function for hbars not owned by the contract will fail.
- Test that an attempt to approve more hbars that the owner possesses will fail.


