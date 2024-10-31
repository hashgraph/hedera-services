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

import static com.hedera.node.app.service.schedule.impl.ScheduleStoreUtility.add;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduleIdList;
import com.hedera.node.app.service.schedule.WritableScheduleStore;
import com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema;
import com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.metrics.StoreMetricsService.StoreType;
import com.hedera.node.config.data.SchedulingConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A writable store that wraps a writable key-value state and supports operations required to create or update
 * schedule objects as a result of ScheduleCreate, ScheduleSign, or ScheduleDelete transactions.
 */
public class WritableScheduleStoreImpl extends ReadableScheduleStoreImpl implements WritableScheduleStore {

    private static final Logger logger = LogManager.getLogger(WritableScheduleStoreImpl.class);
    private static final String SCHEDULE_NULL_FOR_DELETE_MESSAGE =
            "Request to delete null schedule ID cannot be fulfilled.";
    private static final String SCHEDULE_MISSING_FOR_DELETE_MESSAGE =
            "Schedule to be deleted, %1$s, not found in state.";
    private final WritableKVState<ScheduleID, Schedule> schedulesByIdMutable;
    private final WritableKVState<ProtoBytes, ScheduleID> scheduleIdByEqualityMutable;
    private final WritableKVState<ProtoLong, ScheduleIdList> scheduleIdsByExpirationMutable;

    /**
     * Create a new {@link WritableScheduleStoreImpl} instance.
     *
     * @param states The state to use.
     * @param configuration The configuration used to read the maximum capacity.
     * @param storeMetricsService Service that provides utilization metrics.
     */
    public WritableScheduleStoreImpl(
            @NonNull final WritableStates states,
            @NonNull final Configuration configuration,
            @NonNull final StoreMetricsService storeMetricsService) {
        super(states);
        schedulesByIdMutable = states.get(V0490ScheduleSchema.SCHEDULES_BY_ID_KEY);
        scheduleIdByEqualityMutable = states.get(V0570ScheduleSchema.SCHEDULE_ID_BY_EQUALITY_KEY);
        scheduleIdsByExpirationMutable = states.get(V0570ScheduleSchema.SCHEDULE_IDS_BY_EXPIRY_SEC_KEY);

        final long maxCapacity =
                configuration.getConfigData(SchedulingConfig.class).maxNumber();
        final var storeMetrics = storeMetricsService.get(StoreType.SCHEDULE, maxCapacity);
        schedulesByIdMutable.setMetrics(storeMetrics);
    }

    /**
     * Delete a given schedule from this state.
     * Given the ID of a schedule and a consensus time, delete that ID from this state as of the
     * consensus time {@link Instant} provided.
     * @param scheduleToDelete The ID of a schedule to be deleted.
     * @param consensusTime The current consensus time
     * @return the {@link Schedule} marked as deleted
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
            result = null;
        }
        return result;
    }

    @Override
    public void put(@NonNull final Schedule scheduleToAdd) {
        schedulesByIdMutable.put(scheduleToAdd.scheduleIdOrThrow(), scheduleToAdd);

        final ProtoBytes newHash = new ProtoBytes(ScheduleStoreUtility.calculateBytesHash(scheduleToAdd));
        scheduleIdByEqualityMutable.put(newHash, scheduleToAdd.scheduleIdOrThrow());

        // calculated expiration time is never null...
        final ProtoLong expirationSecond = new ProtoLong(scheduleToAdd.calculatedExpirationSecond());
        final ScheduleIdList inStateExpiration = scheduleIdsByExpirationMutable.get(expirationSecond);
        // we should not be modifying the scheduleIds list directly. This could cause ISS
        final var newExpiryScheduleIdList = add(scheduleToAdd.scheduleId(), inStateExpiration);
        scheduleIdsByExpirationMutable.put(expirationSecond, newExpiryScheduleIdList);
    }

    @NonNull
    private Schedule markDeleted(final Schedule schedule, final Instant consensusTime) {
        final Timestamp consensusTimestamp = new Timestamp(consensusTime.getEpochSecond(), consensusTime.getNano());
        return new Schedule(
                schedule.scheduleId(),
                true,
                schedule.executed(),
                schedule.waitForExpiry(),
                schedule.memo(),
                schedule.schedulerAccountId(),
                schedule.payerAccountId(),
                schedule.adminKey(),
                schedule.scheduleValidStart(),
                schedule.providedExpirationSecond(),
                schedule.calculatedExpirationSecond(),
                consensusTimestamp,
                schedule.scheduledTransaction(),
                schedule.originalCreateTransaction(),
                schedule.signatories());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void purgeExpiredSchedulesBetween(long firstSecondToExpire, long lastSecondToExpire) {
        for (long i = firstSecondToExpire; i <= lastSecondToExpire; i++) {
            final var second = new ProtoLong(i);
            final var scheduleIdList = scheduleIdsByExpirationMutable.get(second);
            if (scheduleIdList != null) {
                for (final var scheduleId : scheduleIdList.scheduleIds()) {
                    final var schedule = schedulesByIdMutable.get(scheduleId);
                    if (schedule != null) {
                        final ProtoBytes hash = new ProtoBytes(ScheduleStoreUtility.calculateBytesHash(schedule));
                        scheduleIdByEqualityMutable.remove(hash);
                    } else {
                        logger.error("Schedule {} not found in state schedulesByIdMutable.", scheduleId);
                    }
                    schedulesByIdMutable.remove(scheduleId);
                    logger.debug("Purging expired schedule {} from state.", scheduleId);
                }
                scheduleIdsByExpirationMutable.remove(second);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public List<Schedule> getByExpirationBetween(long firstSecondToExpire, long lastSecondToExpire) {
        final var schedules = new ArrayList<Schedule>();
        for (long i = firstSecondToExpire; i <= lastSecondToExpire; i++) {
            final var scheduleIdList = getByExpirationSecond(i);
            if (scheduleIdList != null) {
                schedules.addAll(scheduleIdList.stream().map(this::get).toList());
            }
        }
        return schedules;
    }
}
