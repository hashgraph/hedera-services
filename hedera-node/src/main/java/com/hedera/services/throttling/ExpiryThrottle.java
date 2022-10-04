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

import static com.hedera.services.utils.MiscUtils.safeResetThrottles;
import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.files.HybridResouceLoader;
import com.hedera.services.sysfiles.domain.throttling.ThrottleBucket;
import com.hedera.services.throttles.DeterministicThrottle;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A throttle to be used to control usage of {@link com.swirlds.merkle.map.MerkleMap} and {@link
 * com.swirlds.virtualmap.VirtualMap} objects during expiration work (auto-renewal and
 * auto-removal).
 */
@Singleton
public class ExpiryThrottle {
    private static final Logger log = LogManager.getLogger(ExpiryThrottle.class);
    private static final String FALLBACK_RESOURCE_LOC = "expiry-throttle.json";

    private final HybridResouceLoader resourceLoader;

    private long minFreeReq;
    private ThrottleConfig config;

    @Inject
    public ExpiryThrottle(final HybridResouceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public boolean stillLacksMinFreeCapAfterLeakingUntil(final Instant now) {
        if (config == null) {
            return true;
        }
        config.throttle.leakUntil(now);
        return config.throttle.capacityFree() < minFreeReq;
    }

    public boolean allow(final List<MapAccessType> accessTypes) {
        if (config != null) {
            config.throttle.resetLastAllowedUse();
            return config.throttle.allowInstantaneous(requiredOps(accessTypes));
        }
        return false;
    }

    public boolean allowOne(final MapAccessType accessType) {
        if (config != null) {
            config.throttle.resetLastAllowedUse();
            return config.throttle.allowInstantaneous(config.accessReqs.get(accessType));
        }
        return false;
    }

    public void reclaimLastAllowedUse() {
        if (config != null) {
            config.throttle.reclaimLastAllowedUse();
        }
    }

    public void rebuildGiven(final String resourceLoc, final List<MapAccessType> minUnitOfWork) {
        try {
            resetInternalsFrom(resourceLoc);
        } catch (Exception userError) {
            log.warn("Unable to load override expiry throttle from {}", resourceLoc, userError);
            try {
                resetInternalsFrom(FALLBACK_RESOURCE_LOC);
            } catch (Exception unrecoverable) {
                log.error(
                        "Unable to load default expiry throttle, will reject all expiry work",
                        unrecoverable);
                config = null;
            }
        }
        if (config != null) {
            minFreeReq = config.throttle.clampedCapacityRequiredFor(requiredOps(minUnitOfWork));
        }
    }

    public void resetToSnapshot(final DeterministicThrottle.UsageSnapshot snapshot) {
        if (config == null) {
            log.error("Attempt to reset expiry throttle to {} before initialization", snapshot);
        } else {
            safeResetThrottles(
                    List.of(config.throttle),
                    new DeterministicThrottle.UsageSnapshot[] {snapshot},
                    "expiry");
        }
    }

    @Nullable
    public DeterministicThrottle getThrottle() {
        return config.throttle;
    }

    @Nullable
    public DeterministicThrottle.UsageSnapshot getThrottleSnapshot() {
        return (config == null) ? null : config.throttle.usageSnapshot();
    }

    private record ThrottleConfig(
            DeterministicThrottle throttle, Map<MapAccessType, Integer> accessReqs) {}

    private void resetInternalsFrom(final String loc) throws JsonProcessingException {
        final var bucket = loadBucket(loc);
        final var mapping = bucket.asThrottleMapping(1);
        final var throttle = mapping.getKey();
        final var accessReqs =
                mapping.getValue().stream()
                        .collect(
                                toMap(
                                        Pair::getKey,
                                        Pair::getValue,
                                        (a, b) -> a,
                                        () -> new EnumMap<>(MapAccessType.class)));
        config = new ThrottleConfig(throttle, accessReqs);
    }

    private ThrottleBucket<MapAccessType> loadBucket(final String at)
            throws JsonProcessingException {
        var bytes = resourceLoader.readAllBytesIfPresent(at);
        if (bytes == null) {
            throw new IllegalArgumentException("Cannot load throttle from '" + at + "'");
        }
        return parseJson(new String(bytes));
    }

    private int requiredOps(final List<MapAccessType> accessTypes) {
        var ans = 0;
        for (final var accessType : accessTypes) {
            ans += config.accessReqs.get(accessType);
        }
        return ans;
    }

    @VisibleForTesting
    ThrottleBucket<MapAccessType> parseJson(final String throttle) throws JsonProcessingException {
        final var om = new ObjectMapper();
        final var pojo = om.readValue(throttle, ExpiryThrottlePojo.class);
        return pojo.getBucket();
    }

    @VisibleForTesting
    Map<MapAccessType, Integer> getAccessReqs() {
        return config.accessReqs;
    }
}
