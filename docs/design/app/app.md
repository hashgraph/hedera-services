# The Application Module

The main module of a Hedera consensus node is the `hedera-app` module. This module depends on all other modules,
and no other modules depend on it. This module contains the main entrypoint of the application which is the `Hedera`
class. This module is responsible for application lifecycle, interfacing with the hashgraph platform, managing the
gRPC or other server processes, constructing and managing the lifecycle of service plugins, generating record streams,
and all other core application tasks. Each of these is broken into a separate package within the module.

The follow subpackages contain implementation details:
- [`fee`](fees.md): An implementation of `FeeAccumulator` and other fee engine components
- [`grpc`](grpc.md): Components for handling gRPC
- [`record`](records.md): The `RecordStreamManager` is here, along with an implementation of the `RecordBuilder` interfaces
- [`state`](states.md): Classes that implement the SPI interfaces for states, and all things related to merkle trees
- [`throttle`](throttles.md): The implementation of the `ThrottleAccumulator` and the throttle engine
- [`workflows`](workflows.md): Classes for the various workflows such as `TransactionWorkflow`, `QueryWorkflow`, `IngestWorkflow`, etc.

## Fees

The [`fee`](fees.md) package contains the fee engine, and an implementation of the `FeeAccumulator`, which is used
by services to record the fees that need to be charged for that service.

## gRPC

The [`grpc`](grpc.md) package contains generic gRPC handlers for all types of gRPC calls that a client can make to
the Hedera API (HAPI).

## Records

[`record`](records.md) contains an implementation of the `RecordStreamManager`, and of the different `RecordBuilder`s
for different modules. This component produces the record stream that forms the principal blockchain of the system.

## States

The [`states`](states.md) package contains the implementation of `ReadableState`, `WritableState` and other APIs
defined in the SPI related to states.

## Throttles

[`throttle`](throttles.md) holds the implementation of the throttle engine, and the `ThrottleAccumulator` which is
used by workflows and/or services to implement throttling.

**NEXT: [Fees](fees.md)**
