package com.hedera.services.fees.calculation.contract.queries;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hedera.services.fees.calculation.crypto.queries.GetTxnRecordResourceUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.fee.SmartContractFeeBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GetBytecodeResourceUsage implements QueryResourceUsageEstimator {
	private static final Logger log = LogManager.getLogger(GetTxnRecordResourceUsage.class);

	private static final byte[] EMPTY_BYTECODE = new byte[0];

	private final SmartContractFeeBuilder usageEstimator;

	public GetBytecodeResourceUsage(SmartContractFeeBuilder usageEstimator) {
		this.usageEstimator = usageEstimator;
	}

	@Override
	public boolean applicableTo(Query query) {
		return query.hasContractGetBytecode();
	}

	@Override
	public FeeData usageGiven(Query query, StateView view) {
		return usageGivenType(query, view, query.getContractGetBytecode().getHeader().getResponseType());
	}

	@Override
	public FeeData usageGivenType(Query query, StateView view, ResponseType type) {
		try {
			var op = query.getContractGetBytecode();
			var bytecode = view.bytecodeOf(op.getContractID()).orElse(EMPTY_BYTECODE);
			return usageEstimator.getContractByteCodeQueryFeeMatrices(bytecode.length, type);
		} catch (Exception illegal) {
			log.warn("Usage estimation unexpectedly failed for {}!", query);
			throw new IllegalArgumentException(illegal);
		}
	}
}
