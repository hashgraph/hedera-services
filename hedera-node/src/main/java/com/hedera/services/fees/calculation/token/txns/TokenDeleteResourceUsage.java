package com.hedera.services.fees.calculation.token.txns;

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
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.fees.calculation.UsageEstimatorUtils;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.SigValueObj;

public class TokenDeleteResourceUsage implements TxnResourceUsageEstimator {
	public static final FeeData MOCK_TOKEN_DELETE_USAGE = UsageEstimatorUtils.defaultPartitioning(
			FeeComponents.newBuilder()
					.setMin(1)
					.setMax(1_000_000)
					.setConstant(5)
					.setBpt(5)
					.setVpt(5)
					.setRbh(5)
					.setSbh(5)
					.setGas(5)
					.setTv(5)
					.setBpr(5)
					.setSbpr(5)
					.build(), 5);

	@Override
	public boolean applicableTo(TransactionBody txn) {
		return txn.hasTokenDeletion();
	}

	@Override
	public FeeData usageGiven(TransactionBody txn, SigValueObj sigUsage, StateView view) throws InvalidTxBodyException {
		return MOCK_TOKEN_DELETE_USAGE;
	}
}
