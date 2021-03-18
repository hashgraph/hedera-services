package com.hedera.services.fees.calculation.schedule.queries;

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
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.usage.schedule.ExtantScheduleContext;
import com.hedera.services.usage.schedule.ScheduleOpsUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Optional;

import static com.hedera.services.queries.AnswerService.NO_QUERY_CTX;
import static com.hedera.services.queries.schedule.GetScheduleInfoAnswer.SCHEDULE_INFO_CTX_KEY;

public class GetScheduleInfoResourceUsage implements QueryResourceUsageEstimator {
	private static final Logger log = LogManager.getLogger(GetScheduleInfoResourceUsage.class);

	private final ScheduleOpsUsage scheduleOpsUsage;

	public GetScheduleInfoResourceUsage(ScheduleOpsUsage scheduleOpsUsage) {
		this.scheduleOpsUsage = scheduleOpsUsage;
	}

	@Override
	public boolean applicableTo(Query query) {
		return query.hasScheduleGetInfo();
	}

	@Override
	public FeeData usageGiven(Query query, StateView view) {
		return usageFor(query, view, NO_QUERY_CTX);
	}

	@Override
	public FeeData usageGivenType(Query query, StateView view, ResponseType type) {
		return usageFor(query, view, NO_QUERY_CTX);
	}

	@Override
	public FeeData usageGiven(Query query, StateView view, Map<String, Object> queryCtx) {
		return usageFor(
				query,
				view,
				Optional.of(queryCtx));
	}

	private FeeData usageFor(Query query, StateView view, Optional<Map<String, Object>> queryCtx) {
		var op = query.getScheduleGetInfo();
		var optionalInfo = view.infoForSchedule(op.getScheduleID());
		if (optionalInfo.isPresent()) {
			var info = optionalInfo.get();
			queryCtx.ifPresent(ctx -> ctx.put(SCHEDULE_INFO_CTX_KEY, info));
			var scheduleCtxBuilder = ExtantScheduleContext.newBuilder()
					.setScheduledTxn(info.getScheduledTransactionBody())
					.setMemo(info.getMemo())
					.setNumSigners(info.getSigners().getKeysCount())
					.setResolved(info.hasExpirationTime() || info.hasDeletionTime());
			if (info.hasAdminKey()) {
				scheduleCtxBuilder.setAdminKey(info.getAdminKey());
			} else {
				scheduleCtxBuilder.setNoAdminKey();
			}
			return scheduleOpsUsage.scheduleInfoUsage(query, scheduleCtxBuilder.build());
		} else {
			return FeeData.getDefaultInstance();
		}
	}
}
