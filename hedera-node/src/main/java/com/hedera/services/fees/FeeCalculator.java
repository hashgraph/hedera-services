package com.hedera.services.fees;

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
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.fee.FeeObject;
import com.hedera.services.legacy.core.jproto.JKey;

import java.time.Instant;
import java.util.Map;

/**
 * Defines a type able to calculate the fees required for various operations within Hedera Services.
 *
 * @author Michael Tinker
 */
public interface FeeCalculator {
	void init();

	long activeGasPriceInTinybars();
	long estimatedGasPriceInTinybars(HederaFunctionality function, Timestamp at);
	long estimatedNonFeePayerAdjustments(SignedTxnAccessor accessor, Timestamp at);
	FeeObject computeFee(SignedTxnAccessor accessor, JKey payerKey, StateView view);
	FeeObject estimateFee(SignedTxnAccessor accessor, JKey payerKey, StateView view, Timestamp at);
	FeeObject estimatePayment(Query query, FeeData usagePrices, StateView view, Timestamp at, ResponseType type);
	FeeObject computePayment(
			Query query,
			FeeData usagePrices,
			StateView view,
			Timestamp at,
			Map<String, Object> queryCtx);
}
