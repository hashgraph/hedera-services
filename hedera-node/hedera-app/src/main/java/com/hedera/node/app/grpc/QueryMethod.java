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

import com.hedera.node.app.SessionContext;
import com.hedera.node.app.workflows.query.QueryWorkflow;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.SpeedometerMetric;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Handles gRPC duties for processing {@link com.hederahashgraph.api.proto.java.Query} gRPC calls. A
 * single instance of this class is used by all query threads in the node.
 *
 * <p>FUTURE WORK: ThreadSafe annotation missing in spotbugs annotations but should be added to
 * class
 */
/*@ThreadSafe*/
final class QueryMethod extends MethodBase {
    private static final String COUNTER_ANSWERED_NAME_TPL = "%sSub";
    private static final String COUNTER_ANSWERED_DESC_TPL = "number of %s answered";
    private static final String SPEEDOMETER_ANSWERED_NAME_TPL = "%sSub/sec";
    private static final String SPEEDOMETER_ANSWERED_DESC_TPL = "number of %s answered per second";

    /** The workflow contains all the steps needed for handling the query. */
    private final QueryWorkflow workflow;

    /** A metric for the number of times the query was answered */
    private final Counter queriesAnsweredCounter;

    /** A metric for the calls per second that queries were answered */
    private final SpeedometerMetric queriesAnsweredSpeedometer;

    /**
     * Create a new QueryMethod. This is only called by the {@link GrpcServiceBuilder}.
     *
     * @param serviceName a non-null reference to the service name
     * @param methodName a non-null reference to the method name
     * @param workflow a non-null {@link QueryWorkflow}
     */
    QueryMethod(
            @NonNull final String serviceName,
            @NonNull final String methodName,
            @NonNull final QueryWorkflow workflow,
            @NonNull final Metrics metrics) {
        super(serviceName, methodName, metrics);
        this.workflow = Objects.requireNonNull(workflow);

        this.queriesAnsweredCounter =
                counter(metrics, COUNTER_ANSWERED_NAME_TPL, COUNTER_ANSWERED_DESC_TPL);
        this.queriesAnsweredSpeedometer =
                speedometer(metrics, SPEEDOMETER_ANSWERED_NAME_TPL, SPEEDOMETER_ANSWERED_DESC_TPL);
    }

    @Override
    protected void handle(
            @NonNull final SessionContext session,
            @NonNull final ByteBuffer requestBuffer,
            @NonNull final ByteBuffer responseBuffer) {
        workflow.handleQuery(session, requestBuffer, responseBuffer);
        queriesAnsweredCounter.increment();
        queriesAnsweredSpeedometer.cycle();
    }
}
