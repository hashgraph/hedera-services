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
package com.hedera.services.throttling;

import static org.hamcrest.MatcherAssert.assertThat;

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.store.schedule.ScheduleStore;
import com.swirlds.common.system.address.AddressBook;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThrottlingModuleTest {
    private static final GlobalDynamicProperties dynamicProperties = new MockGlobalDynamicProps();

    @Mock private AddressBook addressBook;
    @Mock private AliasManager aliasManager;
    @Mock private TransactionContext txnCtx;
    @Mock private ScheduleStore scheduleStore;

    @Test
    void constructsHapiAndHandleThrottlesAsExpected() {
        final var hapiThrottle =
                ThrottlingModule.provideHapiThrottling(
                        aliasManager, () -> addressBook, dynamicProperties, scheduleStore);
        final var handleThrottle =
                ThrottlingModule.provideHandleThrottling(
                        aliasManager, txnCtx, dynamicProperties, scheduleStore);
        final var timedScheduleThrottling =
                ThrottlingModule.provideTimedScheduleThrottling(
                        aliasManager, dynamicProperties, scheduleStore);
        final var scheduleThrottling =
                ThrottlingModule.provideScheduleThrottling(timedScheduleThrottling);

        assertThat(hapiThrottle, Matchers.instanceOf(HapiThrottling.class));
        assertThat(handleThrottle, Matchers.instanceOf(TxnAwareHandleThrottling.class));
        assertThat(timedScheduleThrottling, Matchers.instanceOf(DeterministicThrottling.class));
        assertThat(scheduleThrottling, Matchers.instanceOf(DeterministicThrottling.class));
        assertThat(scheduleThrottling, Matchers.equalTo(timedScheduleThrottling));
    }
}
