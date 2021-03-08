package com.hedera.services.bdd.suites.perf;

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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.nft.NftXchange;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.NOISY_RETRY_PRECHECKS;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.stdMgmtOf;
import static java.util.concurrent.TimeUnit.MINUTES;

public class NftXchangeLoadProvider extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(NftXchangeLoadProvider.class);

	private AtomicLong duration = new AtomicLong(Long.MAX_VALUE);
	private AtomicReference<TimeUnit> unit = new AtomicReference<>(MINUTES);
	private AtomicInteger nftTypes = new AtomicInteger();
	private AtomicInteger maxOpsPerSec = new AtomicInteger(500);

	public static void main(String... args) {
		new NftXchangeLoadProvider().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						runNftXchange(),
				}
		);
	}

	private HapiApiSpec runNftXchange() {
		return HapiApiSpec.defaultHapiSpec("RunNftXchange")
				.given(
						stdMgmtOf(duration, unit, maxOpsPerSec)
				).when(
						runWithProvider(nftXchangeFactory())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get)
				).then(
						withOpContext((spec, opLog) -> {
							for (int i = 0, n = nftTypes.get(); i < n; i++) {
								allRunFor(
										spec,
										getAccountBalance(NftXchange.treasury(i)).logged());
							}
						})
				);
	}

	private Function<HapiApiSpec, OpProvider> nftXchangeFactory() {
		var serialNos = new AtomicInteger();
		var nextNftType = new AtomicInteger(0);
		List<NftXchange> xchanges = new ArrayList<>();

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				var ciProps = spec.setup().ciPropertiesMap();
				nftTypes.set(ciProps.getInteger("nftTypes"));
				serialNos.set(ciProps.getInteger("serialNos"));
				boolean swapHbar = ciProps.getBoolean("swapHbar");

				List<HapiSpecOperation> initializers = new ArrayList<>();
				for (int i = 0, n = nftTypes.get(); i < n; i++) {
//					var xchange = new NftXchange(i, serialNos.get(), swapHbar);
//					initializers.addAll(xchange.initializers());
//					xchanges.add(xchange);
				}

				for (HapiSpecOperation op : initializers) {
					if (op instanceof HapiTxnOp) {
						((HapiTxnOp) op).hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS);
					}
				}

				return initializers;
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				var nftType = nextNftType.getAndUpdate(i -> (i + 1) % nftTypes.get());
				var xchange = xchanges.get(nftType);
				return Optional.of(xchange.nextOp());
			}
		};
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
