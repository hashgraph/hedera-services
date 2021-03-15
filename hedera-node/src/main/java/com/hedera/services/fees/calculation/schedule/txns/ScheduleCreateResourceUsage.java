package com.hedera.services.fees.calculation.schedule.txns;

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
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.schedule.ScheduleCreateUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.SigValueObj;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.BiFunction;

public class ScheduleCreateResourceUsage implements TxnResourceUsageEstimator {
	private static final Logger log = LogManager.getLogger(ScheduleCreateResourceUsage.class);

	static BiFunction<TransactionBody, SigUsage, ScheduleCreateUsage> factory = ScheduleCreateUsage::newEstimate;

	private final GlobalDynamicProperties dynamicProperties;

	public ScheduleCreateResourceUsage(GlobalDynamicProperties dynamicProperties) {
		this.dynamicProperties = dynamicProperties;
	}

	@Override
	public boolean applicableTo(TransactionBody txn) {
		return txn.hasReplScheduleCreate();
	}

	@Override
	public FeeData usageGiven(TransactionBody txn, SigValueObj svo, StateView view) throws InvalidTxBodyException {
		var sigUsage = new SigUsage(svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());
		var usageEstimate = factory.apply(txn, sigUsage)
				.givenScheduledTxExpirationTimeSecs(dynamicProperties.scheduledTxExpiryTimeSecs())
				.givenScheduledTxn(txn.getReplScheduleCreate().getScheduledTransactionBody())
				.get();
		return usageEstimate;
	}
}
