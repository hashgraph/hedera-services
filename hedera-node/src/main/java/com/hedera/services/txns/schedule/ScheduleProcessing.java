/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.schedule;
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
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_FUTURE_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_FUTURE_THROTTLE_EXCEEDED;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.merkle.MerkleScheduledTransactions;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.throttling.TimedFunctionalityThrottling;
import com.hedera.services.throttling.annotations.ScheduleThrottle;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet;

/** Class that encapsulates some of the more complex processing of scheduled transactions. */
@Singleton
public class ScheduleProcessing {
    private static final Logger log = LogManager.getLogger(ScheduleProcessing.class);

    private final SigImpactHistorian sigImpactHistorian;
    private final ScheduleStore store;
    private final ScheduleExecutor scheduleExecutor;
    private final GlobalDynamicProperties dynamicProperties;
    private final TimedFunctionalityThrottling scheduleThrottling;
    private final Supplier<MerkleScheduledTransactions> schedules;

    SigMapScheduleClassifier classifier = new SigMapScheduleClassifier();
    SignatoryUtils.ScheduledSigningsWitness signingsWitness = SignatoryUtils::witnessScoped;
    Predicate<ScheduleVirtualValue> isFullySigned;

    @Inject
    public ScheduleProcessing(
            final SigImpactHistorian sigImpactHistorian,
            final ScheduleStore store,
            final ScheduleExecutor scheduleExecutor,
            final GlobalDynamicProperties dynamicProperties,
            final ScheduleSigsVerifier scheduleSigsVerifier,
            @ScheduleThrottle final TimedFunctionalityThrottling scheduleThrottling,
            final Supplier<MerkleScheduledTransactions> schedules) {
        this.sigImpactHistorian = sigImpactHistorian;
        this.store = store;
        this.scheduleExecutor = scheduleExecutor;
        this.dynamicProperties = dynamicProperties;
        this.scheduleThrottling = scheduleThrottling;
        isFullySigned = scheduleSigsVerifier::areAllKeysActive;
        this.schedules = schedules;
    }

    /**
     * Expires all scheduled transactions, that are in a final state, having an expiry before
     * consensusTime, and having an expiry before any transaction that is ready to execute.
     */
    public void expire(Instant consensusTime) {

        // we _really_ don't want to loop forever. If the database isn't working, then it's
        // possible. So we put
        // an upper limit on the number of iterations here.
        for (long i = 0; i < getMaxProcessingLoopIterations(); ++i) {

            List<ScheduleID> scheduleIdsToExpire = store.nextSchedulesToExpire(consensusTime);
            if (scheduleIdsToExpire.isEmpty()) {
                return;
            }

            for (var txnId : scheduleIdsToExpire) {
                store.expire(txnId);
                sigImpactHistorian.markEntityChanged(txnId.getScheduleNum());
            }
        }

        log.warn(
                "maxProcessingLoopIterations reached in expire. Waiting for next call to continue."
                        + " Scheduled Transaction expiration may be delayed.");
    }

    /**
     * Gets the next scheduled transaction that is available to execute. Scheduled transactions may
     * be expired as needed during this call.
     *
     * @param consensusTime the current consensus time
     * @param previous the previous accessor returned from this method, if available.
     * @param onlyExpire true if we are only expiring and not trying to execute anything.
     * @return the TxnAccessor of the next scheduled transaction to execute, or null if there are
     *     none.
     */
    @Nullable
    public TxnAccessor triggerNextTransactionExpiringAsNeeded(
            Instant consensusTime, @Nullable TxnAccessor previous, boolean onlyExpire) {

        LongHashSet seen = null;

        for (long i = 0; i < getMaxProcessingLoopIterations(); ++i) {

            expire(consensusTime);

            var next = store.nextScheduleToEvaluate(consensusTime);

            if (next == null) {
                return null;
            }

            // avoid creating a hash set in the normal case where we don't actually process any
            // scheduled txns
            if (seen == null) {
                seen = new LongHashSet();
                if ((previous != null) && (previous.getScheduleRef() != null)) {
                    seen.add(previous.getScheduleRef().getScheduleNum());
                }
            }

            var nextLong = next.getScheduleNum();

            if (!seen.add(nextLong)) {
                log.error("tried to process the same transaction twice! {}", next);
                throw new IllegalStateException("tried to process the same transaction twice!");
            }

            // if we were going to check throttling to prevent scheduled transactions from all
            // executing
            // rapidly after downtime, it would be done here. We don't do that currently.

            try {

                if (!this.dynamicProperties.schedulingLongTermEnabled()) {
                    // if long term is disabled, we always expire transactions that would otherwise
                    // execute autonomously
                    store.expire(next);
                    sigImpactHistorian.markEntityChanged(nextLong);
                    continue;
                }

                var schedule = store.get(next);

                if (!this.isFullySigned.test(schedule)) {

                    // expire transactions that are not ready to execute
                    store.expire(next);
                    sigImpactHistorian.markEntityChanged(nextLong);

                } else if (onlyExpire) {

                    // if we are only expiring, we have to stop processing here and return null
                    return null;

                } else {

                    var triggerResult =
                            scheduleExecutor.getTriggeredTxnAccessor(next, store, false);

                    if (triggerResult.getLeft() != OK) {
                        log.error(
                                "Scheduled transaction was not trigger-able even though it should"
                                        + " be! Expiring it. {}",
                                next);
                        store.expire(next);
                        sigImpactHistorian.markEntityChanged(nextLong);
                    } else {
                        return triggerResult.getRight();
                    }
                }
            } catch (Exception e) {
                log.error(
                        "SCHEDULED TRANSACTION SKIPPED!! Failed to triggered transaction due"
                                + " unexpected error! {}",
                        next,
                        e);

                // Immediately expire malfunctioning transactions, if we get here then there is a
                // bug.
                // We can't leave it in the db, it will prevent other scheduled transactions from
                // processing.
                store.expire(next);
                sigImpactHistorian.markEntityChanged(nextLong);
            }
        }

        log.warn(
                "maxProcessingLoopIterations reached in triggerNextTransactionExpiringAsNeeded."
                    + " Waiting for next call to continue. Scheduled Transaction expiration may be"
                    + " delayed.");

        return null;
    }

