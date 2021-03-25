package com.hedera.services.throttling.bootstrap;

import com.hedera.services.throttling.real.DeterministicThrottle;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hedera.services.throttling.bootstrap.ThrottlesJsonToProtoSerde.loadPojoDefs;
import static com.hedera.services.throttling.bootstrap.ThrottlesJsonToProtoSerde.loadProtoDefs;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;

class ThrottleBucketTest {
	@Test
	void factoryWorks() {
		// given:
		var proto = loadProtoDefs("bootstrap/throttles.json");

		// setup:
		var bucketA = proto.getThrottleBuckets(0);

		// expect:
		Assertions.assertEquals(bucketA, ThrottleBucket.fromProto(bucketA).toProto());
	}

	@Test
	void failsWhenConstructingThrottlesThatNeverPermitAnOperationAtNodeLevel() {
		// setup:
		int n = 24;
		var defs = loadPojoDefs("bootstrap/overdone-throttles.json");
		// and:
		var subject = defs.getBuckets().get(0);

		// expect:
		Assertions.assertThrows(IllegalStateException.class, () -> subject.asThrottleMapping(n));
	}

	@Test
	void failsWhenConstructingThrottlesWithZeroOpsPerSecForAGroup() {
		// setup:
		int n = 1;
		var defs = loadPojoDefs("bootstrap/undersupplied-throttles.json");
		// and:
		var subject = defs.getBuckets().get(0);

		try {
			subject.asThrottleMapping(n);
		} catch (Exception e) {
			e.printStackTrace();
		}

		// expect:
		Assertions.assertThrows(IllegalStateException.class, () -> subject.asThrottleMapping(n));
	}

	@Test
	void constructsExpectedABucketMappingForGlobalThrottle() {
		// setup:
		var defs = loadPojoDefs("bootstrap/throttles.json");
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
		Assertions.assertEquals(expectedThrottle, actualThrottle);
		Assertions.assertEquals(expectedReqs, actualReqs);
	}

	@Test
	void constructsExpectedABucketMappingForNetworkWith24Nodes() {
		// setup:
		int n = 24;
		var defs = loadPojoDefs("bootstrap/throttles.json");

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
		Assertions.assertEquals(expectedThrottle, actualThrottle);
		Assertions.assertEquals(expectedReqs, actualReqs);
	}

	@Test
	@Disabled
	void constructedSubTpsThrottleWorksAsExpected() throws InterruptedException {
		// setup:
		var defs = loadPojoDefs("bootstrap/throttles.json");

		// given:
		var subject = defs.getBuckets().get(0);
		int n = 20;
		int lifetimeSecs = 60;
		double expectedContractCallTps = (1.0 * subject.getThrottleGroups().get(1).getOpsPerSec()) / n;
		// and:
		var mapping = subject.asThrottleMapping(n);
		var throttle = mapping.getLeft();
		int opsForContractCall = opsForFunction(mapping.getRight(), ContractCall);

		// when:
		var helper = new ConcurrentThrottleTestHelper(1, lifetimeSecs, opsForContractCall);
		// and:
		int numAllowed = helper.successfulAllowsWhenRunWith(throttle);

		// then:
		double approxActualTps = (1.0 * numAllowed)	/ lifetimeSecs;
		Assertions.assertEquals(expectedContractCallTps, approxActualTps, 0.00);
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
	void throwOnBucketWithOverflowingLogicalOps() {
		// setup:
		var defs = loadPojoDefs("bootstrap/overflow-throttles.json");

		// given:
		var subject = defs.getBuckets().get(0);

		// expect:
		Assertions.assertThrows(IllegalStateException.class, () -> subject.asThrottleMapping(1));
	}

	@Test
	void throwOnBucketWithRepeatedOp() {
		// setup:
		var defs = loadPojoDefs("bootstrap/repeated-op-throttles.json");

		// given:
		var subject = defs.getBuckets().get(0);

		// expect:
		Assertions.assertThrows(IllegalStateException.class, () -> subject.asThrottleMapping(1));
	}
}