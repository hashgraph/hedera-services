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
package com.hedera.services.stats;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.throttling.annotations.HandleThrottle;
import com.hedera.services.throttling.annotations.HapiThrottle;
import com.hedera.services.utils.MiscUtils;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

@Module
public final class StatsModule {
    @Provides
    @Singleton
    public static ExpiryStats provideExpiryStats(final NodeLocalProperties nodeLocalProperties) {
        return new ExpiryStats(nodeLocalProperties.statsRunningAvgHalfLifeSecs());
    }

    @Provides
    @Singleton
    public static MiscRunningAvgs provideMiscRunningAvgs(
            final NodeLocalProperties nodeLocalProperties) {
        return new MiscRunningAvgs(nodeLocalProperties.statsRunningAvgHalfLifeSecs());
    }

    @Provides
    @Singleton
    public static ThrottleGauges provideThrottleUtilizations(
            final @HandleThrottle FunctionalityThrottling handleThrottling,
            final @HapiThrottle FunctionalityThrottling hapiThrottling,
            final NodeLocalProperties nodeProperties) {
        return new ThrottleGauges(handleThrottling, hapiThrottling, nodeProperties);
    }

    @Provides
    @Singleton
    public static MiscSpeedometers provideMiscSpeedometers(
            final NodeLocalProperties nodeLocalProperties) {
        return new MiscSpeedometers(nodeLocalProperties.statsSpeedometerHalfLifeSecs());
    }

    @Provides
    @Singleton
    public static HapiOpSpeedometers provideHapiOpSpeedometers(
            final HapiOpCounters counters, final NodeLocalProperties nodeLocalProperties) {
        return new HapiOpSpeedometers(counters, nodeLocalProperties, MiscUtils::baseStatNameOf);
    }

    @Provides
    @Singleton
    public static HapiOpCounters provideHapiOpCounters(
            final MiscRunningAvgs runningAvgs, final TransactionContext txnCtx) {
        return new HapiOpCounters(runningAvgs, txnCtx, MiscUtils::baseStatNameOf);
    }

    private StatsModule() {
        throw new UnsupportedOperationException("Dagger2 module");
    }
}
