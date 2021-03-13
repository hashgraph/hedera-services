package com.hedera.services.usage.schedule;

/*
 * ‌
 * Hedera Services API Fees
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BOOL_SIZE;

public class ScheduleSignUsage extends ScheduleTxnUsage<ScheduleSignUsage> {
	private long expiry;

	private ScheduleSignUsage(TransactionBody scheduleSignOp, TxnUsageEstimator usageEstimator) {
		super(scheduleSignOp, usageEstimator);
	}

	public static ScheduleSignUsage newEstimate(TransactionBody scheduleSignOp, SigUsage sigUsage) {
		return new ScheduleSignUsage(scheduleSignOp, estimatorFactory.get(sigUsage, scheduleSignOp, ESTIMATOR_UTILS));
	}

	public ScheduleSignUsage givenExpiry(long expiry) {
		this.expiry = expiry;
		return self();
	}

	@Override
	ScheduleSignUsage self() {
		return this;
	}

	/**
	 * Use a sensible estimate for how much signing activity was related to the scheduled transaction.
	 *
	 * At least one signing key is assumed to be added to the schedule; if the {@code SignatureMap} contains
	 * signatures in excess of the payer's key count, they are all assumed to result in new signing keys.
	 *
	 * @return the estimated usage for the {@code ScheduleSign}
	 */
	public FeeData get() {
		var sigUsage = usageEstimator.getSigUsage();

		var estNewSigners = scheduleEntitySizes.estimatedScheduleSigs(sigUsage);
		long lifetime = ESTIMATOR_UTILS.relativeLifetime(this.op, this.expiry);
		usageEstimator.addRbs(scheduleEntitySizes.sigBytesForAddingSigningKeys(estNewSigners) * lifetime);

		usageEstimator.addBpt(BASIC_ENTITY_ID_SIZE);

		addNetworkRecordRb(BASIC_TX_ID_SIZE + BOOL_SIZE);
		return usageEstimator.get();
	}
}
