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

import com.hedera.services.usage.TxnUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hedera.services.usage.schedule.entities.ScheduleEntitySizes;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.schedule.entities.ScheduleEntitySizes.SCHEDULE_ENTITY_SIZES;

public abstract class ScheduleTxnUsage<T extends ScheduleTxnUsage<T>> extends TxnUsage {
	static ScheduleEntitySizes scheduleEntitySizes = SCHEDULE_ENTITY_SIZES;

	abstract T self();

	protected ScheduleTxnUsage(TransactionBody scheduleOp, TxnUsageEstimator usageEstimator) {
		super(scheduleOp, usageEstimator);
	}
}
