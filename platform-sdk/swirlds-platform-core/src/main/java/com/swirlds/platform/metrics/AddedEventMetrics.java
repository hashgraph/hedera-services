/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.metrics;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_3;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_13_2;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_16_0;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_16_2;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_17_1;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_5_3;
import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;
import static com.swirlds.common.metrics.Metrics.PLATFORM_CATEGORY;
import static com.swirlds.common.units.UnitConstants.NANOSECONDS_TO_SECONDS;

import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.observers.EventAddedObserver;
import com.swirlds.platform.stats.AverageStat;
import java.time.temporal.ChronoUnit;

/**
 * An {@link EventAddedObserver} that maintains all metrics which need to be updated on a new event
 */
public class AddedEventMetrics implements EventAddedObserver {

    private final NodeId selfId;

    private static final SpeedometerMetric.Config EVENTS_CREATED_PER_SECOND_CONFIG = new SpeedometerMetric.Config(
                    PLATFORM_CATEGORY, "cEvents/sec")
            .withDescription("number of events per second created by this node")
            .withFormat(FORMAT_16_2);
    private final SpeedometerMetric eventsCreatedPerSecond;

    private static final RunningAverageMetric.Config AVG_CREATED_RECEIVED_TIME_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "secC2R")
            .withDescription("time from another member creating an event to receiving it and "
                    + "verifying the signature (in seconds)")
            .withFormat(FORMAT_10_3);
    private final RunningAverageMetric avgCreatedReceivedTime;

    private static final SpeedometerMetric.Config EVENTS_PER_SECOND_CONFIG = new SpeedometerMetric.Config(
                    PLATFORM_CATEGORY, "events/sec")
            .withDescription("number of unique events received per second (created by self and others)")
            .withFormat(FORMAT_16_2);
    private final SpeedometerMetric eventsPerSecond;

    private static final RunningAverageMetric.Config AVG_BYTES_PER_TRANSACTION_SYS_CONFIG =
            new RunningAverageMetric.Config(INTERNAL_CATEGORY, "bytes/trans_sys")
                    .withDescription("number of bytes in each system transaction")
                    .withFormat(FORMAT_16_0);
    private final RunningAverageMetric avgBytesPerTransactionSys;

    private static final RunningAverageMetric.Config AVG_BYTES_PER_TRANSACTION_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "bytes/trans")
            .withDescription("number of bytes in each transactions")
            .withFormat(FORMAT_16_0);
    private final RunningAverageMetric avgBytesPerTransaction;

    private static final RunningAverageMetric.Config AVG_TRANSACTIONS_PER_EVENT_CONFIG =
            new RunningAverageMetric.Config(PLATFORM_CATEGORY, "trans/event")
                    .withDescription("number of app transactions in each event")
                    .withFormat(FORMAT_17_1);
    private final RunningAverageMetric avgTransactionsPerEvent;

    private static final RunningAverageMetric.Config AVG_TRANSACTIONS_PER_EVENT_SYS_CONFIG =
            new RunningAverageMetric.Config(INTERNAL_CATEGORY, "trans/event_sys")
                    .withDescription("number of system transactions in each event")
                    .withFormat(FORMAT_17_1);
    private final RunningAverageMetric avgTransactionsPerEventSys;

    private static final String DETAILS = "(from unique events created by self and others)";
    private static final SpeedometerMetric.Config BYTES_PER_SECOND_TRANS_CONFIG = new SpeedometerMetric.Config(
                    PLATFORM_CATEGORY, "bytes/sec_trans")
            .withDescription("number of bytes in the transactions received per second " + DETAILS)
            .withFormat(FORMAT_16_2);
    private final SpeedometerMetric bytesPerSecondTrans;

    private static final SpeedometerMetric.Config BYTES_PER_SECOND_SYS_CONFIG = new SpeedometerMetric.Config(
                    INTERNAL_CATEGORY, "bytes/sec_sys")
            .withDescription("number of bytes in the system transactions received per second " + DETAILS)
            .withFormat(FORMAT_16_2);
    private final SpeedometerMetric bytesPerSecondSys;

    private static final SpeedometerMetric.Config TRANSACTIONS_PER_SECOND_CONFIG = new SpeedometerMetric.Config(
                    PLATFORM_CATEGORY, "trans/sec")
            .withDescription("number of app transactions received per second " + DETAILS)
            .withFormat(FORMAT_13_2);
    private final SpeedometerMetric transactionsPerSecond;

    private static final SpeedometerMetric.Config TRANSACTIONS_PER_SECOND_SYS_CONFIG = new SpeedometerMetric.Config(
                    INTERNAL_CATEGORY, "trans/sec_sys")
            .withDescription("number of system transactions received per second " + DETAILS)
            .withFormat(FORMAT_13_2);
    private final SpeedometerMetric transactionsPerSecondSys;

    private static final Counter.Config NUM_TRANS_CONFIG =
            new Counter.Config(INTERNAL_CATEGORY, "trans").withDescription("number of transactions received so far");
    private final Counter numTrans;

    private final AverageStat averageOtherParentAgeDiff;

    /**
     * The constructor of {@code AddedEventMetrics}
     *
     * @param selfId
     * 		the {@link NodeId} of this node
     * @param metrics
     * 		a reference to the metrics-system
     * @throws IllegalArgumentException if one of the parameters is {@code null}
     */
    public AddedEventMetrics(final NodeId selfId, final Metrics metrics) {
        this.selfId = CommonUtils.throwArgNull(selfId, "selfId");
        CommonUtils.throwArgNull(metrics, "metrics");

        eventsCreatedPerSecond = metrics.getOrCreate(EVENTS_CREATED_PER_SECOND_CONFIG);
        averageOtherParentAgeDiff = new AverageStat(
                metrics,
                PLATFORM_CATEGORY,
                "opAgeDiff",
                "average age difference (in generations) between an event created by this node and its other parent",
                FORMAT_5_3,
                AverageStat.WEIGHT_VOLATILE);
        avgCreatedReceivedTime = metrics.getOrCreate(AVG_CREATED_RECEIVED_TIME_CONFIG);
        eventsPerSecond = metrics.getOrCreate(EVENTS_PER_SECOND_CONFIG);
        avgBytesPerTransactionSys = metrics.getOrCreate(AVG_BYTES_PER_TRANSACTION_SYS_CONFIG);
        avgBytesPerTransaction = metrics.getOrCreate(AVG_BYTES_PER_TRANSACTION_CONFIG);
        avgTransactionsPerEvent = metrics.getOrCreate(AVG_TRANSACTIONS_PER_EVENT_CONFIG);
        avgTransactionsPerEventSys = metrics.getOrCreate(AVG_TRANSACTIONS_PER_EVENT_SYS_CONFIG);
        bytesPerSecondTrans = metrics.getOrCreate(BYTES_PER_SECOND_TRANS_CONFIG);
        bytesPerSecondSys = metrics.getOrCreate(BYTES_PER_SECOND_SYS_CONFIG);
        transactionsPerSecond = metrics.getOrCreate(TRANSACTIONS_PER_SECOND_CONFIG);
        transactionsPerSecondSys = metrics.getOrCreate(TRANSACTIONS_PER_SECOND_SYS_CONFIG);
        numTrans = metrics.getOrCreate(NUM_TRANS_CONFIG);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void eventAdded(final EventImpl event) {
        if (event.isCreatedBy(selfId)) {
            eventsCreatedPerSecond.cycle();
            if (event.getBaseEventHashedData().hasOtherParent()) {
                averageOtherParentAgeDiff.update(event.getGeneration() - event.getOtherParentGen());
            }
        } else {
            avgCreatedReceivedTime.update(
                    event.getTimeCreated().until(event.getBaseEvent().getTimeReceived(), ChronoUnit.NANOS)
                            * NANOSECONDS_TO_SECONDS);
        }

        // count the unique events in the hashgraph
        eventsPerSecond.cycle();

        // record stats for all transactions in this event
        final ConsensusTransaction[] trans = event.getTransactions();
        final int numTransactions = (trans == null ? 0 : trans.length);

        // we have already ensured this isn't a duplicate event, so record all the stats on it:

        // count the bytes in the transactions, and bytes per second, and transactions per event
        // for both app transactions and system transactions.
        // Handle system transactions
        int appSize = 0;
        int sysSize = 0;
        int numAppTrans = 0;
        int numSysTrans = 0;
        for (int i = 0; i < numTransactions; i++) {
            if (trans[i].isSystem()) {
                numSysTrans++;
                sysSize += trans[i].getSerializedLength();
                avgBytesPerTransactionSys.update(trans[i].getSerializedLength());
            } else {
                numAppTrans++;
                appSize += trans[i].getSerializedLength();
                avgBytesPerTransaction.update(trans[i].getSerializedLength());
            }
        }
        avgTransactionsPerEvent.update(numAppTrans);
        avgTransactionsPerEventSys.update(numSysTrans);
        bytesPerSecondTrans.update(appSize);
        bytesPerSecondSys.update(sysSize);
        // count each transaction within that event (this is like calling cycle() numTrans times)
        transactionsPerSecond.update(numAppTrans);
        transactionsPerSecondSys.update(numSysTrans);

        // count all transactions ever in the hashgraph
        if (!event.isEmpty()) {
            numTrans.add(event.getTransactions().length);
        }
    }

    /**
     * Returns the events per seconds of this node
     *
     * @return the events per second
     */
    public double getEventsCreatedPerSecond() {
        return eventsCreatedPerSecond.get();
    }
}
