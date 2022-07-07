# Types of Protobuf Deprecation

There are three senses in which a protobuf element (e.g. a type or field)
can be deprecated.

1. **End-of-Life:** Services ignores the element completely.
2. **Phase-Out:** Services still respects the element, 
but clients _must_ stop using it before end-of-life is reached.
3. **Client-Side:** Services uses the element internally, and
will continue to do so, but clients _should_ use a more efficient 
equivalent element.

This document indicates in what sense each currently `deprecated` 
protobuf element should be understood.

## End-of-Life Deprecations

- The [`proxyFraction`](../hapi-proto/src/main/proto/CryptoUpdate.proto) field is ignored.
- The [`generateRecord`](../hapi-proto/src/main/proto/TransactionBody.proto) field is ignored.

## Phase-Out Deprecations

- The [`Signature`/`ThresholdSignature`/`SignatureList`](../hapi-proto/src/main/proto/BasicTypes.proto) type constellation is still supported but superseded by `SignatureMap`. 
- The [raw `receiverSigRequired`, `receiveRecordThreshold`, and `sendRecordThreshold` types](../hapi-proto/src/main/proto/CryptoUpdate.proto) are still respected if set to non-default values, but superseded by their wrapped equivalents. 

## Client-Side Deprecations

- The [`body`](../hapi-proto/src/main/proto/Transaction.proto) field is used internally by Services to represent the semantics of a `Transaction`, and a client _may_ send a gRPC message with this field. However, it is preferable to set the `bodyBytes` field with the bytes of the serialized `body`.
