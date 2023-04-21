/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.stats;

import static com.hedera.node.app.service.mono.stats.ServicesStatsManager.GAUGE_FORMAT;
import static com.hedera.node.app.service.mono.stats.ServicesStatsManager.STAT_CATEGORY;

import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.service.mono.utils.NonAtomicReference;
import com.swirlds.common.metrics.DoubleGauge;
import com.swirlds.common.system.Platform;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class EntityUtilGauges {
    private static final String UTIL_NAME_TPL = "%sUsed";
    private static final String UTIL_DESCRIPTION_TPL = "instantaneous %% used of %s system limit";

    private final List<UtilGauge> utils;

    @Inject
    public EntityUtilGauges(final UsageLimits usageLimits) {
        utils = List.of(
                // percent metrics for the current number of each entity type
                new UtilGauge(
                        usageLimits::percentAccountsUsed,
                        gaugeConfigFor("accountsPercent"),
                        new NonAtomicReference<>()),
                new UtilGauge(
                        usageLimits::percentContractsUsed,
                        gaugeConfigFor("contractsPercent"),
                        new NonAtomicReference<>()),
                new UtilGauge(
                        usageLimits::percentFilesUsed, gaugeConfigFor("filesPercent"), new NonAtomicReference<>()),
                new UtilGauge(usageLimits::percentNftsUsed, gaugeConfigFor("nftsPercent"), new NonAtomicReference<>()),
                new UtilGauge(
                        usageLimits::percentSchedulesUsed,
                        gaugeConfigFor("schedulesPercent"),
                        new NonAtomicReference<>()),
                new UtilGauge(
                        usageLimits::percentStorageSlotsUsed,
                        gaugeConfigFor("storageSlotsPercent", "storage slots"),
                        new NonAtomicReference<>()),
                new UtilGauge(
                        usageLimits::percentTokensUsed, gaugeConfigFor("tokensPercent"), new NonAtomicReference<>()),
                new UtilGauge(
                        usageLimits::percentTokenRelsUsed,
                        gaugeConfigFor("tokenAssociationsPercent", "token associations"),
                        new NonAtomicReference<>()),
                new UtilGauge(
                        usageLimits::percentTopicsUsed, gaugeConfigFor("topicsPercent"), new NonAtomicReference<>()),
                // plain metrics for the current number of each entity type
                new UtilGauge(usageLimits::getNumAccounts, gaugeConfigFor("accounts"), new NonAtomicReference<>()),
                new UtilGauge(usageLimits::getNumContracts, gaugeConfigFor("contracts"), new NonAtomicReference<>()),
                new UtilGauge(usageLimits::getNumFiles, gaugeConfigFor("files"), new NonAtomicReference<>()),
                new UtilGauge(usageLimits::getNumNfts, gaugeConfigFor("nfts"), new NonAtomicReference<>()),
                new UtilGauge(usageLimits::getNumSchedules, gaugeConfigFor("schedules"), new NonAtomicReference<>()),
                new UtilGauge(
                        usageLimits::getNumStorageSlots,
                        gaugeConfigFor("storageSlots", "storage slots"),
                        new NonAtomicReference<>()),
                new UtilGauge(usageLimits::getNumTokens, gaugeConfigFor("tokens"), new NonAtomicReference<>()),
                new UtilGauge(
                        usageLimits::getNumTokenRels,
                        gaugeConfigFor("tokenAssociations", "token associations"),
                        new NonAtomicReference<>()),
                new UtilGauge(usageLimits::getNumTopics, gaugeConfigFor("topics"), new NonAtomicReference<>()));
    }

    public void registerWith(final Platform platform) {
        utils.forEach(util -> {
            final var gauge = platform.getContext().getMetrics().getOrCreate(util.config());
            util.gauge().set(gauge);
        });
    }

    public void updateAll() {
        utils.forEach(util -> util.gauge().get().set(util.valueSource().getAsDouble()));
    }

    private static DoubleGauge.Config gaugeConfigFor(final String utilType) {
        return gaugeConfigFor(utilType, null);
    }

    private static DoubleGauge.Config gaugeConfigFor(final String utilType, @Nullable final String forDesc) {
        return new DoubleGauge.Config(STAT_CATEGORY, String.format(UTIL_NAME_TPL, utilType))
                .withDescription(String.format(
                        UTIL_DESCRIPTION_TPL, Optional.ofNullable(forDesc).orElse(utilType)))
                .withFormat(GAUGE_FORMAT);
    }
}