    /**
     * @param scheduleId the id for schedule
     * @param schedule a schedule to check the "future throttles" for.
     * @return an error code if there was an error, OK otherwise
     */
    public ResponseCodeEnum checkFutureThrottlesForCreate(
            final ScheduleID scheduleId, final ScheduleVirtualValue schedule) {

        if (dynamicProperties.schedulingLongTermEnabled()) {
            scheduleThrottling.resetUsage();

            final TreeMap<RichInstant, List<TxnAccessor>> transactionsInExecutionOrder =
                    new TreeMap<>();

            final var curSecond = schedule.calculatedExpirationTime().getSeconds();

            final var bySecond = store.getBySecond(curSecond);

            if (bySecond != null) {
                bySecond.getIds()
                        .values()
                        .forEach(
                                ids ->
                                        ids.forEach(
                                                id -> {
                                                    final var existingScheduleId =
                                                            EntityNum.fromLong(id)
                                                                    .toGrpcScheduleId();
                                                    final var existing =
                                                            store.getNoError(existingScheduleId);

                                                    if (existing != null) {
                                                        if (existing.calculatedExpirationTime()
                                                                        .getSeconds()
                                                                != curSecond) {
                                                            log.warn(
                                                                    "bySecond contained a schedule"
                                                                        + " in the wrong spot!"
                                                                        + " Ignoring it! spot={},"
                                                                        + " id={}, schedule={}",
                                                                    curSecond,
                                                                    id,
                                                                    existing);
                                                        } else {
                                                            var list =
                                                                    transactionsInExecutionOrder
                                                                            .computeIfAbsent(
                                                                                    existing
                                                                                            .calculatedExpirationTime(),
                                                                                    k ->
                                                                                            new ArrayList<>());
                                                            list.add(
                                                                    getTxnAccessorForThrottleCheck(
                                                                            existingScheduleId,
                                                                            existing));
                                                        }
                                                    } else {
                                                        log.warn(
                                                                "bySecond contained a schedule that"
                                                                    + " does not exist! Ignoring"
                                                                    + " it! second={}, id={}",
                                                                curSecond,
                                                                id);
                                                    }
                                                }));
            }

            var list =
                    transactionsInExecutionOrder.computeIfAbsent(
                            schedule.calculatedExpirationTime(), k -> new ArrayList<>());
            list.add(getTxnAccessorForThrottleCheck(scheduleId, schedule));

            Instant timestamp = Instant.ofEpochSecond(curSecond);
            for (var entry : transactionsInExecutionOrder.entrySet()) {
                for (var t : entry.getValue()) {
                    if (scheduleThrottling.shouldThrottleTxn(t, timestamp)) {
                        if (scheduleThrottling.wasLastTxnGasThrottled()) {
                            return SCHEDULE_FUTURE_GAS_LIMIT_EXCEEDED;
                        } else {
                            return SCHEDULE_FUTURE_THROTTLE_EXCEEDED;
                        }
                    }
                    timestamp = timestamp.plusNanos(1);
                }
            }
        }

        return OK;
    }

    /**
     * This method is meant to short-circuit processing scheduled transactions within a given second
     * after processing has already happened. It needs to be as fast as possible.
     *
     * @param consensusTime the current consensus time
     * @return true if we should process scheduled transactions for consensusTime
     */
    public boolean shouldProcessScheduledTransactions(Instant consensusTime) {
        return consensusTime.getEpochSecond() > schedules.get().getCurrentMinSecond();
    }

    /**
     * @return the max number of iterations of any loop calling
     *     triggerNextTransactionExpiringAsNeeded.
     */
    public long getMaxProcessingLoopIterations() {
        return dynamicProperties.schedulingMaxTxnPerSecond() * 10;
    }

    private TxnAccessor getTxnAccessorForThrottleCheck(
            final ScheduleID scheduleId, final ScheduleVirtualValue schedule) {
        try {
            return scheduleExecutor.getTxnAccessor(scheduleId, schedule, false);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException(e);
        }
    }
}
