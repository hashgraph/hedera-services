/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.throttling;

import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.store.schedule.ScheduleStore;
import com.hedera.node.app.service.mono.throttling.annotations.HandleThrottle;
import com.hedera.node.app.service.mono.throttling.annotations.HapiThrottle;
import com.hedera.node.app.service.mono.throttling.annotations.ScheduleThrottle;
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
        final var delegate = new DeterministicThrottling(
                () -> addressBook.get().getSize(),
                aliasManager,
                dynamicProperties,
                DeterministicThrottling.DeterministicThrottlingMode.HAPI,
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
                DeterministicThrottling.DeterministicThrottlingMode.SCHEDULE,
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
        final var delegate = new DeterministicThrottling(
                () -> 1,
                aliasManager,
                dynamicProperties,
                DeterministicThrottling.DeterministicThrottlingMode.CONSENSUS,
                scheduleStore);
        return new TxnAwareHandleThrottling(txnCtx, delegate);
    }

    private ThrottlingModule() {
        throw new UnsupportedOperationException("Dagger2 module");
    }
}
