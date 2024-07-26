# EVM Transaction Response Codes in Hedera

When executing a transaction processed by the EVM (i.e., EVM Transaction) using `ContractCreate`, `ContractCall`, `EthereumTransaction`, and `ContractCallLocal` in Hedera, different response codes may be present for the top-level status after the transaction is finished.
These responses are found in the transaction receipt. This document's goal is to list the possible EVM transaction type-specific status codes for failure (i.e. different from SUCCESS).

## Propagated EVM Transaction Response Codes

These response codes can appear for `ContractCall`, `ContractCreate`, and `EthereumTransaction`.
It is possible for `ContractCreate` and `ContractCall` to create child transactions, and if one of them fails with a special revert or halt reason, the status is propagated to the top-level status of the transaction.
- `ContractCall` creates child transactions by executing logic that results in another call to a function/contract.
- `ContractCreate` creates child transactions by having logic containing calls in the constructor.
- `EthereumTransaction` allows for wrapped ContractCall and ContractCreate transactions, which can also create child transactions in the same manner as described above.

Note: The description for the statuses is copied from [protobuf repo](https://github.com/hashgraph/hedera-protobufs/blob/main/services/response_code.proto)

- **CONTRACT_REVERT_EXECUTED**
  - The default error for contract execution being reverted. This also happens when the REVERT opcode is executed in the contract.
- **INVALID_SOLIDITY_ADDRESS**
  - The system cannot find the user’s Solidity address.
- **INVALID_ALIAS_KEY**
  - An alias used in a `CryptoTransfer` transaction is not the serialization of a primitive Key message. This means a Key with a single Ed25519 or ECDSA (secp256k1) public key and no unknown protobuf fields.
- **INVALID_SIGNATURE**
  - The transaction signature is not valid.
- **INSUFFICIENT_GAS**
  - Not enough gas was supplied to execute the transaction.
- **LOCAL_CALL_MODIFICATION_EXCEPTION**
  - Local execution (query) is requested for a function that changes state.
- **MAX_CHILD_RECORDS_EXCEEDED**
  - A contract transaction tried to use more than the allowed number of child records, either through system contract records or internal contract creations.
- **INVALID_CONTRACT_ID**
  - The contract ID is invalid or does not exist.
- **INVALID_FEE_SUBMITTED**
  - The fee submitted is invalid.
- **INSUFFICIENT_TX_FEE**
  - The fee provided in the transaction is insufficient for this type of transaction.
- **INSUFFICIENT_PAYER_BALANCE**
  - The transaction payer could not afford a custom fee.
- **CONTRACT_EXECUTION_EXCEPTION**
  - This code is used for any contract execution-related error not handled by specific error codes listed above.
  - *Example*: An error in Besu due to differences in supported EVM versions. For instance, if Besu supports up to the Shanghai version and the contract is compiled with the Cancun version containing opcode changes, this error would be thrown.

Those are some possible, but very unlikely to happen, top-level status codes.
As for MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED and MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED, they indicate too many contracts/entities in the entire system, so the caller can't do anything except wait until the network is fixed.
MAX_CONTRACT_STORAGE_EXCEEDED applies to an individual contract but still is very hard to reach under normal operation.

- **MAX_CONTRACT_STORAGE_EXCEEDED**
  - Contract permanent storage exceeded the currently allowable limit.
- **MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED**
  - All contract storage allocated to the current price regime has been consumed.
- **MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED**
  - The maximum number of entities allowed in the current price regime have been created.

## Additional statuses, that are not propagated from child transactions, but can be returned as top-level status:

Those status codes can also be present for `ContractCall`, `ContractCreate` via `EthereumTransaction`.

- **CONTRACT_NEGATIVE_VALUE**
  - Negative value/initial balance was specified in a smart contract call/create.
- **CONTRACT_DELETED**
  - Contract is marked as deleted.
  - Scope: This can happen as a top-level status only for contract call and ethereum contract call.

## Create operation Specific Response Codes

These responses do include ContractCreate via EthereumTransaction.

- **AUTORENEW_DURATION_NOT_IN_RANGE**
  - The duration is not a subset of `[MINIMUM_AUTORENEW_DURATION, MAXIMUM_AUTORENEW_DURATION]`.
- **CONTRACT_NEGATIVE_GAS**
  - Negative gas was offered in a smart contract call.
- **MAX_GAS_LIMIT_EXCEEDED**
  - Gas exceeded the currently allowable gas limit per transaction.
- **INVALID_MAX_AUTO_ASSOCIATIONS**
  - The maximum automatic associations value is not valid. The most common cause for this error is a value less than `-1`.
- **REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT**
  - Cannot set the number of automatic associations for an account more than the maximum allowed token associations tokens.maxPerAccount. If you would like to exceed that, you should set maxAutoAssociations to -1.
- **PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED**
  - Proxy account ID field is deprecated.
- **INVALID_AUTORENEW_ACCOUNT**
  - The `autoRenewAccount` specified is not a valid, active account.
- **ERROR_DECODING_BYTESTRING**
  - Decoding the smart contract binary to a byte array failed. Check that the input is a valid hex string.
- **INVALID_FILE_ID**
  - The file ID is invalid or does not exist.
- **FILE_DELETED**
  - The file has been marked as deleted.
- **CONTRACT_BYTECODE_EMPTY**
  - Bytecode for the smart contract is of length zero.
- **CONTRACT_FILE_EMPTY**
  - The file to create a smart contract was of length zero.
- **INVALID_ACCOUNT_ID**
  - The account ID is invalid or does not exist.

## Ethereum Transaction Specific Response Codes

These are additional response codes that can appear specifically for `EthereumTransaction`.

- **WRONG_NONCE**
  - This transaction specified an `ethereumNonce` that is not the current `ethereumNonce` of the account.
- **WRONG_CHAIN_ID**
  - `EthereumTransaction` was signed against a chain ID that this network does not support.
- **NEGATIVE_ALLOWANCE_AMOUNT**
  - The maxGasAllowance field is set to less than 0. This represents the maximum amount of tinybars, that the payer of the transaction is willing to pay to complete the transaction.
- **INVALID_ETHEREUM_TRANSACTION**
  - The Ethereum transaction either failed parsing or failed signature validation, or some other `EthereumTransaction` error not covered by another response code.
