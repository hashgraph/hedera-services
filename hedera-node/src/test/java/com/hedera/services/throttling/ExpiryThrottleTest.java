/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.throttling.MapAccessType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.hedera.services.files.HybridResouceLoader;
import com.hedera.services.sysfiles.domain.throttling.ThrottleGroup;
import com.hedera.services.throttles.DeterministicThrottle;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpiryThrottleTest {
    private static final Instant pointA = Instant.ofEpochSecond(1_234_567);
    private static final String OTHER_RESOURCE_LOC = "testfiles/other-expiry-throttle.json";
    private static final String VALID_RESOURCE_LOC = "testfiles/expiry-throttle.json";
    private static final long EXPECTED_CAPACITY = 10000000000000000L;
    private static final List<MapAccessType> minReqUnitOfWork = List.of(ACCOUNTS_GET, STORAGE_PUT);

    @Mock private HybridResouceLoader resourceLoader;

    private ExpiryThrottle subject;

    @BeforeEach
    void setUp() {
        subject = new ExpiryThrottle(resourceLoader);
    }

    @Test
    void byDefaultLacksCapacity() {
        assertTrue(subject.stillLacksMinFreeCapAfterLeakingUntil(pointA));
    }

    @Test
    void getsExpectedBucketFromTestResource() throws JsonProcessingException {
        final var jsonBytes = getTestResource(VALID_RESOURCE_LOC);

        final var bucket = subject.parseJson(new String(jsonBytes));
        assertEquals("ExpiryWorkLimits", bucket.getName());
        assertEquals(1_000L, bucket.getBurstPeriodMs());
        final var groups = bucket.getThrottleGroups();
        assertEquals(4, groups.size());
        assertGroupContents(
                10_000L,
                List.of(ACCOUNTS_GET, NFTS_GET, TOKENS_GET, TOKEN_ASSOCIATIONS_GET),
                groups.get(0));
        assertGroupContents(
                1000L,
                List.of(
                        ACCOUNTS_GET_FOR_MODIFY,
                        NFTS_GET_FOR_MODIFY,
                        TOKEN_ASSOCIATIONS_GET_FOR_MODIFY,
                        ACCOUNTS_REMOVE,
                        NFTS_REMOVE,
                        TOKEN_ASSOCIATIONS_REMOVE),
                groups.get(1));
        assertGroupContents(500L, List.of(STORAGE_GET), groups.get(2));
        assertGroupContents(50L, List.of(BLOBS_REMOVE, STORAGE_PUT, STORAGE_REMOVE), groups.get(3));
    }

    @Test
    void usesResourceProvidedByLoader() {
        final var now = Instant.ofEpochSecond(1_234_567L, 890);
        final var expectedMtps = 10000000L;
        given(resourceLoader.readAllBytesIfPresent(VALID_RESOURCE_LOC))
                .willReturn(getTestResource(VALID_RESOURCE_LOC));

        subject.rebuildGiven(VALID_RESOURCE_LOC, minReqUnitOfWork);

        final var throttle = subject.getThrottle();
        assertEquals(expectedMtps, throttle.mtps());
        assertEquals(EXPECTED_CAPACITY, throttle.capacity());
        // and:
        final var expectedMinReqs = requiredOps(minReqUnitOfWork, subject.getAccessReqs());
        assertFalse(subject.stillLacksMinFreeCapAfterLeakingUntil(now));
        // and:
        final var totalCap = throttle.capacity();
        final var neededCap = throttle.clampedCapacityRequiredFor(expectedMinReqs);
        throttle.resetUsageTo(new DeterministicThrottle.UsageSnapshot(totalCap - neededCap, now));
        assertFalse(subject.stillLacksMinFreeCapAfterLeakingUntil(now));
        throttle.resetUsageTo(
                new DeterministicThrottle.UsageSnapshot(totalCap - neededCap + 1, now));
        assertTrue(subject.stillLacksMinFreeCapAfterLeakingUntil(now));
    }

    @Test
    void fallsBackToDefaultResourceIfProvidedIsUnavailable() {
        final var expectedMtps = 10000000L;
        given(resourceLoader.readAllBytesIfPresent(OTHER_RESOURCE_LOC)).willReturn(null);
        given(resourceLoader.readAllBytesIfPresent("expiry-throttle.json"))
                .willReturn(getTestResource(VALID_RESOURCE_LOC));

        subject.rebuildGiven(OTHER_RESOURCE_LOC, minReqUnitOfWork);

        final var throttle = subject.getThrottle();
        assertEquals(expectedMtps, throttle.mtps());
        assertEquals(EXPECTED_CAPACITY, throttle.capacity());
    }

    @Test
    void warnsAndAllowsNoExpiryWorkIfFallbackResourceUnavailable() {
        subject.rebuildGiven(OTHER_RESOURCE_LOC, minReqUnitOfWork);

        assertFalse(subject.allow(List.of(ACCOUNTS_GET)));
    }

    @Test
    void unallowableOpsDontTakeCapacity() {
        given(resourceLoader.readAllBytesIfPresent(VALID_RESOURCE_LOC))
                .willReturn(getTestResource(VALID_RESOURCE_LOC));

        subject.rebuildGiven(VALID_RESOURCE_LOC, minReqUnitOfWork);

        final var impermissible = new MapAccessType[51];
        Arrays.fill(impermissible, STORAGE_PUT);
        assertFalse(subject.allow(Arrays.asList(impermissible)));
        final var throttle = subject.getThrottle();
        assertEquals(0L, throttle.used());
    }

    @Test
    void allowableOpsTakeCapacity() {
        given(resourceLoader.readAllBytesIfPresent(VALID_RESOURCE_LOC))
                .willReturn(getTestResource(VALID_RESOURCE_LOC));

        subject.rebuildGiven(VALID_RESOURCE_LOC, minReqUnitOfWork);

        final var permissible = new MapAccessType[50];
        Arrays.fill(permissible, STORAGE_PUT);
        assertTrue(subject.allow(Arrays.asList(permissible)));
        final var throttle = subject.getThrottle();
        assertEquals(EXPECTED_CAPACITY, throttle.used());

        assertFalse(subject.allow(List.of(STORAGE_PUT)));
    }

    @Test
    void canResetToSnapshotIfNonNullThrottle() {
        given(resourceLoader.readAllBytesIfPresent(VALID_RESOURCE_LOC))
                .willReturn(getTestResource(VALID_RESOURCE_LOC));
        final var someSnapshot = new DeterministicThrottle.UsageSnapshot(10_000L, pointA);

        subject.rebuildGiven(VALID_RESOURCE_LOC, minReqUnitOfWork);

        subject.resetToSnapshot(someSnapshot);

        final var currentSnapshot = subject.getThrottleSnapshot();
        assertEquals(someSnapshot, currentSnapshot);
    }

    @Test
    void namecanReclaimLastAllowed() {
        given(resourceLoader.readAllBytesIfPresent(VALID_RESOURCE_LOC))
                .willReturn(getTestResource(VALID_RESOURCE_LOC));

        assertDoesNotThrow(subject::reclaimLastAllowedUse);
        subject.rebuildGiven(VALID_RESOURCE_LOC, minReqUnitOfWork);

        subject.allow(List.of(STORAGE_GET));
        subject.reclaimLastAllowedUse();
        final var used = Objects.requireNonNull(subject.getThrottleSnapshot()).used();
        assertEquals(0, used);
    }

    @Test
    void snapshotNullIfThrottleNull() {
        assertNull(subject.getThrottleSnapshot());
    }

    @Test
    void complainsIfResetWithoutInitialization() {
        final var someSnapshot = new DeterministicThrottle.UsageSnapshot(10_000L, pointA);

        assertDoesNotThrow(() -> subject.resetToSnapshot(someSnapshot));
    }

    private void assertGroupContents(
            final long opsPerSec,
            final List<MapAccessType> ops,
            final ThrottleGroup<MapAccessType> group) {
        assertEquals(opsPerSec, group.getOpsPerSec());
        assertEquals(ops, group.getOperations());
    }

    private byte[] getTestResource(final String loc) {
        try (final var in = ExpiryThrottleTest.class.getClassLoader().getResourceAsStream(loc)) {
            return Objects.requireNonNull(in).readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private int requiredOps(
            final List<MapAccessType> accessTypes, final Map<MapAccessType, Integer> reqs) {
        var ans = 0;
        for (final var accessType : accessTypes) {
            ans += reqs.get(accessType);
        }
        return ans;
    }
}
