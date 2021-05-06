package com.hedera.services.fees;

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
import com.hedera.services.fees.calculation.AutoRenewCalcs;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.fee.FeeObject;
import com.hedera.services.legacy.core.jproto.JKey;
import org.apache.commons.lang3.tuple.Pair;

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
	long estimatedNonFeePayerAdjustments(TxnAccessor accessor, Timestamp at);
	FeeObject computeFee(TxnAccessor accessor, JKey payerKey, StateView view);
	FeeObject estimateFee(TxnAccessor accessor, JKey payerKey, StateView view, Timestamp at);
	FeeObject estimatePayment(Query query, FeeData usagePrices, StateView view, Timestamp at, ResponseType type);
	FeeObject computePayment(
			Query query,
			FeeData usagePrices,
			StateView view,
			Timestamp at,
			Map<String, Object> queryCtx);

	/**
	 * Returns a pair whose key is the longest period for which the expired account
	 * can afford to renew itself, <b>up to</b> the requested period; and whose value
	 * is the service fee to be charged for renewing the account for that period.
	 *
	 * <b>Important:</b> The key will always be a multiple of 3600, because the fee
	 * schedule uses hours as the units to price memory consumption.
	 *
	 * Also, unless the expired account's balance is exactly zero, the pair's key
	 * will always be at least 3600 (representing one hour of renewal).
	 *
	 * @param expiredAccount the expired account
	 * @param requestedRenewal the desired renewal period
	 * @param now the consensus time of expiration
	 */
	 AutoRenewCalcs.RenewAssessment assessCryptoAutoRenewal(
	 		MerkleAccount expiredAccount,
			 long requestedRenewal,
			 Instant now);
}
