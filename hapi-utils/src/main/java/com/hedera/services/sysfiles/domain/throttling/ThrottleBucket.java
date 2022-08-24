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

import static com.hedera.services.sysfiles.validation.ErrorCodeUtils.exceptionMsgFor;
import static com.hedera.services.throttles.DeterministicThrottle.capacityRequiredFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUCKET_CAPACITY_OVERFLOW;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUCKET_HAS_NO_THROTTLE_GROUPS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OPERATION_REPEATED_IN_BUCKET_GROUPS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC;
import static java.util.Collections.disjoint;

import com.hedera.services.throttles.BucketThrottle;
import com.hedera.services.throttles.DeterministicThrottle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ThrottleBucket<E extends Enum<E>> {
    private static final Logger log = LogManager.getLogger(ThrottleBucket.class);

    private static final String BUCKET_PREFIX = "Bucket ";

    private int burstPeriod;
    private long burstPeriodMs;
    private String name;
    private List<ThrottleGroup<E>> throttleGroups = new ArrayList<>();

    public ThrottleBucket() {
        // Needed by Jackson
    }

    public ThrottleBucket(long burstPeriodMs, String name, List<ThrottleGroup<E>> throttleGroups) {
        this.burstPeriodMs = burstPeriodMs;
        this.name = name;
        this.throttleGroups = throttleGroups;
    }

    public long getBurstPeriodMs() {
        return burstPeriodMs;
    }

    public void setBurstPeriodMs(final long burstPeriodMs) {
        this.burstPeriodMs = burstPeriodMs;
    }

    public int getBurstPeriod() {
        return burstPeriod;
    }

    public void setBurstPeriod(final int burstPeriod) {
        this.burstPeriod = burstPeriod;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public List<ThrottleGroup<E>> getThrottleGroups() {
        return throttleGroups;
    }

    public void setThrottleGroups(List<ThrottleGroup<E>> throttleGroups) {
        this.throttleGroups = throttleGroups;
    }

    /**
     * Returns a deterministic throttle scoped to (1 / capacitySplit) of the nominal milliOpsPerSec
     * in each throttle group; and a list that maps each relevant {@code HederaFunctionality} to the
     * number of logical operations it requires from the throttle.
     *
     * @param capacitySplit capacity split
     * @return a throttle with (1 / capacitySplit) the capacity of this bucket, and a list of how
     *     many logical operations each assigned function will use from the throttle
     * @throws IllegalStateException if this bucket was constructed with invalid throttle groups
     */
    public Pair<DeterministicThrottle, List<Pair<E, Integer>>> asThrottleMapping(
            final long capacitySplit) {
        if (throttleGroups.isEmpty()) {
            throw new IllegalStateException(
                    exceptionMsgFor(
                            BUCKET_HAS_NO_THROTTLE_GROUPS,
                            BUCKET_PREFIX + name + " includes no throttle groups!"));
        }

        assertMinimalOpsPerSec();
        final var mtps = logicalMtps();
        return mappingWith(mtps, capacitySplit);
    }

    private long logicalMtps() {
        final var ans = requiredLogicalMilliTpsToAccommodateAllGroups();
        if (ans < 0) {
            throw new IllegalStateException(
                    exceptionMsgFor(
                            BUCKET_CAPACITY_OVERFLOW,
                            BUCKET_PREFIX + name + " overflows with given throttle groups!"));
        }
        return ans;
    }

    private Pair<DeterministicThrottle, List<Pair<E, Integer>>> mappingWith(
            final long mtps, final long capacitySplit) {
        final var throttle = throttleFor(mtps, capacitySplit);
        final var totalCapacityUnits = throttle.capacity();

        final Set<E> seenSoFar = new HashSet<>();
        final List<Pair<E, Integer>> opsReqs = new ArrayList<>();
        for (final var throttleGroup : throttleGroups) {
            updateOpsReqs(
                    capacitySplit, mtps, totalCapacityUnits, throttleGroup, seenSoFar, opsReqs);
        }

        return Pair.of(throttle, opsReqs);
    }

    private void updateOpsReqs(
            final long capacitySplit,
            final long mtps,
            final long totalCapacity,
            final ThrottleGroup<E> group,
            final Set<E> seenSoFar,
            final List<Pair<E, Integer>> opsReqs) {
        final var opsReq = (int) (mtps / group.impliedMilliOpsPerSec());
        final var capacityReq = capacityRequiredFor(opsReq);
        if (capacityReq < 0 || capacityReq > totalCapacity) {
            throw new IllegalStateException(
                    exceptionMsgFor(
                            NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION,
                            BUCKET_PREFIX
                                    + name
                                    + " contains an unsatisfiable milliOpsPerSec with "
                                    + capacitySplit
                                    + " nodes!"));
        }

        final var functions = group.getOperations();
        if (disjoint(seenSoFar, functions)) {
            final Set<E> listedSoFar = new HashSet<>();
            for (final var function : functions) {
                if (!listedSoFar.contains(function)) {
                    opsReqs.add(Pair.of(function, opsReq));
                    listedSoFar.add(function);
                }
            }
            seenSoFar.addAll(functions);
        } else {
            throw new IllegalStateException(
                    exceptionMsgFor(
                            OPERATION_REPEATED_IN_BUCKET_GROUPS,
                            BUCKET_PREFIX + name + " assigns an operation to multiple groups!"));
        }
    }

    private DeterministicThrottle throttleFor(final long mtps, final long capacitySplit) {
        try {
            final var effBurstPeriodMs = autoScaledBurstPeriodMs(capacitySplit);
            return DeterministicThrottle.withMtpsAndBurstPeriodMsNamed(
                    mtps / capacitySplit, effBurstPeriodMs, name);
        } catch (final IllegalArgumentException unsatisfiable) {
            if (unsatisfiable.getMessage().startsWith("Cannot free")) {
                throw new IllegalStateException(
                        exceptionMsgFor(
                                BUCKET_CAPACITY_OVERFLOW,
                                BUCKET_PREFIX + name + " overflows with given throttle groups!"));
            } else {
                throw new IllegalStateException(
                        exceptionMsgFor(
                                NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION,
                                BUCKET_PREFIX
                                        + name
                                        + " contains an unsatisfiable milliOpsPerSec with "
                                        + capacitySplit
                                        + " nodes!"));
            }
        }
    }

    long autoScaledBurstPeriodMs(final long capacitySplit) {
        final var mtps = logicalMtps();
        var minCapacityUnitsPostSplit = 0L;
        for (final var group : throttleGroups) {
            final var opsReq = (int) (mtps / group.impliedMilliOpsPerSec());
            minCapacityUnitsPostSplit =
                    Math.max(minCapacityUnitsPostSplit, capacityRequiredFor(opsReq));
        }
        final var minCapacityUnits = minCapacityUnitsPostSplit * capacitySplit;
        final var capacityUnitsPerMs = BucketThrottle.capacityUnitsPerMs(mtps);
        final var minBurstPeriodMs = quotientRoundedUp(minCapacityUnits, capacityUnitsPerMs);
        final var reqBurstPeriodMs = impliedBurstPeriodMs();
        if (minBurstPeriodMs > reqBurstPeriodMs) {
            log.info(
                    "Auto-scaled {} burst period from {}ms -> {}ms to achieve requested"
                            + " steady-state OPS",
                    name,
                    reqBurstPeriodMs,
                    minBurstPeriodMs);
        }
        return Math.max(minBurstPeriodMs, impliedBurstPeriodMs());
    }

    public static long quotientRoundedUp(final long a, final long b) {
        return a / b + (a % b == 0 ? 0 : 1);
    }

    private void assertMinimalOpsPerSec() {
        for (final var group : throttleGroups) {
            if (group.impliedMilliOpsPerSec() == 0) {
                throw new IllegalStateException(
                        exceptionMsgFor(
                                THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC,
                                BUCKET_PREFIX
                                        + name
                                        + " contains a group with zero milliOpsPerSec!"));
            }
        }
    }

    private long requiredLogicalMilliTpsToAccommodateAllGroups() {
        var lcm = throttleGroups.get(0).impliedMilliOpsPerSec();
        for (int i = 1, n = throttleGroups.size(); i < n; i++) {
            lcm = lcm(lcm, throttleGroups.get(i).impliedMilliOpsPerSec());
        }
        return lcm;
    }

    public long impliedBurstPeriodMs() {
        return burstPeriodMs > 0 ? burstPeriodMs : 1_000L * burstPeriod;
    }

    private long lcm(final long a, final long b) {
        return (a * b) / gcd(Math.min(a, b), Math.max(a, b));
    }

    private long gcd(final long a, final long b) {
        return (a == 0) ? b : gcd(b % a, a);
    }
}
