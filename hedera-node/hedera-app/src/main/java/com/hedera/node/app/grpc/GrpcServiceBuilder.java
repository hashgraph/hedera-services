/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.grpc;

import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.workflows.ingest.IngestWorkflow;
import com.hedera.node.app.workflows.query.QueryWorkflow;
import com.swirlds.common.metrics.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.MethodDescriptor;
import io.helidon.grpc.core.MarshallerSupplier;
import io.helidon.grpc.server.ServiceDescriptor;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convenient builder API for constructing gRPC Service definitions. The {@link GrpcServiceBuilder}
 * is capable of constructing service definitions for {@link Transaction} based calls using the
 * {@link #transaction(String)} method, or {@link Query} based calls using the {@link
 * #query(String)} method.
 *
 * <p>Every gRPC service definition needs to define, per service method definition, the "marshaller"
 * to use for marshalling and unmarshalling binary data sent in the protocol. Usually this is some
 * kind of protobuf parser. In our case, we simply read a byte array from the {@link InputStream}
 * and pass the array raw to the appropriate workflow implementation {@link IngestWorkflow} or
 * {@link QueryWorkflow}, so they can do the protobuf parsing. We do this to segregate the code.
 * This class is <strong>only</strong> responsible for the gRPC call, the workflows are responsible
 * for working with protobuf.
 */
/*@NotThreadSafe*/
public final class GrpcServiceBuilder {
    /** Logger */
    private static final Logger LOG = LoggerFactory.getLogger(GrpcServiceBuilder.class);

    /**
     * Create a single JVM-wide Marshaller instance that simply reads/writes byte arrays to/from
     * {@link InputStream}s. This class is totally thread safe because it does not reuse byte
     * arrays. If we get more sophisticated and reuse byte array buffers, we will need to use a
     * {@link ThreadLocal} to make sure we have a unique byte array buffer for each request.
     */
    private static final DataBufferMarshaller NOOP_MARSHALLER = new DataBufferMarshaller();

    /**
     * Create a single instance of the marshaller supplier to provide to every gRPC method
     * registered with the system. We only need the one, and it always returns the same
     * NoopMarshaller instance. This is fine to use with multiple app instances within the same JVM.
     */
    private static final MarshallerSupplier MARSHALLER_SUPPLIER = new MarshallerSupplier() {
        @Override
        public <T> MethodDescriptor.Marshaller<T> get(final Class<T> clazz) {
            //noinspection unchecked
            return (MethodDescriptor.Marshaller<T>) NOOP_MARSHALLER;
        }
    };

    /** The name of the service we are building. */
    private final String serviceName;

    /** The {@link IngestWorkflow} to invoke for transaction methods. */
    private final IngestWorkflow ingestWorkflow;

    /** The {@link QueryWorkflow} to invoke for query methods. */
    private final QueryWorkflow queryWorkflow;

    /**
     * The set of transaction method names that need corresponding service method definitions
     * generated.
     */
    private final Set<String> txMethodNames = new HashSet<>();

    /**
     * The set of query method names that need corresponding service method definitions generated.
     */
    private final Set<String> queryMethodNames = new HashSet<>();

    /**
     * Creates a new builder.
     *
     * @param serviceName The name of the service. Cannot be null or blank.
     * @param ingestWorkflow The workflow to use for handling all transaction ingestion API calls
     * @param queryWorkflow The workflow to use for handling all queries
     */
    public GrpcServiceBuilder(
            @NonNull final String serviceName,
            @NonNull final IngestWorkflow ingestWorkflow,
            @NonNull final QueryWorkflow queryWorkflow) {
        this.ingestWorkflow = Objects.requireNonNull(ingestWorkflow);
        this.queryWorkflow = Objects.requireNonNull(queryWorkflow);
        this.serviceName = Objects.requireNonNull(serviceName);
        if (serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName cannot be blank");
        }
    }

    /**
     * Register the creation of a new gRPC method for handling transactions with the given name.
     * This call is idempotent.
     *
     * @param methodName The name of the transaction method. Cannot be null or blank.
     * @return A reference to the builder.
     */
    public @NonNull GrpcServiceBuilder transaction(@NonNull final String methodName) {
        if (Objects.requireNonNull(methodName).isBlank()) {
            throw new IllegalArgumentException("The gRPC method name cannot be blank");
        }

        txMethodNames.add(methodName);
        return this;
    }

    /**
     * Register the creation of a new gRPC method for handling queries with the given name. This
     * call is idempotent.
     *
     * @param methodName The name of the query method. Cannot be null or blank.
     * @return A reference to the builder.
     */
    public @NonNull GrpcServiceBuilder query(@NonNull final String methodName) {
        if (Objects.requireNonNull(methodName).isBlank()) {
            throw new IllegalArgumentException("The gRPC method name cannot be blank");
        }

        queryMethodNames.add(methodName);
        return this;
    }

    /**
     * Build a gRPC {@link ServiceDescriptor} for each transaction and query method registered with
     * this builder.
     *
     * @return a non-null {@link ServiceDescriptor}.
     */
    public ServiceDescriptor build(final Metrics metrics) {
        final var builder = ServiceDescriptor.builder(null, serviceName);
        txMethodNames.forEach(methodName -> {
            LOG.debug("Registering gRPC transaction method {}.{}", serviceName, methodName);
            final var method = new TransactionMethod(serviceName, methodName, ingestWorkflow, metrics);
            builder.unary(methodName, method, rules -> rules.marshallerSupplier(MARSHALLER_SUPPLIER));
        });
        queryMethodNames.forEach(methodName -> {
            LOG.debug("Registering gRPC query method {}.{}", serviceName, methodName);
            final var method = new QueryMethod(serviceName, methodName, queryWorkflow, metrics);
            builder.unary(methodName, method, rules -> rules.marshallerSupplier(MARSHALLER_SUPPLIER));
        });
        return builder.build();
    }
}
