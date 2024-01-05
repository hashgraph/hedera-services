/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.schedule.WritableScheduleStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles the daily staking period updates
 */
@Singleton
public class ScheduleExpirationHook {
    private static final Logger logger = LogManager.getLogger(ScheduleExpirationHook.class);

    @Inject
    public ScheduleExpirationHook() {}

    public void processExpiredSchedules(
            @NonNull final WritableScheduleStore store, long firstSecondToExpire, final long lastSecondToExpire) {
        requireNonNull(store);
        // first transaction handled has a consensus time of 0
        if (firstSecondToExpire == Instant.EPOCH.getEpochSecond()) {
            firstSecondToExpire = lastSecondToExpire;
        }
        store.purgeExpiredSchedulesBetween(firstSecondToExpire, lastSecondToExpire);
    }
}
