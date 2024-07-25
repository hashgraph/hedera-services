# Consistency Testing Tool

The purpose of the Consistency Testing Tool is to guarantee that transactions are handled exactly
once, and always in the same order.

## Motivation

When a node is shut down, there are potentially a number of transactions that have come to
consensus, but have not yet been included in a signed state. It is important that these transactions
are not lost upon restart, and that they are handled in precisely the same order as they were before
the node was shut down.

## Implementation

In order to guarantee consistency in the way that transactions are handled, the Consistency Testing
Tool utilizes a durable log file, containing information on when a transaction has come to
consensus, and in what round. This log file persists across reboots, and is read into memory at boot
time if it exists.

During operation, when a given round comes to consensus, the tool behaves in the following way:

- If the round coming to consensus doesn't already appear in the log:
  - the round is written to the log, along with all included transactions
  - each transaction is added to a hashmap, to guarantee that no transaction is handled more
    than once
- If the round coming to consensus already appears in the log:
  - the tool checks that the transactions included in the round are the same as the ones already
    in the log, in the exact same order
