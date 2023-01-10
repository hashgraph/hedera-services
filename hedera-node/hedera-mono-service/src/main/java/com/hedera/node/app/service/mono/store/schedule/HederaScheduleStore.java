/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.store.schedule;

/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import static com.hedera.node.app.service.mono.store.CreationResult.failure;
import static com.hedera.node.app.service.mono.store.CreationResult.success;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.readableId;
import static com.hedera.node.app.service.mono.utils.EntityNum.fromScheduleId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_PAYER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_EXPIRATION_TIME_MUST_BE_HIGHER_THAN_CONSENSUS_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_EXPIRATION_TIME_TOO_FAR_IN_FUTURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_PENDING_EXPIRATION;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactions;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleSecondVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.temporal.SecondSinceEpocVirtualKey;
import com.hedera.node.app.service.mono.store.CreationResult;
import com.hedera.node.app.service.mono.store.HederaStore;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.collections.impl.list.mutable.primitive.LongArrayList;

/** Provides a managing store for Scheduled Entities. */
@Singleton
public final class HederaScheduleStore extends HederaStore implements ScheduleStore {
    private static final Logger log = LogManager.getLogger(HederaScheduleStore.class);

    static final ScheduleID NO_PENDING_ID = ScheduleID.getDefaultInstance();

    private final GlobalDynamicProperties properties;
    private final Supplier<MerkleScheduledTransactions> schedules;

    ScheduleID pendingId = NO_PENDING_ID;
    ScheduleVirtualValue pendingCreation;

    @Inject
    public HederaScheduleStore(
            final GlobalDynamicProperties properties,
            final EntityIdSource ids,
            final Supplier<MerkleScheduledTransactions> schedules) {
        super(ids);
        this.schedules = schedules;
        this.properties = properties;
    }

    @Override
    public ScheduleVirtualValue get(final ScheduleID id) {
        throwIfMissing(id);

        return pendingId.equals(id)
                ? pendingCreation
                : schedules.get().byId().get(new EntityNumVirtualKey(fromScheduleId(id)));
    }

    @Override
    public ScheduleVirtualValue getNoError(final ScheduleID id) {
        return pendingId.equals(id)
                ? pendingCreation
                : schedules.get().byId().get(new EntityNumVirtualKey(fromScheduleId(id)));
    }

    @Override
    public boolean exists(final ScheduleID id) {
        return (isCreationPending() && pendingId.equals(id))
                || schedules.get().byId().containsKey(new EntityNumVirtualKey(fromScheduleId(id)));
    }

    @Override
    public void apply(final ScheduleID id, final Consumer<ScheduleVirtualValue> change) {
        throwIfMissing(id);

        if (id.equals(pendingId)) {
            applyProvisionally(change);
            return;
        }

        final var key = fromScheduleId(id);
        final var virtualKey = new EntityNumVirtualKey(key);
        final var schedule = schedules.get().byId().get(virtualKey).asWritable();
        try {
            change.accept(schedule);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Schedule change failed unexpectedly!", e);
        } finally {
            schedules.get().byId().put(virtualKey, schedule);
        }
    }

    private void applyProvisionally(final Consumer<ScheduleVirtualValue> change) {
        change.accept(pendingCreation);
    }

