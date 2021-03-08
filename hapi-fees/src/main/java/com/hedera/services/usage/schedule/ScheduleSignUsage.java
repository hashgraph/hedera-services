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
	private int nonceBytes;
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

	public ScheduleSignUsage givenNonceBytes(int nonceBytes) {
		this.nonceBytes = nonceBytes;
		return self();
	}

	@Override
	ScheduleSignUsage self() {
		return this;
	}

	public FeeData get() {
		var op = this.op.getScheduleSign();

		var txnBytes = BASIC_ENTITY_ID_SIZE;
		var ramBytes = 0;
		var scheduledTxSigs = 0;
		if (op.hasSigMap()) {
			txnBytes += scheduleEntitySizes.bptScheduleReprGiven(op.getSigMap());
			ramBytes += scheduleEntitySizes.sigBytesInScheduleReprGiven(op.getSigMap());
			scheduledTxSigs += op.getSigMap().getSigPairCount();
		}
		long lifetime = ESTIMATOR_UTILS.relativeLifetime(this.op, this.expiry);
		usageEstimator.addBpt(txnBytes);
		usageEstimator.addRbs(ramBytes * lifetime);
		usageEstimator.addVpt(scheduledTxSigs);

		/* A ScheduleSign record includes the TransactionID of the associated
		scheduled transaction (which always has scheduled = true). */
		addNetworkRecordRb(BASIC_TX_ID_SIZE + BOOL_SIZE + nonceBytes);
		return usageEstimator.get();
	}
}
