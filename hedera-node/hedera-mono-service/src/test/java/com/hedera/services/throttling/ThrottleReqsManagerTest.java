/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.throttling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.sysfiles.domain.throttling.ThrottleReqOpsScaleFactor;
import com.hedera.services.throttles.BucketThrottle;
import com.hedera.services.throttles.DeterministicThrottle;
import java.time.Instant;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        subjectWithTps(aTps, bTps);
    }

    private void subjectWithTps(int tpsForA, int tpsForB) {
        a = DeterministicThrottle.withTpsAndBurstPeriod(tpsForA, aBurstPeriod);
        b = DeterministicThrottle.withTpsAndBurstPeriod(tpsForB, bBurstPeriod);

        subject = new ThrottleReqsManager(List.of(Pair.of(a, aReq), Pair.of(b, bReq)));
    }

    @Test
    void usesExpectedCapacityWithAllReqsAndScaleFactor() {
        // setup:
        final var numOps = 7;
        final var scaleFactor = ThrottleReqOpsScaleFactor.from("3:2");
        final var modifiedAReq = (numOps * aReq * 3) / 2;
        final var modifiedBReq = (numOps * bReq * 3) / 2;
        // and:
        subjectWithTps(20, 1000);

        // when:
        var result = subject.allReqsMetAt(now, numOps, scaleFactor);

        // then:
        assertTrue(result);
        // and:
        assertEquals(modifiedAReq * BucketThrottle.capacityUnitsPerTxn(), a.usageSnapshot().used());
        assertEquals(modifiedBReq * BucketThrottle.capacityUnitsPerTxn(), b.usageSnapshot().used());
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
                (aReq * BucketThrottle.capacityUnitsPerTxn()
                                - nanosSinceLastDecision * aTps * 1_000)
                        + (aReq * BucketThrottle.capacityUnitsPerTxn()),
                a.usageSnapshot().used());
        assertEquals(
                bReq * BucketThrottle.capacityUnitsPerTxn() - nanosSinceLastDecision * bTps * 1_000,
                b.usageSnapshot().used());
    }

    @Test
    void getsExpectedUsage() {
        // setup:
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
        assertEquals(aReq * BucketThrottle.capacityUnitsPerTxn(), aUsage.used());
        assertEquals(bReq * BucketThrottle.capacityUnitsPerTxn(), bUsage.used());
    }
}
