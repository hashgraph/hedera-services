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
package com.hedera.services.sysfiles.domain.throttling;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.services.TestUtils;
import com.hedera.services.throttles.ConcurrentThrottleTestHelper;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.io.IOException;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ThrottleBucketTest {
    @Test
    void beanMethodsWork() {
        final var subject = new ThrottleBucket<HederaFunctionality>();

        subject.setBurstPeriod(123);
        subject.setBurstPeriodMs(123L);
        subject.setName("Thom");

        assertEquals(123, subject.getBurstPeriod());
        assertEquals(123L, subject.getBurstPeriodMs());
        assertEquals("Thom", subject.getName());
    }

    @Test
    void burstPeriodAutoScalingWorks() throws IOException {
        final var bucket = bucketFrom("bootstrap/auto-scale-exercise.json");

        assertEquals(5_000, bucket.autoScaledBurstPeriodMs(2));
        assertEquals(10_000L, bucket.autoScaledBurstPeriodMs(8));
        assertEquals(20_000L, bucket.autoScaledBurstPeriodMs(16));
        assertEquals(30_000L, bucket.autoScaledBurstPeriodMs(24));
        assertEquals(31_250L, bucket.autoScaledBurstPeriodMs(25));
    }

    @Test
    void roundsUpToEnsureMinBurstPeriodIfRequired() {
        assertEquals(5, ThrottleBucket.quotientRoundedUp(35, 7));
        assertEquals(6, ThrottleBucket.quotientRoundedUp(36, 7));
    }

    @ParameterizedTest
    @CsvSource({
        "2, bootstrap/insufficient-capacity-throttles.json",
        "1, bootstrap/undersupplied-throttles.json",
        "1, bootstrap/overflow-throttles.json",
        "1, bootstrap/repeated-op-throttles.json"
    })
    void failsWhenConstructingThrottlesThatNeverPermitAnOperationAtNodeLevel(
            final int networkSize, final String path) throws IOException {
        final var subject = bucketFrom(path);

        assertThrows(IllegalStateException.class, () -> subject.asThrottleMapping(networkSize));
    }

    @Test
    void failsWhenConstructingThrottlesWithZeroGroups() {
        final var subject = new ThrottleBucket();

        assertThrows(IllegalStateException.class, () -> subject.asThrottleMapping(1));
    }

    @ParameterizedTest
    @CsvSource({
        "1, bootstrap/throttles.json",
        "1, bootstrap/throttles-repeating.json",
        "24, bootstrap/throttles.json"
    })
    void constructsExpectedBucketMapping(final int networkSize, final String path)
            throws IOException {
        final var subject = bucketFrom(path);

        /* Bucket A includes groups with opsPerSec of 12, 3000, and 10_000 so the
        logical operations are, respectively, 30_000 / 12 = 2500, 30_000 / 3_000 = 10,
        and 30_000 / 10_000 = 3. */
        final var expectedThrottle =
                DeterministicThrottle.withTpsAndBurstPeriod(30_000 / networkSize, 2);
        final var expectedReqs =
                List.of(
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
        throttle.resetUsageTo(
                new DeterministicThrottle.UsageSnapshot(
                        throttle.capacity() - DeterministicThrottle.capacityRequiredFor(opsForXfer),
                        null));

        final var helper = new ConcurrentThrottleTestHelper(3, 10, opsForXfer);
        helper.runWith(throttle);

        helper.assertTolerableTps(expectedXferTps, 1.00, opsForXfer);
    }

    private static ThrottleBucket<HederaFunctionality> bucketFrom(final String path)
            throws IOException {
        final var defs = TestUtils.pojoDefs(path);
        return defs.getBuckets().get(0);
    }

    private static int opsForFunction(
            final List<Pair<HederaFunctionality, Integer>> source,
            final HederaFunctionality function) {
        for (final var pair : source) {
            if (pair.getLeft() == function) {
                return pair.getRight();
            }
        }
        Assertions.fail("Function " + function + " was missing!");
        return 0;
    }
}
