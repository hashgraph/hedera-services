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
import com.hedera.services.bdd.spec.infrastructure.listeners.ScheduleSignersRegistry;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.suites.HapiApiSuite.GENESIS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;

public class RandomScheduleSign implements OpProvider {
	static final Logger log = LogManager.getLogger(RandomScheduleSign.class);

	public static final int MAX_SIGNATURES_PER_OP = 1;
	public static final int DEFAULT_CEILING_NUM = 10_000;

	private int ceilingNum = DEFAULT_CEILING_NUM;

	private final RegistrySourcedNameProvider<ScheduleID> schedules;
	private final RegistrySourcedNameProvider<AccountID> accounts;
	private final RegistrySourcedNameProvider<ScheduleSignersRegistry> scheduleSigners;

	private final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd(
			SOME_SIGNATURES_WERE_INVALID, INVALID_SCHEDULE_ID
	);

	public RandomScheduleSign(
			RegistrySourcedNameProvider<ScheduleID> schedules,
			RegistrySourcedNameProvider<AccountID> accounts,
			RegistrySourcedNameProvider<ScheduleSignersRegistry> scheduleSigners
	) {
		this.schedules = schedules;
		this.accounts = accounts;
		this.scheduleSigners = scheduleSigners;
	}

	public RandomScheduleSign ceiling(int n) {
		ceilingNum = n;
		return this;
	}

	@Override
	public Optional<HapiSpecOperation> get() {
		var schedule = schedules.getQualifying();
		if (schedule.isEmpty()) {
			return Optional.empty();
		}

		var account = accounts.getQualifying();
		if (account.isEmpty()) {
			return Optional.empty();
		}

		int numOfSignaturesNeeded = BASE_RANDOM.nextInt(MAX_SIGNATURES_PER_OP) + 1;
		Set<String> chosen = new HashSet<>();
		while (numOfSignaturesNeeded-- > 0) {
			var signer = schedules.getQualifyingExcept(chosen);
			signer.ifPresent(chosen::add);
		}
		if (chosen.isEmpty()) {
			return Optional.empty();
		}
		String[] toUse = chosen.toArray(new String[0]);
		var op= scheduleSign(schedule.get())
				.logged()
				.lookingUpBytesToSign()
				.withSignatories(GENESIS)
				.hasAnyPrecheck()
				.hasKnownStatusFrom(permissibleOutcomes);

		return Optional.of(op);
	}
}
