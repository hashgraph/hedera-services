package com.hedera.services.fees.calculation.meta.queries;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_QUERY_HEADER;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_QUERY_RES_HEADER;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.FEE_MATRICES_CONST;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;

@Singleton
public class GetExecTimeResourceUsage implements QueryResourceUsageEstimator {
	private static final long LONG_BASIC_TX_ID_SIZE = BASIC_TX_ID_SIZE;
	private static final long LONG_LONG_SIZE = LONG_SIZE;

	@Inject
	public GetExecTimeResourceUsage() {
		/* Dagger2 */
	}

	@Override
	public boolean applicableTo(Query query) {
		return query.hasNetworkGetExecutionTime();
	}

	@Override
	public FeeData usageGiven(Query query, StateView view) {
		return usageGivenType(query, view, query.getNetworkGetExecutionTime().getHeader().getResponseType());
	}

	@Override
	public FeeData usageGivenType(Query query, StateView view, ResponseType type) {
		final var op = query.getNetworkGetExecutionTime();
		final var n = op.getTransactionIdsCount();
		final var nodeUsage = FeeComponents.newBuilder()
				.setConstant(FEE_MATRICES_CONST)
				.setBpt(BASIC_QUERY_HEADER + n * LONG_BASIC_TX_ID_SIZE)
				.setBpr(BASIC_QUERY_RES_HEADER + n * LONG_LONG_SIZE)
				.build();
		return FeeData.newBuilder()
				.setNodedata(nodeUsage)
				.build();
	}
}
