package com.hedera.services.sysfiles.domain.throttling;

/*-
 * ‌
 * Hedera Services API Utilities
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

import com.hedera.services.TestUtils;
import com.hedera.services.throttles.ConcurrentThrottleTestHelper;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.util.List;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThrottleBucketTest {
	@Test
	void beanMethodsWork() {
		final var subject = new ThrottleBucket();

		subject.setBurstPeriod(123);
		subject.setBurstPeriodMs(123L);
		subject.setName("Thom");

		assertEquals(123, subject.getBurstPeriod());
		assertEquals(123L, subject.getBurstPeriodMs());
		assertEquals("Thom", subject.getName());
	}

	@Test
	void factoryWorks() throws IOException {
		final var proto = TestUtils.protoDefs("bootstrap/throttles.json");

		final var bucketA = proto.getThrottleBuckets(0);

		assertEquals(bucketA, ThrottleBucket.fromProto(bucketA).toProto());
	}

	@ParameterizedTest
	@CsvSource({
			"2, bootstrap/insufficient-capacity-throttles.json",
			"24, bootstrap/overdone-throttles.json",
			"1, bootstrap/undersupplied-throttles.json",
			"1, bootstrap/never-true-throttles.json",
			"1, bootstrap/overflow-throttles.json",
			"1, bootstrap/repeated-op-throttles.json"
	})
	void failsWhenConstructingThrottlesThatNeverPermitAnOperationAtNodeLevel(
			final int networkSize,
			final String path
	) throws IOException {
		final var subject = bucketFrom(path);

		assertThrows(IllegalStateException.class, () -> subject.asThrottleMapping(networkSize));
	}

	@Test
	void failsWhenConstructingThrottlesWithZeroGroups() {
		assertThrows(IllegalStateException.class, () -> new ThrottleBucket().asThrottleMapping(1));
	}

	@ParameterizedTest
	@CsvSource({
			"1, bootstrap/throttles.json",
			"1, bootstrap/throttles-repeating.json",
			"24, bootstrap/throttles.json"
	})
	void constructsExpectedBucketMapping(final int networkSize, final String path) throws IOException {
		final var subject = bucketFrom(path);

		/* Bucket A includes groups with opsPerSec of 12, 3000, and 10_000 so the
		logical operations are, respectively, 30_000 / 12 = 2500, 30_000 / 3_000 = 10,
		and 30_000 / 10_000 = 3. */
		final var expectedThrottle = DeterministicThrottle.withTpsAndBurstPeriod(30_000 / networkSize, 2);
		final var expectedReqs = List.of(
				Pair.of(HederaFunctionality.CryptoTransfer, 3),
				Pair.of(HederaFunctionality.CryptoCreate, 3),
				Pair.of(ContractCall, 2500),
				Pair.of(HederaFunctionality.TokenMint, 10));

		final var mapping = subject.asThrottleMapping(networkSize);
		final var actualThrottle = mapping.getLeft();
		final var actualReqs = mapping.getRight();

		assertEquals(expectedThrottle, actualThrottle);
		assertEquals(expectedReqs, actualReqs);
	}

	@Test
	void constructedThrottleWorksAsExpected() throws InterruptedException, IOException {
		final var subject = bucketFrom("bootstrap/throttles.json");
		final var n = 14;
		final var expectedXferTps = (1.0 * subject.getThrottleGroups().get(0).getOpsPerSec()) / n;
		final var mapping = subject.asThrottleMapping(n);
		final var throttle = mapping.getLeft();
		final var opsForXfer = opsForFunction(mapping.getRight(), CryptoTransfer);
		throttle.resetUsageTo(new DeterministicThrottle.UsageSnapshot(
				throttle.capacity() - DeterministicThrottle.capacityRequiredFor(opsForXfer),
				null));

		final var helper = new ConcurrentThrottleTestHelper(3, 10, opsForXfer);
		helper.runWith(throttle);

		helper.assertTolerableTps(expectedXferTps, 1.00, opsForXfer);
	}

	private static final ThrottleBucket bucketFrom(final String path) throws IOException {
		final var defs = TestUtils.pojoDefs(path);
		return defs.getBuckets().get(0);
	}

	private static final int opsForFunction(
			final List<Pair<HederaFunctionality, Integer>> source,
			final HederaFunctionality function
	) {
		for (final var pair : source) {
			if (pair.getLeft() == function) {
				return pair.getRight();
			}
		}
		Assertions.fail("Function " + function + " was missing!");
		return 0;
	}
}
