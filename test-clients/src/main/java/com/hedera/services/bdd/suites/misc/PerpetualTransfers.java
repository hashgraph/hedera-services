package com.hedera.services.bdd.suites.misc;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static java.util.concurrent.TimeUnit.MINUTES;

public class PerpetualTransfers extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(PerpetualTransfers.class);

	private AtomicLong duration = new AtomicLong(Long.MAX_VALUE);
	private AtomicReference<TimeUnit> unit = new AtomicReference<>(MINUTES);
	private AtomicInteger maxOpsPerSec = new AtomicInteger(10);

	public static void main(String... args) {
		new PerpetualTransfers().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						canTransferBackAndForthForever(),
				}
		);
	}

	private HapiApiSpec canTransferBackAndForthForever() {
		return HapiApiSpec.defaultHapiSpec("CanTransferBackAndForthForever")
				.given().when().then(
						runWithProvider(transfersFactory())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get)
				);
	}

	private Function<HapiApiSpec, OpProvider> transfersFactory() {
		AtomicBoolean fromAtoB = new AtomicBoolean(true);
		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return List.of(
						cryptoCreate("A"),
						cryptoCreate("B")
				);
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				var from = fromAtoB.get() ? "A" : "B";
				var to = fromAtoB.get() ? "B" : "A";
				fromAtoB.set(!fromAtoB.get());

				var op = cryptoTransfer(tinyBarsFromTo(from, to, 1)).deferStatusResolution();

				return Optional.of(op);
			}
		};
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}


}
