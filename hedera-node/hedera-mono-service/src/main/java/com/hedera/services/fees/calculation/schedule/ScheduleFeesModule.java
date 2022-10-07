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
package com.hedera.services.fees.calculation.schedule;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleSign;

import com.hedera.services.fees.annotations.FunctionKey;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.fees.calculation.schedule.queries.GetScheduleInfoResourceUsage;
import com.hedera.services.fees.calculation.schedule.txns.ScheduleCreateResourceUsage;
import com.hedera.services.fees.calculation.schedule.txns.ScheduleDeleteResourceUsage;
import com.hedera.services.fees.calculation.schedule.txns.ScheduleSignResourceUsage;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoMap;
import java.util.List;
import java.util.Set;

@Module
public final class ScheduleFeesModule {
    @Provides
    @ElementsIntoSet
    public static Set<QueryResourceUsageEstimator> provideScheuleQueryEstimators(
            final GetScheduleInfoResourceUsage getScheduleInfoResourceUsage) {
        return Set.of(getScheduleInfoResourceUsage);
    }

    @Provides
    @IntoMap
    @FunctionKey(ScheduleCreate)
    public static List<TxnResourceUsageEstimator> provideScheduleCreateEstimator(
            final ScheduleCreateResourceUsage scheduleCreateResourceUsage) {
        return List.of(scheduleCreateResourceUsage);
    }

    @Provides
    @IntoMap
    @FunctionKey(ScheduleDelete)
    public static List<TxnResourceUsageEstimator> provideScheduleDeleteEstimator(
            final ScheduleDeleteResourceUsage scheduleDeleteResourceUsage) {
        return List.of(scheduleDeleteResourceUsage);
    }

    @Provides
    @IntoMap
    @FunctionKey(ScheduleSign)
    public static List<TxnResourceUsageEstimator> provideScheduleSignEstimator(
            final ScheduleSignResourceUsage scheduleSignResourceUsage) {
        return List.of(scheduleSignResourceUsage);
    }

    private ScheduleFeesModule() {
        throw new UnsupportedOperationException("Dagger2 module");
    }
}
