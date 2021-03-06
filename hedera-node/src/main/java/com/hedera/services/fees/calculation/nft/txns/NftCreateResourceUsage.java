package com.hedera.services.fees.calculation.nft.txns;

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
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.SigValueObj;

public class NftCreateResourceUsage implements TxnResourceUsageEstimator {
	private static final FeeData MOCK_USAGE;
	static {
		var usagesBuilder = FeeData.newBuilder();
		usagesBuilder.setNetworkdata(FeeComponents.newBuilder()
				.setConstant(1).setBpt(256).setVpt(2).setRbh(2160));
		usagesBuilder.setNodedata(FeeComponents.newBuilder()
				.setConstant(1).setBpt(256).setVpt(1).setBpr(32));
		usagesBuilder.setServicedata(FeeComponents.newBuilder()
				.setConstant(1).setRbh(2160));
		MOCK_USAGE = usagesBuilder.build();
	}

	@Override
	public boolean applicableTo(TransactionBody txn) {
		return txn.hasNftCreate();
	}

	@Override
	public FeeData usageGiven(TransactionBody txn, SigValueObj svo, StateView view) throws InvalidTxBodyException {
		return MOCK_USAGE;
	}
}
