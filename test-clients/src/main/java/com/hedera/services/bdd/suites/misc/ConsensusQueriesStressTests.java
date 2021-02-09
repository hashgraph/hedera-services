package com.hedera.services.bdd.suites.misc;

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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static java.util.concurrent.TimeUnit.SECONDS;

public class ConsensusQueriesStressTests extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ConsensusQueriesStressTests.class);

	private AtomicLong duration = new AtomicLong(30);
	private AtomicReference<TimeUnit> unit = new AtomicReference<>(SECONDS);
	private AtomicInteger maxOpsPerSec = new AtomicInteger(100);

	public static void main(String... args) {
		new ConsensusQueriesStressTests().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						getTopicInfoStress(),
				}
		);
	}

	private HapiApiSpec getTopicInfoStress() {
		return defaultHapiSpec("GetTopicInfoStress")
				.given().when().then(
						withOpContext((spec, opLog) -> configureFromCi(spec)),
						runWithProvider(getTopicInfoFactory())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get)
				);
	}

	private Function<HapiApiSpec, OpProvider> getTopicInfoFactory() {
		var memo = "General interest only.";

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return List.of(
						createTopic("about").topicMemo(memo)
				);
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				return Optional.of(getTopicInfo("about")
						.noLogging()
						.hasMemo(memo));
			}
		};
	}

	private void configureFromCi(HapiApiSpec spec) {
		HapiPropertySource ciProps = spec.setup().ciPropertiesMap();
		configure("duration", duration::set, ciProps, ciProps::getLong);
		configure("unit", unit::set, ciProps, ciProps::getTimeUnit);
		configure("maxOpsPerSec", maxOpsPerSec::set, ciProps, ciProps::getInteger);
	}

	private <T> void configure(
			String name,
			Consumer<T> configurer,
			HapiPropertySource ciProps,
			Function<String, T> getter
	) {
		if (ciProps.has(name)) {
			configurer.accept(getter.apply(name));
		}
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
