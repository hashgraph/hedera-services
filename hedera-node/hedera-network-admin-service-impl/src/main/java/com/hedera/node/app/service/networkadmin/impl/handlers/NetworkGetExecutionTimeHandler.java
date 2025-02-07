/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.networkadmin.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.network.NetworkGetExecutionTimeResponse;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.spi.workflows.FreeQueryHandler;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.app.spi.workflows.WorkflowException;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#NETWORK_GET_EXECUTION_TIME}.
 * <p>
 * This network service call has been deprecated. Because protobufs promise backwards compatibility,
 * we cannot remove it. However, it should not be used.
 */
@Singleton
public class NetworkGetExecutionTimeHandler extends FreeQueryHandler {
    @Inject
    public NetworkGetExecutionTimeHandler() {
        // Exists for injection
    }

    @Override
    public QueryHeader extractHeader(@NonNull final Query query) {
        requireNonNull(query);
        return query.networkGetExecutionTimeOrThrow().header();
    }

    @Override
    public Response createEmptyResponse(@NonNull final ResponseHeader header) {
        requireNonNull(header);
        final var response = NetworkGetExecutionTimeResponse.newBuilder().header(header);
        return Response.newBuilder().networkGetExecutionTime(response).build();
    }

    @Override
    public void validate(@NonNull final QueryContext context) {
        requireNonNull(context);
        throw new WorkflowException(NOT_SUPPORTED);
    }

    @Override
    public Response findResponse(@NonNull final QueryContext context, @NonNull final ResponseHeader header) {
        requireNonNull(context);
        requireNonNull(header);
        throw new UnsupportedOperationException("Not supported");
    }
}
