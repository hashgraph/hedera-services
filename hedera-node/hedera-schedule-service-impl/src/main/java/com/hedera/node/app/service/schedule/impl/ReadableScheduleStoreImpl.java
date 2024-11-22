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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduledCounts;
import com.hedera.hapi.node.state.schedule.ScheduledOrder;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema;
import com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Schedules.
 *
 */
public class ReadableScheduleStoreImpl implements ReadableScheduleStore {
    private static final String NULL_STATE_IN_CONSTRUCTOR_MESSAGE =
            "Null states instance passed to ReadableScheduleStore constructor, possible state corruption.";

    private final ReadableKVState<ScheduleID, Schedule> schedulesById;
    private final ReadableKVState<TimestampSeconds, ScheduledCounts> scheduledCounts;
    private final ReadableKVState<TimestampSeconds, ThrottleUsageSnapshots> scheduledUsages;
    private final ReadableKVState<ScheduledOrder, ScheduleID> scheduledOrders;
    private final ReadableKVState<ProtoBytes, ScheduleID> scheduleIdByStringHash;

    /**
     * Create a new {@link ReadableScheduleStore} instance.
     *
     * @param states The state to use.
     */
    public ReadableScheduleStoreImpl(@NonNull final ReadableStates states) {
        requireNonNull(states, NULL_STATE_IN_CONSTRUCTOR_MESSAGE);
        schedulesById = states.get(V0490ScheduleSchema.SCHEDULES_BY_ID_KEY);
        scheduledCounts = states.get(V0570ScheduleSchema.SCHEDULED_COUNTS_KEY);
        scheduledOrders = states.get(V0570ScheduleSchema.SCHEDULED_ORDERS_KEY);
        scheduledUsages = states.get(V0570ScheduleSchema.SCHEDULED_USAGES_KEY);
        scheduleIdByStringHash = states.get(V0570ScheduleSchema.SCHEDULE_ID_BY_EQUALITY_KEY);
    }

    /**
     * Get a {@link Schedule} referenced by {@link ScheduleID}.
     * If the schedule ID is null or there is no schedule referenced by that ID, then return null.
     *
     * @param id given id for the schedule
     *
     * @return The Schedule corresponding to the id provided, or null if there is no such schedule
     */
    @Override
    @Nullable
    public Schedule get(@Nullable final ScheduleID id) {
        return (id == null) ? null : schedulesById.get(id);
    }

    @Override
    @Nullable
    public ScheduleID getByEquality(final @NonNull Schedule scheduleToMatch) {
        Bytes bytesHash = ScheduleStoreUtility.calculateBytesHash(scheduleToMatch);
        return scheduleIdByStringHash.get(new ProtoBytes(bytesHash));
    }

    @Override
    public @NonNull List<ScheduleID> getByExpirationSecond(final long expirationTime) {
        final List<ScheduleID> scheduleIds = new ArrayList<>();
        final var counts = scheduledCounts.get(new TimestampSeconds(expirationTime));
        if (counts != null) {
            for (int i = counts.numberProcessed(), n = counts.numberScheduled(); i < n; i++) {
                final var scheduleId = scheduledOrders.get(new ScheduledOrder(expirationTime, i));
                scheduleIds.add(requireNonNull(scheduleId));
            }
        }
        return scheduleIds;
    }

    /**
     * {@inheritDoc}
     */
    public List<Schedule> getByExpirationBetween(final long firstSecondToExpire, final long lastSecondToExpire) {
        final var schedules = new ArrayList<Schedule>();
        for (long i = firstSecondToExpire; i <= lastSecondToExpire; i++) {
            final var scheduleIdList = getByExpirationSecond(i);
            if (scheduleIdList != null) {
                schedules.addAll(scheduleIdList.stream().map(this::get).toList());
            }
        }
        return schedules;
    }

    @Override
    public long numSchedulesInState() {
        return schedulesById.size();
    }

    @Override
    public int numTransactionsScheduledAt(final long consensusSecond) {
        final var counts = scheduledCounts.get(new TimestampSeconds(consensusSecond));
        return counts == null ? 0 : counts.numberScheduled();
    }

    @Override
    public @Nullable ThrottleUsageSnapshots usageSnapshotsForScheduled(final long consensusSecond) {
        return scheduledUsages.get(new TimestampSeconds(consensusSecond));
    }
}
