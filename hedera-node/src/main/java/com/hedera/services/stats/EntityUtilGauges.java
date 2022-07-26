/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
                        new UtilGauge(usageLimits::percentAccountsUsed, gaugeFor("accounts")),
                        new UtilGauge(usageLimits::percentContractsUsed, gaugeFor("contracts")),
                        new UtilGauge(usageLimits::percentFilesUsed, gaugeFor("files")),
                        new UtilGauge(usageLimits::percentNftsUsed, gaugeFor("nfts")),
                        new UtilGauge(usageLimits::percentSchedulesUsed, gaugeFor("schedules")),
                        new UtilGauge(
                                usageLimits::percentStorageSlotsUsed,
                                gaugeFor("storageSlots", "storage slots")),
                        new UtilGauge(usageLimits::percentTokensUsed, gaugeFor("tokens")),
                        new UtilGauge(
                                usageLimits::percentTokenRelsUsed,
                                gaugeFor("tokenAssociations", "token associations")),
                        new UtilGauge(usageLimits::percentTopicsUsed, gaugeFor("topics")));
    }

    public void registerWith(final Platform platform) {
        utils.forEach(util -> platform.addAppMetrics(util.gauge()));
    }

    public void updateAll() {
        utils.forEach(util -> util.gauge().set(util.valueSource().getAsDouble()));
    }

    private static DoubleGauge gaugeFor(final String utilType) {
        return gaugeFor(utilType, null);
    }

    private static DoubleGauge gaugeFor(final String utilType, @Nullable final String forDesc) {
        return new DoubleGauge(
                STAT_CATEGORY,
                String.format(UTIL_NAME_TPL, utilType),
                String.format(UTIL_DESCRIPTION_TPL, Optional.ofNullable(forDesc).orElse(utilType)),
                GAUGE_FORMAT);
    }
}
