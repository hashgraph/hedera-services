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

import java.io.IOException;
import java.util.List;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ThrottleBucketTest {
	@Test
	void beanMethodsWork() {
		var subject = new ThrottleBucket();

		// when:
		subject.setBurstPeriod(123);
		subject.setName("Thom");

		// then:
		assertEquals(123, subject.getBurstPeriod());
		assertEquals("Thom", subject.getName());
	}

	@Test
	void factoryWorks() throws IOException {
		// given:
		var proto = TestUtils.protoDefs("bootstrap/throttles.json");

		// setup:
		var bucketA = proto.getThrottleBuckets(0);

		// expect:
		assertEquals(bucketA, ThrottleBucket.fromProto(bucketA).toProto());
	}

	@Test
	void failsWhenConstructingThrottlesThatNeverPermitAnOperationAtNodeLevel() throws IOException {
		// setup:
		int n = 24;
		var defs = TestUtils.pojoDefs("bootstrap/overdone-throttles.json");
		// and:
		var subject = defs.getBuckets().get(0);

		// expect:
		Assertions.assertThrows(IllegalStateException.class, () -> subject.asThrottleMapping(n));
	}

	@Test
	void failsWhenConstructingThrottlesWithZeroGroups() {
		// expect:
		Assertions.assertThrows(IllegalStateException.class, () -> new ThrottleBucket().asThrottleMapping(1));
	}

	@Test
	void failsWhenConstructingThrottlesWithZeroOpsPerSecForAGroup() throws IOException {
		// setup:
		int n = 1;
		var defs = TestUtils.pojoDefs("bootstrap/undersupplied-throttles.json");
		// and:
		var subject = defs.getBuckets().get(0);

		// expect:
		Assertions.assertThrows(IllegalStateException.class, () -> subject.asThrottleMapping(n));
	}

	@Test
	void constructsExpectedABucketMappingForGlobalThrottle() throws IOException {
		// setup:
		var defs = TestUtils.pojoDefs("bootstrap/throttles.json");
		// and:
		var subject = defs.getBuckets().get(0);

		// and:
		/* Bucket A includes groups with opsPerSec of 12, 3000, and 10_000 so the
		logical operations are, respectively, 30_000 / 12 = 2500, 30_000 / 3_000 = 10,
		and 30_000 / 10_000 = 3. */
		var expectedThrottle = DeterministicThrottle.withTpsAndBurstPeriod(30_000, 2);
		var expectedReqs = List.of(
				Pair.of(HederaFunctionality.CryptoTransfer, 3),
				Pair.of(HederaFunctionality.CryptoCreate, 3),
				Pair.of(ContractCall, 2500),
				Pair.of(HederaFunctionality.TokenMint, 10));

		// when:
		var mapping = subject.asThrottleMapping(1);
		// and:
		var actualThrottle = mapping.getLeft();
		var actualReqs = mapping.getRight();
		// then:
		assertEquals(expectedThrottle, actualThrottle);
		assertEquals(expectedReqs, actualReqs);
	}

	@Test
	void constructsExpectedABucketMappingForNetworkWith24Nodes() throws IOException {
		// setup:
		int n = 24;
		var defs = TestUtils.pojoDefs("bootstrap/throttles.json");

		// given:
		var subject = defs.getBuckets().get(0);
		// and:
		var expectedThrottle = DeterministicThrottle.withMtpsAndBurstPeriod((30_000 * 1_000) / n, 2);
		var expectedReqs = List.of(
				Pair.of(HederaFunctionality.CryptoTransfer, 3),
				Pair.of(HederaFunctionality.CryptoCreate, 3),
				Pair.of(ContractCall, 2500),
				Pair.of(HederaFunctionality.TokenMint, 10));

		// when:
		var mapping = subject.asThrottleMapping(n);
		// and:
		var actualThrottle = mapping.getLeft();
		var actualReqs = mapping.getRight();
		// then:
		assertEquals(expectedThrottle, actualThrottle);
		assertEquals(expectedReqs, actualReqs);
	}

	@Test
	void constructedThrottleWorksAsExpected() throws InterruptedException, IOException {
		// setup:
		var defs = TestUtils.pojoDefs("bootstrap/throttles.json");

		// given:
		var subject = defs.getBuckets().get(0);
		int n = 14;
		double expectedXferTps = (1.0 * subject.getThrottleGroups().get(0).getOpsPerSec()) / n;
		// and:
		var mapping = subject.asThrottleMapping(n);
		var throttle = mapping.getLeft();
		int opsForXfer = opsForFunction(mapping.getRight(), CryptoTransfer);
		throttle.resetUsageTo(new DeterministicThrottle.UsageSnapshot(
				throttle.capacity() - DeterministicThrottle.capacityRequiredFor(opsForXfer),
				null));

		// when:
		var helper = new ConcurrentThrottleTestHelper(3, 10, opsForXfer);
		// and:
		helper.runWith(throttle);

		// then:
		helper.assertTolerableTps(expectedXferTps, 1.00, opsForXfer);
	}

	private int opsForFunction(List<Pair<HederaFunctionality, Integer>> source, HederaFunctionality function) {
		for (var pair : source)	 {
			if (pair.getLeft() == function) {
				return pair.getRight();
			}
		}
		Assertions.fail("Function " + function + " was missing!");
		return 0;
	}

	@Test
	void throwOnBucketWithOverflowingLogicalOps() throws IOException {
		// setup:
		var defs = TestUtils.pojoDefs("bootstrap/overflow-throttles.json");

		// given:
		var subject = defs.getBuckets().get(0);

		// expect:
		Assertions.assertThrows(IllegalStateException.class, () -> subject.asThrottleMapping(1));
	}

	@Test
	void throwOnBucketWithRepeatedOp() throws IOException {
		// setup:
		var defs = TestUtils.pojoDefs("bootstrap/repeated-op-throttles.json");

		// given:
		var subject = defs.getBuckets().get(0);

		// expect:
		Assertions.assertThrows(IllegalStateException.class, () -> subject.asThrottleMapping(1));
	}
}
