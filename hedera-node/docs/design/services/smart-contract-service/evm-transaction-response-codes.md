# EVM Transaction Response Codes in Hedera

When executing an EVM transaction to a smart contract using `contractCall` in Hedera, different response codes may be present for the top-level status after the transaction is finished. These responses are found in the transaction receipt.

The errors listed below could occur for a `contractCall` which creates child transactions, and the revertReason or haltReason which is set when operation fails is propagated to the top-level call.
There are other errors that could occur in the childs, but the ones listed below are the specific ones that are propagated to the top-level call, which currently happens [here](https://github.com/hashgraph/hedera-services/blob/774ed309600e4b4acd9e1ca72fcd87d354c8b9ff/hedera-node/hedera-smart-contract-service-impl/src/main/java/com/hedera/node/app/service/contract/impl/hevm/HederaEvmTransactionResult.java#L141-L169), in the `HederaEvmTransactionResult` in the `finalStatus()` method.
Other revert/halt reasons from the child should default to CONTRACT_REVERT_EXECUTED for the top-level status.

## Possible Propagated Response Codes

- **INVALID_SOLIDITY_ADDRESS**
    -  The system is not able to find the user’s Solidity address.

- **INVALID_ALIAS_KEY**
    -  An alias used in a CryptoTransfer transaction is not the serialization of a primitive Key message. This means a Key with a single Ed25519 or ECDSA(secp256k1) public key and no unknown protobuf fields.

- **INVALID_SIGNATURE**
    -  The transaction signature is not valid.

- **MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED**
    -  The maximum number of entities allowed in the current price regime have been created.

- **INSUFFICIENT_GAS**
    -  Not enough gas was supplied to execute the transaction.

- **LOCAL_CALL_MODIFICATION_EXCEPTION**
    -  Local execution (query) is requested for a function which changes state.

- **MAX_CHILD_RECORDS_EXCEEDED**
    -  A contract transaction tried to use more than the allowed number of child records, either through system contract records or internal contract creations.

- **INVALID_CONTRACT_ID**
    -  The contract ID is invalid or does not exist.

- **INVALID_FEE_SUBMITTED**
    -  The fee submitted is invalid.

- **MAX_CONTRACT_STORAGE_EXCEEDED**
    -  Contract permanent storage exceeded the currently allowable limit.

- **MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED**
    -  All contract storage allocated to the current price regime has been consumed.

- **INSUFFICIENT_TX_FEE**
    -  The fee provided in the transaction is insufficient for this type of transaction.

- **INSUFFICIENT_PAYER_BALANCE**
    -  The transaction payer could not afford a custom fee.

- **CONTRACT_EXECUTION_EXCEPTION**
    -  This code is used for any contract execution-related error not handled by specific error codes listed above.
    - *Example:* An error in Besu due to differences in supported Solidity versions. For instance, if Besu supports up to 0.8.23 and the contract is compiled with a version higher than 0.8.23 containing opcode changes, this error would be thrown.

- **CONTRACT_REVERT_EXECUTED**
    -  The default error for contract execution being reverted. Which also happens when REVERT OPCODE is executed in the contract.