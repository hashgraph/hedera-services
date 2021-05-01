# Hedera prechecks

When a gRPC client submits a [`Transaction`](https://hashgraph.github.io/hedera-protobufs/#Transaction.proto)
to a Hedera Services node, the node does not---in turn---immediately
submit the `Transaction` to the Platform (where it would be assigned 
a consensus timestamp, and then handled at that consensus time).

Rather, the node first performs a "precheck" on the `Transaction` and 
_only_ submits it to the Platform if the precheck passes. Precheck 
may be broken into five stages, as follows:
  1. :building_construction:&nbsp; **Structural checks** test 
  if the top-level `bytes` fields in the `Transaction` are set 
  correctly, are within size limits, and contain a parseable
  gRPC `TransactionBody` that requests exactly one function.

  2. :memo:&nbsp; **Syntax checks** confirm that the parsed
  `TransactionBody` has all necessary fields set, including 
  a feasible valid start time and duration.

  3. :shield:&nbsp; **System checks** test if the network can be 
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
can receive due to a failure in any of the first four stages of 
precheck. We do not include an exhaustive list of response codes from
failed semantic prechecks, since they are usually self-explanatory (e.g. 
`ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS`); and are likely to be removed 
in future releases as Hedera enhances its client libraries to perform 
such checks before any network interaction occurs. (Of course the checks
will still be enforced at consensus! :guard:)

## A comment on queries
It is important to understand that the header of any non-free `Query` 
includes a `CryptoTransfer` transaction that will be submitted to the 
network to pay the node that answers the query. 

This means all the response codes in this document are _also_ 
possible for any non-free query, since they could arise from the 
precheck performed on the query's enclosed `CryptoTransfer`.  

## :building_construction:&nbsp; Response codes from failed structural checks
- `INVALID_TRANSACTION`
  * The top-level `Transaction` used both a deprecated field _and_ 
    the `signedTransactionBytes` field.
- `INVALID_TRANSACTION_BODY`
  * No `TransactionBody` could be parsed from the top-level `Transaction`; or,
  * The parsed `TransactionBody` did not have any function-specific body set.
- `TRANSACTION_OVERSIZE`
  * The serialized size of the `Transaction` exceeded 6144 bytes,
    which is the maximum allowed by the Platform.
- `TRANSACTION_TOO_MANY_LAYERS`
  * The deserialized `TransactionBody` contained an excessively nested 
    complex key (roughly 20+ levels of nested `ThesholdKey`s).

## :memo:&nbsp; Interpreting failed syntax checks
- `INVALID_TRANSACTION_ID`
  * The `TransactionBody` was missing a `TransactionID` field.
- `TRANSACTION_ID_FIELD_NOT_ALLOWED`
  * The `TransactionID` field set the `scheduled` flag to `true`.
- `INVALID_NODE_ACCOUNT`
  * There was no node `AccountID` given, or the given node 
    account didn't match the account of the node receiving the
    top-level `Transaction`. 
- `MEMO_TOO_LONG`
  * The `TransactionBody` had a UTF-8 encoding of more than 100 bytes.
- `INVALID_ZERO_BYTE_IN_STRING`
  * The memo in the `TransactionBody` included the `NUL` code point 
    (UTF-8 encoding of `0`, prohibited due to its common usage as a 
    string delimiter in database internals).
- `INVALID_TRANSACTION_DURATION`
  * The given `transactionValidDuration` fell outside the range set
    by the [`hedera.transaction.minValidDuration` and `hedera.transaction.maxValidDuration` properties](../hedera-node/src/main/resources/bootstrap.properties), which default to 15 and 180 seconds, respectively.
- `INVALID_TRANSACTION_START`
  * Even allowing for a delay of up to 
    [`hedera.transaction.minValidityBufferSecs`](../hedera-node/src/main/resources/bootstrap.properties) seconds to reach consensus (default is
    10 seconds), the given `transactionValidStart` is likely to 
    fall _after_ any assigned consensus timestamp, preventing the 
    transaction from being handled at consensus.
- `TRANSACTION_EXPIRED`
  * After allowing for a delay of up to 
    [`hedera.transaction.minValidityBufferSecs`](../hedera-node/src/main/resources/bootstrap.properties) seconds to reach consensus (default is
    10 seconds), the window implied by the given `transactionValidStart` 
    and `transactionValidDuration` is likely to end _before_ any 
    assigned consensus timestamp, preventing the transaction from 
    being handled at consensus.

## :moneybag:&nbsp; Interpreting failed solvency checks
- `INSUFFICIENT_TX_FEE`
  * No positive `transactionFee` was offered, or a positive fee
    _was_ offered, but was insufficient.
- `PAYER_ACCOUNT_NOT_FOUND`
  * There was no payer `AccountID` given, or a payer _was_ given,  
    but the given account was deleted or missing.
