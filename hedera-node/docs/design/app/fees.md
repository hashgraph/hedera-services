# Fees

Contains code related to the fee engine. The `FeeSchedule` is defined in the protobuf, and in theory
saved in some file on the ledger. Code in the `fee` package would pay attention to changes to this file and use it
for implementing the `FeeAccumulator`. The code in this package right now is just a placeholder.

**NEXT: [gRPC](grpc.md)**
