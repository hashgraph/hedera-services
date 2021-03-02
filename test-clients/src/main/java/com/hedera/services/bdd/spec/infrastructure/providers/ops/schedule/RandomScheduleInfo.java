package com.hedera.services.bdd.spec.infrastructure.providers.ops.schedule;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;

import java.util.Optional;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

public class RandomScheduleInfo implements OpProvider {
	private final RegistrySourcedNameProvider<ScheduleID> schedules;

	private final ResponseCodeEnum[] permissibleCostAnswerPrechecks = standardQueryPrechecksAnd(
			INVALID_SCHEDULE_ID
	);
	private final ResponseCodeEnum[] permissibleAnswerOnlyPrechecks = standardQueryPrechecksAnd(
			INVALID_SCHEDULE_ID
	);
	public RandomScheduleInfo(RegistrySourcedNameProvider<ScheduleID> schedules) {
		this.schedules = schedules;
	}

	@Override
	public Optional<HapiSpecOperation> get() {
		Optional<String> schedule = schedules.getQualifying();
		if (schedule.isEmpty())	{
			return Optional.empty();
		}

		var op = getScheduleInfo(schedule.get())
				.hasCostAnswerPrecheckFrom(permissibleCostAnswerPrechecks)
				.hasAnswerOnlyPrecheckFrom(permissibleAnswerOnlyPrechecks);

		return Optional.of(op);
	}
}
