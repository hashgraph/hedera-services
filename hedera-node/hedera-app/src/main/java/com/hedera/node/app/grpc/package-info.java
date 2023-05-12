/**
 * An implementation of gRPC handling that does not depend on protobuf generated handlers, but instead uses a builder
 * pattern to generate transaction and query gRPC handlers. This class has no dependency in any way on protobuf or
 * any other specific serialization format. Instead, it operates on bytes. This makes it very easy to test. This
 * implementation is also based on Helidon as the gRPC server implementation. In the future we plan to replace this
 * with our own gRPC/HTTP1.1/HTTP2 server implementation which will be highly optimized for minimizing byte array
 * copies and for reducing garbage collection overhead.
 *
 * <p>This implementation starts with the {@link com.hedera.node.app.grpc.GrpcServiceBuilder}. This is the only
 * public API in the entire package. With it, you can build a gRPC service definition for a transaction or query.
 * Internally, it handles all the details of marshalling and unmarshalling the bytes to/from the gRPC protocol
 * and representing them as PBJ data types ({@link com.hedera.pbj.runtime.io.buffer.Bytes} and
 * {@link com.hedera.pbj.runtime.io.buffer.BufferedData}).
 */
package com.hedera.node.app.grpc;
