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
import com.hedera.node.app.SessionContext;
import com.hedera.node.app.workflows.ingest.IngestWorkflow;
import com.hedera.pbj.runtime.io.DataBuffer;
import com.hedera.pbj.runtime.io.DataInputBuffer;
import com.swirlds.common.metrics.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Handles gRPC duties for processing {@link Transaction} gRPC calls. A single instance of this
 * class is used by all transaction ingest threads in the node.
 *
 * <p>FUTURE WORK: ThreadSafe annotation missing in spotbugs annotations but should be added to
 * class
 */
/*@ThreadSafe*/
final class TransactionMethod extends MethodBase {
    /** The pipeline contains all the steps needed for handling the ingestion of a transaction. */
    private final IngestWorkflow workflow;

    /**
     * @param serviceName a non-null reference to the service name
     * @param methodName a non-null reference to the method name
     * @param workflow a non-null {@link IngestWorkflow}
     */
    TransactionMethod(
            @NonNull final String serviceName,
            @NonNull final String methodName,
            @NonNull final IngestWorkflow workflow,
            @NonNull final Metrics metrics) {
        super(serviceName, methodName, metrics);
        this.workflow = Objects.requireNonNull(workflow);
    }

    @Override
    protected void handle(
            @NonNull final SessionContext session,
            @NonNull final DataInputBuffer requestBuffer,
            @NonNull final DataBuffer responseBuffer) {
        workflow.submitTransaction(session, requestBuffer, responseBuffer);
    }
}
