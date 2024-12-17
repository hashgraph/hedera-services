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
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduledCounts;
import com.hedera.hapi.node.state.schedule.ScheduledOrder;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A writable store that wraps a writable key-value state and supports operations required to create or update
 * schedule objects as a result of ScheduleCreate, ScheduleSign, or ScheduleDelete transactions.
 */
public class WritableScheduleStoreImpl extends ReadableScheduleStoreImpl implements WritableScheduleStore {
    private static final Logger logger = LogManager.getLogger(WritableScheduleStoreImpl.class);

    private final WritableKVState<ScheduleID, Schedule> schedulesByIdMutable;
    private final WritableKVState<ProtoBytes, ScheduleID> scheduleIdByEqualityMutable;
    private final WritableKVState<TimestampSeconds, ScheduledCounts> scheduleCountsMutable;
    private final WritableKVState<TimestampSeconds, ThrottleUsageSnapshots> scheduleUsagesMutable;
    private final WritableKVState<ScheduledOrder, ScheduleID> scheduleOrdersMutable;

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
        requireNonNull(configuration);
        requireNonNull(storeMetricsService);

        schedulesByIdMutable = states.get(V0490ScheduleSchema.SCHEDULES_BY_ID_KEY);
        scheduleCountsMutable = states.get(V0570ScheduleSchema.SCHEDULED_COUNTS_KEY);
        scheduleOrdersMutable = states.get(V0570ScheduleSchema.SCHEDULED_ORDERS_KEY);
        scheduleUsagesMutable = states.get(V0570ScheduleSchema.SCHEDULED_USAGES_KEY);
        scheduleIdByEqualityMutable = states.get(V0570ScheduleSchema.SCHEDULE_ID_BY_EQUALITY_KEY);

        final long maxCapacity =
                configuration.getConfigData(SchedulingConfig.class).maxNumber();
        final var storeMetrics = storeMetricsService.get(StoreType.SCHEDULE, maxCapacity);
        schedulesByIdMutable.setMetrics(storeMetrics);
    }

    /**
     * Delete a given schedule from this state.
     * Given the ID of a schedule and a consensus time, delete that ID from this state as of the
     * consensus time {@link Instant} provided.
     * @param scheduleId The ID of a schedule to be deleted.
     * @param consensusTime The current consensus time
     * @return the {@link Schedule} marked as deleted
     * @throws IllegalStateException if the {@link ScheduleID} to be deleted is not present in this state
     */
    @Override
    public @NonNull Schedule delete(@Nullable final ScheduleID scheduleId, @NonNull final Instant consensusTime) {
        requireNonNull(consensusTime);
        requireNonNull(scheduleId);
        final var schedule = schedulesByIdMutable.getForModify(scheduleId);
        if (schedule == null) {
            throw new IllegalStateException("Schedule to be deleted, %1$s, not found in state.".formatted(scheduleId));
        }
        final var deletedSchedule = markDeleted(schedule, consensusTime);
        schedulesByIdMutable.put(scheduleId, deletedSchedule);
        return deletedSchedule;
    }

    @Override
    public Schedule getForModify(@Nullable final ScheduleID scheduleId) {
        final Schedule result;
        if (scheduleId != null) {
            result = schedulesByIdMutable.getForModify(scheduleId);
        } else {
            result = null;
        }
        return result;
    }

    @Override
    public void put(@NonNull final Schedule schedule) {
        requireNonNull(schedule);
        final var scheduleId = schedule.scheduleIdOrThrow();
        final var extant = schedulesByIdMutable.get(scheduleId);
        schedulesByIdMutable.put(scheduleId, schedule);
        // Updating a schedule that already exists in the store has no other side-effects
        if (extant != null) {
            return;
        }
        final var equalityKey = new ProtoBytes(ScheduleStoreUtility.calculateBytesHash(schedule));
        scheduleIdByEqualityMutable.put(equalityKey, scheduleId);
        final var second = schedule.calculatedExpirationSecond();
        final var countsKey = new TimestampSeconds(second);
        final var oldCounts = scheduleCountsMutable.get(countsKey);
        final var counts = oldCounts == null
                ? new ScheduledCounts(1, 0)
                : new ScheduledCounts(oldCounts.numberScheduled() + 1, oldCounts.numberProcessed());
        scheduleCountsMutable.put(countsKey, counts);
        final var orderKey = new ScheduledOrder(second, counts.numberScheduled() - 1);
        scheduleOrdersMutable.put(orderKey, schedule.scheduleIdOrThrow());
    }

    @Override
    public boolean purgeByOrder(@NonNull final ScheduledOrder order) {
        requireNonNull(order);
        final var scheduleId = getByOrder(order);
        if (scheduleId != null) {
            final var key = new TimestampSeconds(order.expirySecond());
            final var counts = requireNonNull(scheduleCountsMutable.get(key));
            if (order.orderNumber() != counts.numberProcessed()) {
                throw new IllegalStateException("Order %s is not next in counts %s".formatted(order, counts));
            }
            purge(scheduleId);
            scheduleOrdersMutable.remove(order);
            final var newCounts = counts.copyBuilder()
                    .numberProcessed(counts.numberProcessed() + 1)
                    .build();
            if (newCounts.numberProcessed() < newCounts.numberScheduled()) {
                scheduleCountsMutable.put(key, newCounts);
                return false;
            } else {
                scheduleCountsMutable.remove(key);
                scheduleUsagesMutable.remove(key);
            }
        }
        return true;
    }

    @Override
    public void trackUsage(final long consensusSecond, @NonNull final ThrottleUsageSnapshots usageSnapshots) {
        requireNonNull(usageSnapshots);
        scheduleUsagesMutable.put(new TimestampSeconds(consensusSecond), usageSnapshots);
    }

    @Override
    public void purgeExpiredRangeClosed(final long start, final long end) {
        for (long i = start; i <= end; i++) {
            final var countsAndUsagesKey = new TimestampSeconds(i);
            final var counts = scheduleCountsMutable.get(countsAndUsagesKey);
            if (counts != null) {
                for (int j = 0, n = counts.numberScheduled(); j < n; j++) {
                    final var orderKey = new ScheduledOrder(i, j);
                    final var scheduleId = requireNonNull(scheduleOrdersMutable.get(orderKey));
                    purge(scheduleId);
                    scheduleOrdersMutable.remove(orderKey);
                }
                scheduleCountsMutable.remove(countsAndUsagesKey);
                scheduleUsagesMutable.remove(countsAndUsagesKey);
            }
        }
    }

    /**
     * Purge a schedule from the store.
     *
     * @param scheduleId The ID of the schedule to purge
     */
    private void purge(@NonNull final ScheduleID scheduleId) {
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
}
