/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.queries.meta;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.NetworkGetExecutionTime;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.queries.AbstractAnswer;
import com.hedera.node.app.service.mono.stats.ExecutionTimeTracker;
import com.hederahashgraph.api.proto.java.NetworkGetExecutionTimeResponse;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetExecTimeAnswer extends AbstractAnswer {
    private final ExecutionTimeTracker executionTimeTracker;

    @Inject
    public GetExecTimeAnswer(final ExecutionTimeTracker executionTimeTracker) {
        super(
                NetworkGetExecutionTime,
                query -> query.getNetworkGetExecutionTime().getHeader().getPayment(),
                query -> query.getNetworkGetExecutionTime().getHeader().getResponseType(),
                response ->
                        response.getNetworkGetExecutionTime()
                                .getHeader()
                                .getNodeTransactionPrecheckCode(),
                (query, view) -> OK);

        this.executionTimeTracker = executionTimeTracker;
    }

    @Override
    public Response responseGiven(
            final Query query,
            @Nullable final StateView view,
            final ResponseCodeEnum validity,
            final long cost) {
        final var op = query.getNetworkGetExecutionTime();
        final var response = NetworkGetExecutionTimeResponse.newBuilder();

        final ResponseType type = op.getHeader().getResponseType();
        if (validity != OK) {
            response.setHeader(header(validity, type, cost));
        } else {
            if (type == COST_ANSWER) {
                response.setHeader(costAnswerHeader(OK, cost));
            } else {
                boolean failed = false;
                final List<Long> ans = new ArrayList<>();
                for (final var txnId : op.getTransactionIdsList()) {
                    final var execNanos = executionTimeTracker.getExecNanosIfPresentFor(txnId);
                    if (execNanos != null) {
                        ans.add(execNanos);
                    } else {
                        response.setHeader(answerOnlyHeader(INVALID_TRANSACTION_ID));
                        failed = true;
                        break;
                    }
                }
                if (!failed) {
                    response.addAllExecutionTimes(ans);
                    response.setHeader(answerOnlyHeader(OK));
                }
            }
        }

        return Response.newBuilder().setNetworkGetExecutionTime(response).build();
    }
}
