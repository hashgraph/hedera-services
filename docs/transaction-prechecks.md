# Hedera prechecks

When a gRPC client submits a [`Transaction`](https://hashgraph.github.io/hedera-protobufs/#Transaction.proto)
to a Hedera Services node, the node does not, in turn, immediately
submit the `Transaction` to the Platform to be assigned a consensus
timestamp (and then handled at that consensus time).

Rather, the node first performs a "precheck" on the `Transaction`; and 
_only_ submits it to the Platform if the precheck passes. Precheck 
may be broken into five stages, as follows:
  1. :building_construction:&nbsp; **Structural checks** test 
  if the top-level `bytes` fields in the `Transaction` are set 
  correctly, are within size limits, and contain a parseable
  gRPC `TransactionBody` that requests exactly one function.
  2. :memo:&nbsp; **Syntax checks** confirm that the parsed
  `TransactionBody` has all necessary fields set, including 
  a feasible valid start time and duration.
  3. :safety_pin:&nbsp; **System checks** test if the network can be 
  expected to handle the given `TransactionBody` if it does reach 
  consensus---for example, if the `TransactionID` is believed unique, 
  the requested HAPI function is enabled on the network, and its
  [throttle bucket(s)](./throttle-design.md) have capacity.
  4. :moneybag:&nbsp; **Solvency checks** determine if the payer 
  account set in the `TransactionID` is expected to be able to pay the 
  fees for the transaction.
  5. :dart:&nbsp; **Semantic checks** test if the specific HAPI
  function requested by the `Transaction` is well-formed.
The node only performs later checks if the earlier checks pass; and 
it performs no checks at all if the Platform is in maintenance mode, 
instead responding to to the gRPC request with a status code of 
`PLATFORM_NOT_ACTIVE`.

In this document, we cover all the response codes that a gRPC client 
can receive due to a failure in any of the _first four_ stages of 
precheck. However, here we only cover semantic checks for the 
`CryptoTransfer` function.

:old_key:&nbsp; It is important to understand that any non-free
`Query` implicitly submits a `CryptoTransfer` to the network to 
pay the answering node. Thus all the response codes in this document
are _also_ possible for a non-free query.

## :building_construction:&nbsp; Interpreting failed structural checks
- `INVALID_TRANSACTION` :arrow_right:
  * The top-level `Transaction` used both a deprecated field _and_ 
    the `signedTransactionBytes` field.
- `INVALID_TRANSACTION_BODY` :arrow_right:
  * No `TransactionBody` could be parsed from the top-level `Transaction`; or,
  * The parsed `TransactionBody` did not have any function-specific body set.
- `TRANSACTION_OVERSIZE` :arrow_right:
  * The serialized size of the `Transaction` exceeded 6144 bytes,
    which is the maximum allowed by the Platform.
- `TRANSACTION_TOO_MANY_LAYERS` :arrow_right:
  * The deserialized `TransactionBody` contained an excessively nested 
    complex key (roughly 20+ levels of nested `ThesholdKey`s).

## :memo:&nbsp; Interpreting failed syntax checks
- `INVALID_TRANSACTION_ID` :arrow_right:
  * The `TransactionBody` was missing a `TransactionID` field.
- `TRANSACTION_ID_FIELD_NOT_ALLOWED` :arrow_right:
  * The `TransactionID` field set the `scheduled` flag to `true`.
- `INVALID_NODE_ACCOUNT` :arrow_right:
  * There was no node `AccountID` given, or the given node 
    account didn't match the account of the node receiving the
    top-level `Transaction`. 
- `MEMO_TOO_LONG` :arrow_right:
  * The `TransactionBody` had a UTF-8 encoding of more than 100 bytes.
- `INVALID_ZERO_BYTE_IN_STRING` :arrow_right:
  * The memo in the `TransactionBody` included the `NUL` code point 
    (UTF-8 encoding of `0`, prohibited due to its common usage as a 
    string delimiter in database internals).
- `INVALID_TRANSACTION_DURATION` :arrow_right:
  * The given `transactionValidDuration` fell outside the range set
    by the [`hedera.transaction.minValidDuration` and `hedera.transaction.maxValidDuration` properties](../hedera-node/src/main/resources/bootstrap.properties), which default to 15 and 180 seconds, respectively.
- `INVALID_TRANSACTION_START` :arrow_right:
  * Even allowing for a delay of up to 
    [`hedera.transaction.minValidityBufferSecs`](../hedera-node/src/main/resources/bootstrap.properties) seconds to reach consensus (default is
    10 seconds), the given `transactionValidStart` is likely to 
    fall _after_ any assigned consensus timestamp, preventing the 
    transaction from being handled at consensus.
- `TRANSACTION_EXPIRED` :arrow_right:
  * After allowing for a delay of up to 
    [`hedera.transaction.minValidityBufferSecs`](../hedera-node/src/main/resources/bootstrap.properties) seconds to reach consensus (default is
    10 seconds), the window implied by the given `transactionValidStart` 
    and `transactionValidDuration` is likely to end _before_ any 
    assigned consensus timestamp, preventing the transaction from 
    being handled at consensus.

## :moneybag:&nbsp; Interpreting failed solvency checks
- `INSUFFICIENT_TX_FEE` :arrow_right:
  * No positive `transactionFee` was offered, or a positive fee
    _was_ offered, but was insufficient.
- `PAYER_ACCOUNT_NOT_FOUND` :arrow_right:
  * There was no payer `AccountID` given, or a payer _was_ given,  
    but the given account was deleted or missing.
