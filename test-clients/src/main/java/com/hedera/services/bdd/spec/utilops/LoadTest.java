package com.hedera.services.bdd.spec.utilops;

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
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runLoadTest;
import static java.util.concurrent.TimeUnit.MINUTES;

public class LoadTest extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(LoadTest.class);

	public static OptionalDouble targetTPS = OptionalDouble.empty();
	public static OptionalInt testDurationMinutes = OptionalInt.empty();
	public static OptionalInt threadNumber = OptionalInt.empty();
	/** initial balance of payer account used for paying for performance test transactions*/
	public static OptionalLong initialBalance = OptionalLong.of(5_000_000_000L);

	public static int parseArgs(String... args) {
		int usedArgs = 0;
		if (args.length > 0) {
			targetTPS = OptionalDouble.of(Double.parseDouble(args[0]));
			log.info("Set targetTPS as " + targetTPS.getAsDouble());
			usedArgs++;
		}

		if (args.length > 1) {
			testDurationMinutes = OptionalInt.of(Integer.parseInt(args[1]));
			log.info("Set testDurationMinutes as " + testDurationMinutes.getAsInt());
			usedArgs++;
		}

		if (args.length > 2) {
			threadNumber = OptionalInt.of(Integer.parseInt(args[2]));
			log.info("Set threadNumber as " + threadNumber.getAsInt());
			usedArgs++;
		}

		if (args.length > 3) {
			initialBalance = OptionalLong.of(Long.parseLong(args[3]));
			log.info("Set initialBalance as " + initialBalance.getAsLong());
			usedArgs++;
		}

		return usedArgs;
	}

	public static double getTargetTPS() {
		return targetTPS.getAsDouble();
	}

	public static int getTestDurationMinutes() {
		return testDurationMinutes.getAsInt();
	}

	public static RunLoadTest defaultLoadTest(Supplier<HapiSpecOperation[]> opSource, PerfTestLoadSettings settings) {
		return runLoadTest(opSource)
				.tps(targetTPS.isPresent() ? LoadTest::getTargetTPS : settings::getTps)
				.tolerance(settings::getTolerancePercentage)
				.allowedSecsBelow(settings::getAllowedSecsBelow)
				.setNumberOfThreads(threadNumber.isPresent() ? threadNumber.getAsInt() : 1)
				.lasting(
						(testDurationMinutes.isPresent() ?
								LoadTest::getTestDurationMinutes :
								settings::getMins), () -> MINUTES);
	}

	@Override
	protected Logger getResultsLogger() {
		return null;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return null;
	}
}
