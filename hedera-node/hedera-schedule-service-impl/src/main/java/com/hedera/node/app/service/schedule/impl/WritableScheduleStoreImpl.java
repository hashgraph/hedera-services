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

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.node.app.service.schedule.WritableScheduleStore;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleUtility;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * A writable store that wraps a writable key-value state and supports operations required to create or update
 * schedule objects as a result of ScheduleCreate, ScheduleSign, or ScheduleDelete transactions.
 */
public class WritableScheduleStoreImpl extends ReadableScheduleStoreImpl implements WritableScheduleStore {
    private static final String SCHEDULE_NULL_FOR_DELETE_MESSAGE =
            "Request to delete null schedule ID cannot be fulfilled.";
    private static final String SCHEDULE_MISSING_FOR_DELETE_MESSAGE =
            "Schedule to be deleted, %1$s, not found in state.";
    private final WritableKVState<ScheduleID, Schedule> schedulesByIdMutable;
    private final WritableKVState<String, List<Schedule>> schedulesByEqualityMutable;
    private final WritableKVState<Long, List<Schedule>> schedulesByExpirationMutable;

    /**
     * Create a new {@link WritableScheduleStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public WritableScheduleStoreImpl(@NonNull final WritableStates states) {
        super(states);
        schedulesByIdMutable = states.get(ScheduleServiceImpl.SCHEDULES_BY_ID_KEY);
        schedulesByEqualityMutable = states.get(ScheduleServiceImpl.SCHEDULES_BY_EQUALITY_KEY);
        schedulesByExpirationMutable = states.get(ScheduleServiceImpl.SCHEDULES_BY_EXPIRY_SEC_KEY);
    }

    /**
     * Delete a given schedule from this state.
     * Given the ID of a schedule and a consensus time, delete that ID from this state as of the
     * consensus time {@link Instant} provided.
     * @param scheduleToDelete The ID of a schedule to be deleted.
     * @param consensusTime The current consensus time
     * @return the {@link Schedule} marked as deleted.
     * @throws IllegalStateException if the {@link ScheduleID} to be deleted is not present in this state,
     *     or the ID value has a mismatched realm or shard for this node.
     */
    @SuppressWarnings("DataFlowIssue")
    @Override
    @NonNull
    public Schedule delete(@Nullable final ScheduleID scheduleToDelete, @NonNull final Instant consensusTime) {
        Objects.requireNonNull(consensusTime, "Null consensusTime provided to schedule delete, cannot proceed.");
        if (scheduleToDelete != null) {
            final Schedule schedule = schedulesByIdMutable.getForModify(scheduleToDelete);
            if (schedule != null) {
                final Schedule deletedSchedule = markDeleted(schedule, consensusTime);
                schedulesByIdMutable.put(scheduleToDelete, deletedSchedule);
                return schedulesByIdMutable.get(scheduleToDelete);
            } else {
                throw new IllegalStateException(SCHEDULE_MISSING_FOR_DELETE_MESSAGE.formatted(scheduleToDelete));
            }
        } else {
            throw new IllegalStateException(SCHEDULE_NULL_FOR_DELETE_MESSAGE);
        }
    }

    @Override
    public Schedule getForModify(@Nullable final ScheduleID idToFind) {
        final Schedule result;
        if (idToFind != null) {
            result = schedulesByIdMutable.getForModify(idToFind);
        } else {
            result = null; // @todo('merge') Not sure if this is correct, need to research a bit more.
        }
        return result;
    }

    @Override
    public void put(@NonNull final Schedule scheduleToAdd) {
        schedulesByIdMutable.put(scheduleToAdd.idOrThrow(), scheduleToAdd);
        final String newHash = ScheduleUtility.calculateStringHash(scheduleToAdd);
        List<Schedule> byEquality = schedulesByEqualityMutable.get(newHash);
        if (byEquality == null) {
            byEquality = new LinkedList<>();
        }
        byEquality.add(scheduleToAdd);
        schedulesByEqualityMutable.put(newHash, byEquality);
        // calculated expiration time is never null...
        final Long expirationSecond =
                Long.valueOf(scheduleToAdd.calculatedExpirationTimeOrThrow().seconds());
        List<Schedule> byExpiration = schedulesByExpirationMutable.get(expirationSecond);
        if (byExpiration == null) {
            byExpiration = new LinkedList<>();
        }
        byExpiration.add(scheduleToAdd);
        schedulesByExpirationMutable.put(expirationSecond, byExpiration);
    }

    @NonNull
    private Schedule markDeleted(final Schedule schedule, final Instant consensusTime) {
        final Timestamp consensusTimestamp = new Timestamp(consensusTime.getEpochSecond(), consensusTime.getNano());
        return new Schedule(
                true,
                schedule.executed(),
                schedule.waitForExpiry(),
                schedule.memo(),
                schedule.id(),
                schedule.schedulerAccount(),
                schedule.payerAccount(),
                schedule.adminKey(),
                schedule.scheduleValidStart(),
                schedule.expirationTimeProvided(),
                schedule.calculatedExpirationTime(),
                consensusTimestamp,
                schedule.scheduledTransaction(),
                schedule.originalCreateTransaction(),
                schedule.signatories());
    }
}
