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
package com.hedera.services.stats;

import static com.hedera.services.stats.ServicesStatsManager.GAUGE_FORMAT;
import static com.hedera.services.stats.ServicesStatsManager.STAT_CATEGORY;

import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.utils.NonAtomicReference;
import com.swirlds.common.metrics.DoubleGauge;
import com.swirlds.common.system.Platform;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class EntityUtilGauges {
    private static final String UTIL_NAME_TPL = "%sPercentUsed";
    private static final String UTIL_DESCRIPTION_TPL = "instantaneous %% used of %s system limit";

    private final List<UtilGauge> utils;

    @Inject
    public EntityUtilGauges(final UsageLimits usageLimits) {
        utils =
                List.of(
                        new UtilGauge(
                                usageLimits::percentAccountsUsed,
                                gaugeConfigFor("accounts"),
                                new NonAtomicReference<>()),
                        new UtilGauge(
                                usageLimits::percentContractsUsed,
                                gaugeConfigFor("contracts"),
                                new NonAtomicReference<>()),
                        new UtilGauge(
                                usageLimits::percentFilesUsed,
                                gaugeConfigFor("files"),
                                new NonAtomicReference<>()),
                        new UtilGauge(
                                usageLimits::percentNftsUsed,
                                gaugeConfigFor("nfts"),
                                new NonAtomicReference<>()),
                        new UtilGauge(
                                usageLimits::percentSchedulesUsed,
                                gaugeConfigFor("schedules"),
                                new NonAtomicReference<>()),
                        new UtilGauge(
                                usageLimits::percentStorageSlotsUsed,
                                gaugeConfigFor("storageSlots", "storage slots"),
                                new NonAtomicReference<>()),
                        new UtilGauge(
                                usageLimits::percentTokensUsed,
                                gaugeConfigFor("tokens"),
                                new NonAtomicReference<>()),
                        new UtilGauge(
                                usageLimits::percentTokenRelsUsed,
                                gaugeConfigFor("tokenAssociations", "token associations"),
                                new NonAtomicReference<>()),
                        new UtilGauge(
                                usageLimits::percentTopicsUsed,
                                gaugeConfigFor("topics"),
                                new NonAtomicReference<>()));
    }

    public void registerWith(final Platform platform) {
        utils.forEach(
                util -> {
                    final var gauge = platform.getOrCreateMetric(util.config());
                    util.gauge().set(gauge);
                });
    }

    public void updateAll() {
        utils.forEach(util -> util.gauge().get().set(util.valueSource().getAsDouble()));
    }

    private static DoubleGauge.Config gaugeConfigFor(final String utilType) {
        return gaugeConfigFor(utilType, null);
    }

    private static DoubleGauge.Config gaugeConfigFor(
            final String utilType, @Nullable final String forDesc) {
        return new DoubleGauge.Config(STAT_CATEGORY, String.format(UTIL_NAME_TPL, utilType))
                .withDescription(
                        String.format(
                                UTIL_DESCRIPTION_TPL,
                                Optional.ofNullable(forDesc).orElse(utilType)))
                .withFormat(GAUGE_FORMAT);
    }
}
