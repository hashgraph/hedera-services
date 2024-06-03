/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.flow.future;

import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.WritableScheduleStore;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.ScheduleExpirationHook;
import com.hedera.node.app.workflows.handle.flow.modules.UserTransactionComponent;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ScheduleServiceCronLogic {
    private final ScheduleExpirationHook scheduleExpirationHook;
    private final StoreMetricsService storeMetricsService;

    @Inject
    public ScheduleServiceCronLogic(
            final ScheduleExpirationHook scheduleExpirationHook, final StoreMetricsService storeMetricsService) {
        this.scheduleExpirationHook = scheduleExpirationHook;
        this.storeMetricsService = storeMetricsService;
    }

    public void expireSchedules(Instant lastHandledTxnTime, UserTransactionComponent userTxn) {
        if (lastHandledTxnTime == Instant.EPOCH) {
            return;
        }
        if (userTxn.consensusNow().getEpochSecond() > lastHandledTxnTime.getEpochSecond()) {
            final var firstSecondToExpire = lastHandledTxnTime.getEpochSecond();
            final var lastSecondToExpire = userTxn.consensusNow().getEpochSecond() - 1;
            final var scheduleStore = new WritableStoreFactory(
                            userTxn.savepointStack(),
                            ScheduleService.NAME,
                            userTxn.tokenContext().configuration(),
                            storeMetricsService)
                    .getStore(WritableScheduleStore.class);
            // purge all expired schedules between the first consensus time of last block and the current consensus time
            scheduleExpirationHook.processExpiredSchedules(scheduleStore, firstSecondToExpire, lastSecondToExpire);
        }
    }
}
