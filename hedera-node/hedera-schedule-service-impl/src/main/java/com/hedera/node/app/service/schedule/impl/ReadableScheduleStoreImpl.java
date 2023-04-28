/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.impl;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Optional;

/**
 * Default implementation of {@link ReadableScheduleStore}.
 */
public class ReadableScheduleStoreImpl implements ReadableScheduleStore {
    /** The underlying data storage class that holds the token data. */
    private final ReadableKVState<Long, ScheduleVirtualValue> schedulesById;

    /**
     * Create a new {@link ReadableScheduleStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public ReadableScheduleStoreImpl(@NonNull final ReadableStates states) {
        Objects.requireNonNull(states);
        this.schedulesById = states.get("SCHEDULES_BY_ID");
    }

    @Override
    @NonNull
    public Optional<ScheduleMetadata> get(@NonNull final ScheduleID id) {
        final var schedule = schedulesById.get(id.scheduleNum());
        if (schedule == null) {
            return Optional.empty();
        }
        final Key adminKey;
        if (schedule.hasAdminKey()) {
            adminKey = PbjConverter.asPbjKey(schedule.adminKey().get());
        } else {
            adminKey = null;
        }
        return Optional.ofNullable(schedule)
                .map(s -> new ScheduleMetadata(
                        adminKey,
                        PbjConverter.toPbj(schedule.ordinaryViewOfScheduledTxn()),
                        schedule.hasExplicitPayer()
                                ? Optional.of(schedule.payer().toPbjAccountId())
                                : Optional.empty()));
    }
}
