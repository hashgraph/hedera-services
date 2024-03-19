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

package com.hedera.node.app.service.schedule.impl;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduleList;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Schedules.
 *
 */
public class ReadableScheduleStoreImpl implements ReadableScheduleStore {
    private static final String NULL_STATE_IN_CONSTRUCTOR_MESSAGE =
            "Null states instance passed to ReadableScheduleStore constructor, possible state corruption.";

    private final ReadableKVState<ScheduleID, Schedule> schedulesById;
    private final ReadableKVState<ProtoLong, ScheduleList> schedulesByExpirationSecond;
    private final ReadableKVState<ProtoBytes, ScheduleList> schedulesByStringHash;

    /**
     * Create a new {@link ReadableScheduleStore} instance.
     *
     * @param states The state to use.
     */
    public ReadableScheduleStoreImpl(@NonNull final ReadableStates states) {
        Objects.requireNonNull(states, NULL_STATE_IN_CONSTRUCTOR_MESSAGE);
        schedulesById = states.get(ScheduleServiceImpl.SCHEDULES_BY_ID_KEY);
        schedulesByExpirationSecond = states.get(ScheduleServiceImpl.SCHEDULES_BY_EXPIRY_SEC_KEY);
        schedulesByStringHash = states.get(ScheduleServiceImpl.SCHEDULES_BY_EQUALITY_KEY);
    }

    /**
     * Get a {@link Schedule} referenced by {@link ScheduleID}.
     * If the schedule ID is null or there is no schedule referenced by that ID, then return null.
     *
     * @param id given id for the schedule
     *
     * @return The Schedule corresponding to the id provided, or null if there is no such schedule.
     */
    @Override
    @Nullable
    public Schedule get(@Nullable final ScheduleID id) {
        return (id == null) ? null : schedulesById.get(id);
    }

    @Override
    @Nullable
    public List<Schedule> getByEquality(final @NonNull Schedule scheduleToMatch) {
        Bytes bytesHash = ScheduleStoreUtility.calculateBytesHash(scheduleToMatch);
        final ScheduleList inStateValue = schedulesByStringHash.get(new ProtoBytes(bytesHash));
        return inStateValue != null ? inStateValue.schedules() : null;
    }

    @Nullable
    @Override
    public List<Schedule> getByExpirationSecond(final long expirationTime) {
        final ScheduleList inStateValue = schedulesByExpirationSecond.get(new ProtoLong(expirationTime));
        return inStateValue != null ? inStateValue.schedules() : null;
    }

    @Override
    public long numSchedulesInState() {
        return schedulesById.size();
    }
}
