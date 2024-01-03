/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.fees.calculation.meta.queries;

import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_QUERY_HEADER;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_QUERY_RES_HEADER;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_TX_ID_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.FEE_MATRICES_CONST;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.LONG_SIZE;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.calculation.QueryResourceUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class GetExecTimeResourceUsage implements QueryResourceUsageEstimator {
    @Inject
    public GetExecTimeResourceUsage() {
        /* No-op */
    }

    @Override
    public boolean applicableTo(final Query query) {
        return query.hasNetworkGetExecutionTime();
    }

    @Override
    public FeeData usageGiven(final Query query, final StateView view, final Map<String, Object> ignoreCtx) {
        final var op = query.getNetworkGetExecutionTime();
        final var n = op.getTransactionIdsCount();
        final var nodeUsage = FeeComponents.newBuilder()
                .setConstant(FEE_MATRICES_CONST)
                .setBpt(BASIC_QUERY_HEADER + n * BASIC_TX_ID_SIZE)
                .setBpr(BASIC_QUERY_RES_HEADER + n * LONG_SIZE)
                .build();
        return FeeData.newBuilder().setNodedata(nodeUsage).build();
    }

    /**
     * This method is used to calculate the fee for the {@code NetworkGetExecutionTime}
     * query in modularized code only.
     * @param query query to be processed
     * @return the fee for the query
     */
    public FeeData usageGiven(@NonNull final com.hedera.hapi.node.transaction.Query query) {
        requireNonNull(query);
        return usageGiven(fromPbj(query), null, null);
    }
}