    @Override
    public CreationResult<ScheduleID> createProvisionally(
            final ScheduleVirtualValue schedule, final RichInstant consensusTime) {
        if (!properties.schedulingWhitelist().contains(schedule.scheduledFunction())) {
            return failure(SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
        }

        if (properties.schedulingLongTermEnabled() && (schedule.expirationTimeProvided() != null)) {

            if (schedule.expirationTimeProvided().getSeconds() <= consensusTime.getSeconds()) {
                return failure(SCHEDULE_EXPIRATION_TIME_MUST_BE_HIGHER_THAN_CONSENSUS_TIME);
            }

            if (schedule.expirationTimeProvided()
                    .isAfter(
                            new RichInstant(
                                    consensusTime.getSeconds()
                                            + properties.schedulingMaxExpirationFutureSeconds(),
                                    0))) {
                return failure(SCHEDULE_EXPIRATION_TIME_TOO_FAR_IN_FUTURE);
            }

            schedule.setCalculatedExpirationTime(schedule.expirationTimeProvided());

        } else {
            schedule.setCalculatedExpirationTime(
                    new RichInstant(
                            consensusTime.getSeconds() + properties.scheduledTxExpiryTimeSecs(),
                            0));
        }

        if (!properties.schedulingLongTermEnabled()) {
            schedule.setCalculatedWaitForExpiry(false);
        } else {
            schedule.setCalculatedWaitForExpiry(schedule.waitForExpiryProvided());
        }

        var validity = OK;
        if (schedule.hasExplicitPayer()) {
            validity = usableOrElse(schedule.payer().toGrpcAccountId(), INVALID_SCHEDULE_PAYER_ID);
        }
        if (validity == OK) {
            validity =
                    usableOrElse(
                            schedule.schedulingAccount().toGrpcAccountId(),
                            INVALID_SCHEDULE_ACCOUNT_ID);
        }
        if (validity != OK) {
            return failure(validity);
        }

        pendingId = ids.newScheduleId(schedule.schedulingAccount().toGrpcAccountId());
        pendingCreation = schedule;

        return success(pendingId);
    }

    @Override
    public ResponseCodeEnum deleteAt(final ScheduleID id, final Instant consensusTime) {
        final var status = usabilityCheck(id, true, consensusTime);
        if (status != OK) {
            return status;
        }

        apply(id, schedule -> schedule.markDeleted(consensusTime));
        return OK;
    }

    @Override
    public ResponseCodeEnum delete(final ScheduleID id) {
        throw new UnsupportedOperationException("Only deleteAt() is usable with schedules");
    }

    @Override
    public void commitCreation() {
        throwIfNoCreationPending();

        final var id = new EntityNumVirtualKey(fromScheduleId(pendingId));
        schedules.get().byId().put(id, pendingCreation);

        final var secondKey =
                new SecondSinceEpocVirtualKey(
                        pendingCreation.calculatedExpirationTime().getSeconds());

        var bySecond = schedules.get().byExpirationSecond().get(secondKey);

        if (bySecond == null) {
            bySecond = new ScheduleSecondVirtualValue();
        } else {
            bySecond = bySecond.asWritable();
        }

        bySecond.add(
                pendingCreation.calculatedExpirationTime(), new LongArrayList(id.getKeyAsLong()));

        schedules.get().byExpirationSecond().put(secondKey, bySecond);

        final var equalityKey = new ScheduleEqualityVirtualKey(pendingCreation.equalityCheckKey());

        var byEquality = schedules.get().byEquality().get(equalityKey);

        if (byEquality == null) {
            byEquality = new ScheduleEqualityVirtualValue();
        } else {
            byEquality = byEquality.asWritable();
        }

        byEquality.add(pendingCreation.equalityCheckValue(), id.getKeyAsLong());

        schedules.get().byEquality().put(equalityKey, byEquality);

        if (schedules.get().getCurrentMinSecond()
                > pendingCreation.calculatedExpirationTime().getSeconds()) {
            schedules
                    .get()
                    .setCurrentMinSecond(pendingCreation.calculatedExpirationTime().getSeconds());
        }

        resetPendingCreation();
    }

    @Override
    public void rollbackCreation() {
        throwIfNoCreationPending();
        super.rollbackCreation();
        resetPendingCreation();
    }

    @Override
    public boolean isCreationPending() {
        return pendingId != NO_PENDING_ID;
    }

    @Override
    public Pair<ScheduleID, ScheduleVirtualValue> lookupSchedule(final byte[] bodyBytes) {
        final var schedule = ScheduleVirtualValue.from(bodyBytes, 0L);

        if (isCreationPending() && schedule.equals(pendingCreation)) {
            return Pair.of(pendingId, pendingCreation);
        }

        final var equalityKey = new ScheduleEqualityVirtualKey(schedule.equalityCheckKey());
        final var byEquality = schedules.get().byEquality().get(equalityKey);
        if (byEquality != null) {
            final var existingId = byEquality.getIds().get(schedule.equalityCheckValue());

            if (existingId != null) {

                final var extantId = EntityNum.fromLong(existingId).toGrpcScheduleId();

                if (exists(extantId)) {
                    return Pair.of(extantId, get(extantId));
                }
            }
        }

        return Pair.of(null, schedule);
    }

    @Override
    public ResponseCodeEnum preMarkAsExecuted(final ScheduleID id) {
        final var status = usabilityCheck(id, false, null);
        if (status != OK) {
            return status;
        }
        return OK;
    }

    @Override
    public ResponseCodeEnum markAsExecuted(final ScheduleID id, final Instant consensusTime) {
        final var status = usabilityCheck(id, false, null);
        if (status != OK) {
            return status;
        }
        apply(id, schedule -> schedule.markExecuted(consensusTime));
        return OK;
    }

    @Override
    public void expire(final ScheduleID id) {
        if (id.equals(pendingId)) {
            throw new IllegalArgumentException(
                    String.format(
                            "Argument 'id=%s' refers to a pending creation!", readableId(id)));
        }

        throwIfMissing(id);

        final var idToDelete = new EntityNumVirtualKey(fromScheduleId(id));
        final var existingSchedule = schedules.get().byId().remove(idToDelete);

        if (existingSchedule != null) {

            final var secondKey =
                    new SecondSinceEpocVirtualKey(
                            existingSchedule.calculatedExpirationTime().getSeconds());

            var bySecond = schedules.get().byExpirationSecond().get(secondKey);

            if (bySecond != null) {
                bySecond = bySecond.asWritable();
                bySecond.removeId(
                        existingSchedule.calculatedExpirationTime(), idToDelete.getKeyAsLong());

                if (bySecond.getIds().isEmpty()) {
                    schedules.get().byExpirationSecond().remove(secondKey);
                } else {
                    schedules.get().byExpirationSecond().put(secondKey, bySecond);
                }
            }

            final var equalityKey =
                    new ScheduleEqualityVirtualKey(existingSchedule.equalityCheckKey());

            var byEquality = schedules.get().byEquality().get(equalityKey);

            if (byEquality != null) {
                byEquality = byEquality.asWritable();
                byEquality.remove(existingSchedule.equalityCheckValue(), idToDelete.getKeyAsLong());

                if (byEquality.getIds().isEmpty()) {
                    schedules.get().byEquality().remove(equalityKey);
                } else {
                    schedules.get().byEquality().put(equalityKey, byEquality);
                }
            }
        }
    }

    @VisibleForTesting
    boolean advanceCurrentMinSecond(final Instant consensusTime) {

        boolean changed = false;

        long curSecond = schedules.get().getCurrentMinSecond();

        while ((consensusTime.getEpochSecond() > curSecond)
                && (!schedules
                        .get()
                        .byExpirationSecond()
                        .containsKey(new SecondSinceEpocVirtualKey(curSecond)))) {

            ++curSecond;
            changed = true;
        }

        if (changed) {
            schedules.get().setCurrentMinSecond(curSecond);
            return true;
        }

        return false;
    }

    @Override
    public List<ScheduleID> nextSchedulesToExpire(final Instant consensusTime) {

        advanceCurrentMinSecond(consensusTime);

        final long curSecond = schedules.get().getCurrentMinSecond();

        if (!shouldProcessSecond(consensusTime, curSecond)) {
            return Collections.emptyList();
        }

        final var bySecondKey = new SecondSinceEpocVirtualKey(curSecond);

        var bySecond = schedules.get().byExpirationSecond().get(bySecondKey);

        final List<ScheduleID> list = new ArrayList<>();
        final List<Pair<RichInstant, Long>> toRemove = new ArrayList<>();

        if (bySecond != null) {
            outer:
            for (final var entry : bySecond.getIds().entrySet()) {
                final var instant = entry.getKey();
                final var ids = entry.getValue();
                for (int i = 0; i < ids.size(); ++i) {
                    final var id = ids.get(i);
                    final var scheduleId = EntityNum.fromLong(id).toGrpcScheduleId();

                    final var schedule = getNoError(scheduleId);

                    if (schedule == null) {
                        log.error(
                                "bySecond contained a schedule that does not exist! Removing it!"
                                        + " second={}, id={}",
                                curSecond,
                                scheduleId);
                        toRemove.add(Pair.of(instant, id));

                    } else if (schedule.calculatedExpirationTime().getSeconds() != curSecond) {
                        log.error(
                                "bySecond contained a schedule in the wrong spot! Removing and"
                                        + " expiring it! spot={}, id={}, schedule={}",
                                curSecond,
                                scheduleId,
                                schedule);
                        toRemove.add(Pair.of(instant, id));
                        list.add(scheduleId);

                    } else if (schedule.isDeleted() || schedule.isExecuted()) {
                        list.add(scheduleId);

                    } else {
                        break outer;
                    }
                }
            }

            if ((!toRemove.isEmpty()) || bySecond.getIds().isEmpty()) {
                bySecond = bySecond.asWritable();
                for (final var p : toRemove) {
                    bySecond.removeId(p.getKey(), p.getValue());
                }

                if (bySecond.getIds().size() <= 0) {
                    log.error("bySecond was unexpectedly empty! Removing it! second={}", curSecond);
                    schedules.get().byExpirationSecond().remove(bySecondKey);
                } else {
                    schedules.get().byExpirationSecond().put(bySecondKey, bySecond);
                }
            }
        }

        return list;
    }

    @Override
    @Nullable
    public ScheduleID nextScheduleToEvaluate(final Instant consensusTime) {

        final long curSecond = schedules.get().getCurrentMinSecond();

        if (!shouldProcessSecond(consensusTime, curSecond)) {
            return null;
        }

        final var bySecond =
                schedules.get().byExpirationSecond().get(new SecondSinceEpocVirtualKey(curSecond));

        if (bySecond != null) {
            for (final var ids : bySecond.getIds().values()) {
                if (ids.size() > 0) {
                    final var scheduleId = EntityNum.fromLong(ids.get(0)).toGrpcScheduleId();

                    final var schedule = getNoError(scheduleId);

                    if (schedule == null) {
                        log.error(
                                "bySecond contained a schedule that does not exist! Not evaluating"
                                        + " it! second={}, id={}",
                                curSecond,
                                scheduleId);
                        return null;
                    }

                    if (schedule.calculatedExpirationTime().getSeconds() != curSecond) {
                        log.error(
                                "bySecond contained a schedule in the wrong spot! Not evaluating"
                                        + " it! spot={}, id={}, schedule={}",
                                curSecond,
                                scheduleId,
                                schedule);
                        return null;
                    }

                    if (schedule.isDeleted() || schedule.isExecuted()) {
                        return null;
                    } else {
                        return scheduleId;
                    }
                }
            }
        }

        return null;
    }

    @Override
    public ScheduleSecondVirtualValue getBySecond(final long second) {
        return schedules.get().byExpirationSecond().get(new SecondSinceEpocVirtualKey(second));
    }

    private boolean shouldProcessSecond(final Instant consensusTime, final long curSecond) {
        return consensusTime.getEpochSecond() > curSecond;
    }

    private void resetPendingCreation() {
        pendingId = NO_PENDING_ID;
        pendingCreation = null;
    }

    private void throwIfNoCreationPending() {
        if (pendingId == NO_PENDING_ID) {
            throw new IllegalStateException("No pending schedule creation!");
        }
    }

    private ResponseCodeEnum usabilityCheck(
            final ScheduleID id,
            final boolean requiresMutability,
            @Nullable final Instant consensusTime) {
        final var idRes = resolve(id);
        if (idRes == MISSING_SCHEDULE) {
            return INVALID_SCHEDULE_ID;
        }

        final var schedule = get(id);
        if (schedule.isDeleted()) {
            return SCHEDULE_ALREADY_DELETED;
        }
        if (schedule.isExecuted()) {
            return SCHEDULE_ALREADY_EXECUTED;
        }

        if ((consensusTime != null)
                && consensusTime.isAfter(schedule.calculatedExpirationTime().toJava())) {
            if (!properties.schedulingLongTermEnabled()) {
                return INVALID_SCHEDULE_ID;
            }
            return SCHEDULE_PENDING_EXPIRATION;
        }

        if (requiresMutability && schedule.adminKey().isEmpty()) {
            return SCHEDULE_IS_IMMUTABLE;
        }

        return OK;
    }

    private void throwIfMissing(final ScheduleID id) {
        if (!exists(id)) {
            throw new IllegalArgumentException(
                    String.format(
                            "Argument 'id=%s' does not refer to an extant scheduled entity!",
                            readableId(id)));
        }
    }
}
