package com.hedera.services.usage.schedule;/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

public class ScheduleSignUsage extends ScheduleTxnUsage<ScheduleSignUsage> {

	private ScheduleSignUsage(TransactionBody scheduleSignOp, TxnUsageEstimator usageEstimator, int txExpirationTimeSecs) {
		super(scheduleSignOp, usageEstimator, txExpirationTimeSecs);
	}

	public static ScheduleSignUsage newEstimate(TransactionBody scheduleSignOp, SigUsage sigUsage, int txExpirationTimeSecs) {
		return new ScheduleSignUsage(scheduleSignOp, estimatorFactory.get(sigUsage, scheduleSignOp, ESTIMATOR_UTILS), txExpirationTimeSecs);
	}

	@Override
	ScheduleSignUsage self() {
		return this;
	}

	public FeeData get() {
		var op = this.op.getScheduleSign();

		var txnBytes = BASIC_ENTITY_ID_SIZE;
		var ramBytes = 0;
		if (op.hasSigMap()) {
			txnBytes += scheduleEntitySizes.bptScheduleReprGiven(op.getSigMap());
			ramBytes += scheduleEntitySizes.sigBytesInScheduleReprGiven(op.getSigMap());
		}
		usageEstimator.addBpt(txnBytes);
		usageEstimator.addRbs(ramBytes * this.expirationTimeSecs); // TODO: Here we are assuming the worst case for rb/s. It must be the delta if we want to optimise

		return usageEstimator.get();
	}
}
