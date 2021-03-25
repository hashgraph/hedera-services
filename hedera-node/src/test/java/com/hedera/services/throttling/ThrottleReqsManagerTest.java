package com.hedera.services.throttling;

import com.hedera.services.throttling.real.BucketThrottle;
import com.hedera.services.throttling.real.DeterministicThrottle;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ThrottleReqsManagerTest {
	int aReq = 1, bReq = 51;
	int aTps = 2, bTps = 100;
	int aBurstPeriod = 1, bBurstPeriod = 1;
	long nanosSinceLastDecision = 100L;
	Instant lastDecision = Instant.ofEpochSecond(1_234_567L, 0);
	Instant now = lastDecision.plusNanos(nanosSinceLastDecision);

	DeterministicThrottle a, b;
	ThrottleReqsManager subject;

	@BeforeEach
	void setUp() {
		a = DeterministicThrottle.withTpsAndBurstPeriod(aTps, aBurstPeriod);
		b = DeterministicThrottle.withTpsAndBurstPeriod(bTps, bBurstPeriod);

		subject = new ThrottleReqsManager(List.of(Pair.of(a, aReq), Pair.of(b, bReq)));
	}

	@Test
	void usesExpectedCapacityWithAllReqsMet() {
		// when:
		var result = subject.allReqsMetAt(now);

		// then:
		assertTrue(result);
		// and:
		assertEquals(aReq * BucketThrottle.capacityUnitsPerTxn(), a.usageSnapshot().used());
		assertEquals(bReq * BucketThrottle.capacityUnitsPerTxn(), b.usageSnapshot().used());
	}

	@Test
	void usesExpectedCapacityWithOnlyOneReqMet() {
		// given:
		a.allow(aReq, lastDecision);
		b.allow(bReq, lastDecision);

		// when:
		var result = subject.allReqsMetAt(now);

		// then:
		assertFalse(result);
		// and:
		assertEquals(
				aReq * BucketThrottle.capacityUnitsPerTxn() - nanosSinceLastDecision * aTps * 1_000,
				a.usageSnapshot().used());
		assertEquals(bReq * BucketThrottle.capacityUnitsPerTxn() - nanosSinceLastDecision * bTps * 1_000,
				b.usageSnapshot().used());
	}

	@Test
	void getsExpectedUsage() {
		// setup:
		var emptyAUsage = a.usageSnapshot();
		var emptyBUsage = b.usageSnapshot();

		// given:
		a.allow(aReq, lastDecision);
		b.allow(bReq, lastDecision);

		// when:
		var usages = subject.currentUsage();
		// and:
		var aUsage = usages.get(0);
		var bUsage = usages.get(1);

		// then:
		assertEquals(lastDecision, aUsage.lastDecisionTime());
		assertEquals(lastDecision, bUsage.lastDecisionTime());
		// and:
		assertEquals(emptyAUsage.capacity(), aUsage.capacity());
		assertEquals(emptyBUsage.capacity(), bUsage.capacity());
		// and:
		assertEquals(aReq * BucketThrottle.capacityUnitsPerTxn(), aUsage.used());
		assertEquals(bReq * BucketThrottle.capacityUnitsPerTxn(), bUsage.used());
	}
}