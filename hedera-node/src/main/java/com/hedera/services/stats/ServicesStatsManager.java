/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.stats;

import static com.hedera.services.utils.SleepingPause.SLEEPING_PAUSE;

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.utils.Pause;
import com.swirlds.common.system.Platform;
import com.swirlds.virtualmap.VirtualMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ServicesStatsManager {
    private static final long MIN_STAT_INTERVAL_UPDATE_MS = 100L;
    public static final String STAT_CATEGORY = "app";
    public static final String GAUGE_FORMAT = "%,13.2f";
    public static final String SPEEDOMETER_FORMAT = "%,13.2f";
    public static final String RUNNING_AVG_FORMAT = "%,13.6f";
    static Pause pause = SLEEPING_PAUSE;
    static Function<Runnable, Thread> loopFactory =
            loop ->
                    new Thread(
                            () -> {
                                while (true) {
                                    loop.run();
                                }
                            });

    static final String STATS_UPDATE_THREAD_NAME_TPL = "StatsUpdateThread%d";

    private final HapiOpCounters opCounters;
    private final MiscRunningAvgs runningAvgs;
    private final MiscSpeedometers speedometers;
    private final HapiOpSpeedometers opSpeedometers;
    private final NodeLocalProperties localProperties;
    private final ThrottleGauges throttleGauges;
    private final EntityUtilGauges entityUtilGauges;
    private final Supplier<VirtualMap<ContractKey, IterableContractValue>> storage;
    private final Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> bytecode;

    @Inject
    public ServicesStatsManager(
            final HapiOpCounters opCounters,
            final ThrottleGauges throttleGauges,
            final MiscRunningAvgs runningAvgs,
            final EntityUtilGauges entityUtilGauges,
            final MiscSpeedometers speedometers,
            final HapiOpSpeedometers opSpeedometers,
            final NodeLocalProperties localProperties,
            final Supplier<VirtualMap<ContractKey, IterableContractValue>> storage,
            final Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> bytecode) {
        this.storage = storage;
        this.bytecode = bytecode;
        this.localProperties = localProperties;
        this.opCounters = opCounters;
        this.runningAvgs = runningAvgs;
        this.speedometers = speedometers;
        this.opSpeedometers = opSpeedometers;
        this.throttleGauges = throttleGauges;
        this.entityUtilGauges = entityUtilGauges;
    }

    public void initializeFor(final Platform platform) {
        opCounters.registerWith(platform);
        runningAvgs.registerWith(platform);
        speedometers.registerWith(platform);
        throttleGauges.registerWith(platform);
        opSpeedometers.registerWith(platform);
        entityUtilGauges.registerWith(platform);
        storage.get().registerMetrics(platform.getMetrics());
        bytecode.get().registerMetrics(platform.getMetrics());

        final var hapiOpsUpdateIntervalMs =
                Math.max(
                        MIN_STAT_INTERVAL_UPDATE_MS,
                        localProperties.hapiOpsStatsUpdateIntervalMs());
        final var entityUtilUpdateIntervalMs =
                Math.max(
                        MIN_STAT_INTERVAL_UPDATE_MS,
                        localProperties.entityUtilStatsUpdateIntervalMs());
        final var throttleUtilUpdateIntervalMs =
                Math.max(
                        MIN_STAT_INTERVAL_UPDATE_MS,
                        localProperties.throttleUtilStatsUpdateIntervalMs());
        final var pauseTimeMs =
                gcd(
                        hapiOpsUpdateIntervalMs,
                        entityUtilUpdateIntervalMs,
                        throttleUtilUpdateIntervalMs);
        final var pausesBetweenHapiOpsUpdate = hapiOpsUpdateIntervalMs / pauseTimeMs;
        final var pausesBetweenEntityUtilUpdate = entityUtilUpdateIntervalMs / pauseTimeMs;
        final var pausesBetweenThrottleUtilUpdate = throttleUtilUpdateIntervalMs / pauseTimeMs;
        final var numPauses = new AtomicLong(0);
        final var updateThread =
                loopFactory.apply(
                        () -> {
                            pause.forMs(pauseTimeMs);
                            final var n = numPauses.incrementAndGet();
                            if (n % pausesBetweenHapiOpsUpdate == 0) {
                                opSpeedometers.updateAll();
                            }
                            if (n % pausesBetweenThrottleUtilUpdate == 0) {
                                throttleGauges.updateAll();
                            }
                            if (n % pausesBetweenEntityUtilUpdate == 0) {
                                entityUtilGauges.updateAll();
                            }
                        });

        updateThread.setName(
                String.format(STATS_UPDATE_THREAD_NAME_TPL, platform.getSelfId().getId()));
        updateThread.start();
    }

    private long gcd(final long a, final long b, final long c) {
        final var abGcd = gcd(Math.min(a, b), Math.max(a, b));
        return gcd(Math.min(abGcd, c), Math.max(abGcd, c));
    }

    private long gcd(final long a, final long b) {
        return (a == 0) ? b : gcd(b % a, a);
    }
}
