package com.hedera.services.usage.schedule;

/*
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
import static com.hederahashgraph.fee.FeeBuilder.getAccountKeyStorageSize;

public class ScheduleCreateUsage extends ScheduleTxnUsage<ScheduleCreateUsage> {

	private ScheduleCreateUsage(TransactionBody scheduleCreationOp, TxnUsageEstimator usageEstimator, int txExpirationTimeSecs) {
		super(scheduleCreationOp, usageEstimator, txExpirationTimeSecs);
	}

	public static ScheduleCreateUsage newEstimate(TransactionBody scheduleCreationOp, SigUsage sigUsage, int txExpirationTimeSecs) {
		return new ScheduleCreateUsage(scheduleCreationOp, estimatorFactory.get(sigUsage, scheduleCreationOp, ESTIMATOR_UTILS), txExpirationTimeSecs);
	}

	@Override
	ScheduleCreateUsage self() {
		return this;
	}

	public FeeData get() {
		var op = this.op.getScheduleCreate();

		var txBytes = op.getTransactionBody().toByteArray().length;
		var ramBytes = scheduleEntitySizes.bytesInBaseReprGiven(op.getTransactionBody().toByteArray());
		if (op.hasAdminKey()) {
			long keySize = getAccountKeyStorageSize(op.getAdminKey());
			txBytes += keySize;
			ramBytes += keySize;
		}

		if (op.hasPayerAccountID()) {
			txBytes += BASIC_ENTITY_ID_SIZE;
		}

		if (op.hasSigMap()) {
			txBytes += scheduleEntitySizes.bptScheduleReprGiven(op.getSigMap());
			ramBytes += scheduleEntitySizes.sigBytesInScheduleReprGiven(op.getSigMap());
		}

		usageEstimator.addBpt(txBytes);
		usageEstimator.addRbs(ramBytes * this.expirationTimeSecs);
		addNetworkRecordRb(BASIC_ENTITY_ID_SIZE); // The newly created ScheduleID that is set in the receipt

		return usageEstimator.get();
	}
}