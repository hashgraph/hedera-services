package com.hedera.services.usage.schedule;

/*-
 * ‌
 * Hedera Services API Fees
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

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.QueryUsage;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Function;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.usage.schedule.entities.ScheduleEntitySizes.SCHEDULE_ENTITY_SIZES;
import static com.hederahashgraph.api.proto.java.SubType.SCHEDULE_CREATE_CONTRACT_CALL;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_RICH_INSTANT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BOOL_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.getAccountKeyStorageSize;

@Singleton
public class ScheduleOpsUsage {
	/* Scheduled transaction ids have the scheduled=true flag set */
	private static final long SCHEDULED_TXN_ID_SIZE = (1L * BASIC_TX_ID_SIZE) + BOOL_SIZE;

	@VisibleForTesting
	EstimatorFactory txnEstimateFactory = TxnUsageEstimator::new;
	@VisibleForTesting
	Function<ResponseType, QueryUsage> queryEstimateFactory = QueryUsage::new;

	@Inject
	public ScheduleOpsUsage() {
		// Default constructor
	}

	public FeeData scheduleInfoUsage(Query scheduleInfo, ExtantScheduleContext ctx) {
		var op = scheduleInfo.getScheduleGetInfo();

		var estimate = queryEstimateFactory.apply(op.getHeader().getResponseType());
		estimate.addTb(BASIC_ENTITY_ID_SIZE);
		estimate.addRb(ctx.nonBaseRb());

		return estimate.get();
	}

	public FeeData scheduleCreateUsage(TransactionBody scheduleCreate, SigUsage sigUsage, long lifetimeSecs) {
		var op = scheduleCreate.getScheduleCreate();

		var scheduledTxn = op.getScheduledTransactionBody();
		long msgBytesUsed = (long) scheduledTxn.getSerializedSize() + op.getMemoBytes().size();
		if (op.hasPayerAccountID()) {
			msgBytesUsed += BASIC_ENTITY_ID_SIZE;
		}

		var creationCtx = ExtantScheduleContext.newBuilder()
				.setScheduledTxn(scheduledTxn)
				.setNumSigners(SCHEDULE_ENTITY_SIZES.estimatedScheduleSigs(sigUsage))
				.setMemo(op.getMemo())
				.setResolved(false);
		if (op.hasAdminKey()) {
			var adminKey = op.getAdminKey();
			msgBytesUsed += getAccountKeyStorageSize(adminKey);
			creationCtx.setAdminKey(adminKey);
		} else {
			creationCtx.setNoAdminKey();
		}

		var estimate = txnEstimateFactory.get(sigUsage, scheduleCreate, ESTIMATOR_UTILS);
		estimate.addBpt(msgBytesUsed);
		estimate.addRbs(creationCtx.build().nonBaseRb() * lifetimeSecs);

		/* The receipt of a schedule create includes both the id of the created schedule
		and the transaction id to use for querying the record of the scheduled txn. */
		estimate.addNetworkRbs((BASIC_ENTITY_ID_SIZE + SCHEDULED_TXN_ID_SIZE)
				* USAGE_PROPERTIES.legacyReceiptStorageSecs());

		if (scheduledTxn.hasContractCall()) {
			return estimate.get(SCHEDULE_CREATE_CONTRACT_CALL);
		}

		return estimate.get();
	}

	public FeeData scheduleSignUsage(TransactionBody scheduleSign, SigUsage sigUsage, long scheduleExpiry) {
		var estimate = txnEstimateFactory.get(sigUsage, scheduleSign, ESTIMATOR_UTILS);

		estimate.addBpt(BASIC_ENTITY_ID_SIZE);

		int estNewSigners = SCHEDULE_ENTITY_SIZES.estimatedScheduleSigs(sigUsage);
		long lifetime = ESTIMATOR_UTILS.relativeLifetime(scheduleSign, scheduleExpiry);
		estimate.addRbs(SCHEDULE_ENTITY_SIZES.bytesUsedForSigningKeys(estNewSigners) * lifetime);

		estimate.addNetworkRbs(SCHEDULED_TXN_ID_SIZE * USAGE_PROPERTIES.legacyReceiptStorageSecs());

		return estimate.get();
	}

	public FeeData scheduleDeleteUsage(TransactionBody scheduleDelete, SigUsage sigUsage, long scheduleExpiry) {
		var estimate = txnEstimateFactory.get(sigUsage, scheduleDelete, ESTIMATOR_UTILS);

		estimate.addBpt(BASIC_ENTITY_ID_SIZE);

		long lifetime = ESTIMATOR_UTILS.relativeLifetime(scheduleDelete, scheduleExpiry);
		estimate.addRbs(BASIC_RICH_INSTANT_SIZE * lifetime);

		return estimate.get();
	}
}
