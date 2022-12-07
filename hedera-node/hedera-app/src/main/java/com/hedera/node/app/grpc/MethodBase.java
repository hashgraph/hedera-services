/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import com.hedera.node.app.SessionContext;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.SpeedometerMetric;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import java.nio.ByteBuffer;
import java.util.Objects;

abstract class MethodBase implements ServerCalls.UnaryMethod<ByteBuffer, ByteBuffer> {
    // To be set by configuration. See Issue #4294
    private static final int MAX_MESSAGE_SIZE = 1024 * 6; // 6k

    private static final String COUNTER_HANDLED_NAME_TPL = "%sHdl";
    private static final String COUNTER_RECEIVED_NAME_TPL = "%sRcv";
    private static final String COUNTER_FAILED_NAME_TPL = "%sFail";
    private static final String SPEEDOMETER_HANDLED_NAME_TPL = "%sHdl/sec";
    private static final String SPEEDOMETER_RECEIVED_NAME_TPL = "%sRcv/sec";

    private static final String COUNTER_HANDLED_DESC_TPL = "number of %s handled";
    private static final String COUNTER_RECEIVED_DESC_TPL = "number of %s received";
    private static final String COUNTER_FAILED_DESC_TPL = "number of %s failed";
    private static final String SPEEDOMETER_HANDLED_DESC_TPL = "number of %s handled per second";
    private static final String SPEEDOMETER_RECEIVED_DESC_TPL = "number of %s received per second";

    /**
     * Per-thread shared resources are shared in a {@link SessionContext}. We store these in a
     * thread local, because we do not have control over the thread pool used by the underlying gRPC
     * server.
     */
    private static final ThreadLocal<SessionContext> SESSION_CONTEXT_THREAD_LOCAL =
            ThreadLocal.withInitial(
                    () ->
                            new SessionContext(
                                    Query.parser(),
                                    Transaction.parser(),
                                    SignedTransaction.parser(),
                                    TransactionBody.parser()));

    /**
     * Per-thread shared ByteBuffer for responses. We store these in a thread local, because we do
     * not have control over the thread pool used by the underlying gRPC server.
     */
    private static final ThreadLocal<ByteBuffer> BUFFER_THREAD_LOCAL =
            ThreadLocal.withInitial(() -> ByteBuffer.allocate(MAX_MESSAGE_SIZE));

    /** The name of the service associated with this method. */
    protected final String serviceName;

    /** The name of the method. */
    protected final String methodName;

    /** A metric for the number of times this method has been invoked */
    private final Counter callsReceivedCounter;

    /** A metric for the number of times this method successfully handled an invocation */
    private final Counter callsHandledCounter;

    /** A metric for the number of times this method failed to handle an invocation */
    private final Counter callsFailedCounter;

    /** A metric for the calls per second successfully this method was invoked */
    private final SpeedometerMetric callsReceivedSpeedometer;

    /** A metric for the calls per second successfully handled by this method */
    private final SpeedometerMetric callsHandledSpeedometer;

    /**
     * Create a new instance.
     *
     * @param serviceName a non-null reference to the service name
     * @param methodName a non-null reference to the method name
     */
    MethodBase(
            @NonNull final String serviceName,
            @NonNull final String methodName,
            @NonNull final Metrics metrics) {

        this.serviceName = Objects.requireNonNull(serviceName);
        this.methodName = Objects.requireNonNull(methodName);

        this.callsHandledCounter =
                counter(metrics, COUNTER_HANDLED_NAME_TPL, COUNTER_HANDLED_DESC_TPL);
        this.callsReceivedCounter =
                counter(metrics, COUNTER_RECEIVED_NAME_TPL, COUNTER_RECEIVED_DESC_TPL);
        this.callsFailedCounter =
                counter(metrics, COUNTER_FAILED_NAME_TPL, COUNTER_FAILED_DESC_TPL);
        this.callsHandledSpeedometer =
                speedometer(metrics, SPEEDOMETER_HANDLED_NAME_TPL, SPEEDOMETER_HANDLED_DESC_TPL);
        this.callsReceivedSpeedometer =
                speedometer(metrics, SPEEDOMETER_RECEIVED_NAME_TPL, SPEEDOMETER_RECEIVED_DESC_TPL);
    }

    @Override
    public void invoke(
            @NonNull final ByteBuffer requestBuffer,
            @NonNull final StreamObserver<ByteBuffer> responseObserver) {
        try {
            // Track the number of times this method has been called
            callsReceivedCounter.increment();
            callsReceivedSpeedometer.cycle();

            // Prepare the response buffer
            final var session = SESSION_CONTEXT_THREAD_LOCAL.get();
            final var responseBuffer = BUFFER_THREAD_LOCAL.get();
            responseBuffer.clear();

            // Call the workflow
            handle(session, requestBuffer, responseBuffer);

            // Respond to the client
            responseBuffer.flip();
            responseObserver.onNext(responseBuffer);
            responseObserver.onCompleted();

            // Track the number of times we successfully handled a call
            callsHandledCounter.increment();
            callsHandledSpeedometer.cycle();
        } catch (final Throwable th) {
            // Track the number of times we failed to handle a call
            callsFailedCounter.increment();
            responseObserver.onError(th);
        }
    }

    /**
     * Called to handle the method invocation. Implementations should <b>only</b> throw an exception
     * if a gRPC <b>ERROR</b> is to be returned.
     *
     * @param session The {@link SessionContext} for this call
     * @param requestBuffer The {@link ByteBuffer} containing the protobuf bytes for the request
     * @param responseBuffer A {@link ByteBuffer} into which the response protobuf bytes may be
     *     written
     */
    protected abstract void handle(
            @NonNull final SessionContext session,
            @NonNull final ByteBuffer requestBuffer,
            @NonNull final ByteBuffer responseBuffer);

    /**
     * Helper method for creating a {@link Counter} metric.
     *
     * @param metrics The {@link Metrics} object to use to create the counter.
     * @param nameTemplate A template to use for generating the metric name
     * @param descriptionTemplate A template to use for generating the metric description
     * @return The metric
     */
    protected final @NonNull Counter counter(
            @NonNull final Metrics metrics,
            @NonNull final String nameTemplate,
            @NonNull final String descriptionTemplate) {
        final var baseName = serviceName + "/" + methodName;
        final var name = String.format(nameTemplate, baseName);
        final var desc = String.format(descriptionTemplate, baseName);
        return metrics.getOrCreate(new Counter.Config("app", name).withDescription(desc));
    }

    /**
     * Helper method for creating a {@link SpeedometerMetric} metric.
     *
     * @param metrics The {@link Metrics} object to use to create the speedometer.
     * @param nameTemplate A template to use for generating the metric name
     * @param descriptionTemplate A template to use for generating the metric description
     * @return The metric
     */
    protected final @NonNull SpeedometerMetric speedometer(
            @NonNull final Metrics metrics,
            @NonNull final String nameTemplate,
            @NonNull final String descriptionTemplate) {
        final var baseName = serviceName + "/" + methodName;
        final var name = String.format(nameTemplate, baseName);
        final var desc = String.format(descriptionTemplate, baseName);
        return metrics.getOrCreate(new SpeedometerMetric.Config("app", name).withDescription(desc));
    }
}
