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
package com.hedera.services.throttling;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.throttling.DeterministicThrottling.DeterministicThrottlingMode;
import com.hedera.services.throttling.annotations.HandleThrottle;
import com.hedera.services.throttling.annotations.HapiThrottle;
import com.hedera.services.throttling.annotations.ScheduleThrottle;
import com.swirlds.common.system.address.AddressBook;
import dagger.Module;
import dagger.Provides;
import java.util.function.Supplier;
import javax.inject.Singleton;

@Module
public final class ThrottlingModule {
    @Provides
    @Singleton
    @HapiThrottle
    public static FunctionalityThrottling provideHapiThrottling(
            final AliasManager aliasManager,
            final Supplier<AddressBook> addressBook,
            final GlobalDynamicProperties dynamicProperties,
            final ScheduleStore scheduleStore) {
        final var delegate =
                new DeterministicThrottling(
                        () -> addressBook.get().getSize(),
                        aliasManager,
                        dynamicProperties,
                        DeterministicThrottlingMode.HAPI,
                        scheduleStore);
        return new HapiThrottling(delegate);
    }

    @Provides
    @Singleton
    @ScheduleThrottle
    public static TimedFunctionalityThrottling provideTimedScheduleThrottling(
            final AliasManager aliasManager,
            final GlobalDynamicProperties dynamicProperties,
            final ScheduleStore scheduleStore) {
        return new DeterministicThrottling(
                () -> 1,
                aliasManager,
                dynamicProperties,
                DeterministicThrottlingMode.SCHEDULE,
                scheduleStore);
    }

    @Provides
    @Singleton
    @ScheduleThrottle
    public static FunctionalityThrottling provideScheduleThrottling(
            @ScheduleThrottle final TimedFunctionalityThrottling timedScheduleThrottling) {
        return timedScheduleThrottling;
    }

    @Provides
    @Singleton
    @HandleThrottle
    public static FunctionalityThrottling provideHandleThrottling(
            final AliasManager aliasManager,
            final TransactionContext txnCtx,
            final GlobalDynamicProperties dynamicProperties,
            final ScheduleStore scheduleStore) {
        final var delegate =
                new DeterministicThrottling(
                        () -> 1,
                        aliasManager,
                        dynamicProperties,
                        DeterministicThrottlingMode.CONSENSUS,
                        scheduleStore);
        return new TxnAwareHandleThrottling(txnCtx, delegate);
    }

    private ThrottlingModule() {
        throw new UnsupportedOperationException("Dagger2 module");
    }
}
