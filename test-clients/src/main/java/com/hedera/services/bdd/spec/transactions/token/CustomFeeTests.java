package com.hedera.services.bdd.spec.transactions.token;

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
import org.junit.Assert;
import proto.CustomFeesOuterClass;

import java.util.OptionalLong;
import java.util.function.BiConsumer;

import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.builtFixedHts;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.builtFractional;

public class CustomFeeTests {
	public static BiConsumer<HapiApiSpec, CustomFeesOuterClass.CustomFees> fixedHbarFeeInSchedule(
			long amount,
			String collector
	) {
		return (spec, actual) -> {
			final var expected = CustomFeeSpecs.builtFixedHbar(amount, collector, spec);
			failUnlessPresent("fixed ℏ", actual, expected);
		};
	}

	public static BiConsumer<HapiApiSpec, CustomFeesOuterClass.CustomFees> fixedHtsFeeInSchedule(
			long amount,
			String denom,
			String collector
	) {
		return (spec, actual) -> {
			final var expected = builtFixedHts(amount, denom, collector, spec);
			failUnlessPresent("fixed HTS", actual, expected);
		};
	}

	public static BiConsumer<HapiApiSpec, CustomFeesOuterClass.CustomFees> fractionalFeeInSchedule(
			long numerator,
			long denominator,
			long min,
			OptionalLong max,
			String collector
	) {
		return (spec, actual) -> {
			final var expected = builtFractional(numerator, denominator, min, max, collector, spec);
			failUnlessPresent("fractional", actual, expected);
		};
	}

	private static void failUnlessPresent(
			String detail,
			CustomFeesOuterClass.CustomFees actual,
			CustomFeesOuterClass.CustomFee expected
	) {
		for (var customFee : actual.getCustomFeesList()) {
			if (expected.equals(customFee))	{
				return;
			}
		}
		Assert.fail("Expected a " + detail + " fee " + expected + ", but only had: " + actual.getCustomFeesList());
	}
}
