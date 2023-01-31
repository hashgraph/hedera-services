/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.stats;

import static com.hedera.services.stats.ServicesStatsManager.RUNNING_AVG_FORMAT;
import static com.hedera.services.stats.ServicesStatsManager.STAT_CATEGORY;

import com.google.common.annotations.VisibleForTesting;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.system.Platform;

public class MiscRunningAvgs {
    final double halfLife;
    private RunningAverageMetric gasPerConsSec;
    private RunningAverageMetric handledSubmitMessageSize;
    private RunningAverageMetric writeQueueSizeRecordStream;
    private RunningAverageMetric hashQueueSizeRecordStream;

    public MiscRunningAvgs(final double halfLife) {
        this.halfLife = halfLife;
    }

    public void registerWith(final Platform platform) {
        gasPerConsSec =
                platform.getMetrics()
                        .getOrCreate(
                                new RunningAverageMetric.Config(
                                                STAT_CATEGORY, Names.GAS_PER_CONSENSUS_SEC)
                                        .withDescription(Descriptions.GAS_PER_CONSENSUS_SEC)
                                        .withFormat(RUNNING_AVG_FORMAT)
                                        .withHalfLife(halfLife));
        handledSubmitMessageSize =
                platform.getMetrics()
                        .getOrCreate(
                                new RunningAverageMetric.Config(
                                                STAT_CATEGORY, Names.HANDLED_SUBMIT_MESSAGE_SIZE)
                                        .withDescription(Descriptions.HANDLED_SUBMIT_MESSAGE_SIZE)
                                        .withFormat(RUNNING_AVG_FORMAT)
                                        .withHalfLife(halfLife));
        writeQueueSizeRecordStream =
                platform.getMetrics()
                        .getOrCreate(
                                new RunningAverageMetric.Config(
                                                STAT_CATEGORY, Names.WRITE_QUEUE_SIZE_RECORD_STREAM)
                                        .withDescription(
                                                Descriptions.WRITE_QUEUE_SIZE_RECORD_STREAM)
                                        .withFormat(RUNNING_AVG_FORMAT)
                                        .withHalfLife(halfLife));
        hashQueueSizeRecordStream =
                platform.getMetrics()
                        .getOrCreate(
                                new RunningAverageMetric.Config(
                                                STAT_CATEGORY, Names.HASH_QUEUE_SIZE_RECORD_STREAM)
                                        .withDescription(Descriptions.HASH_QUEUE_SIZE_RECORD_STREAM)
                                        .withFormat(RUNNING_AVG_FORMAT)
                                        .withHalfLife(halfLife));
    }

    public void recordHandledSubmitMessageSize(final int bytes) {
        handledSubmitMessageSize.update(bytes);
    }

    public void writeQueueSizeRecordStream(final int num) {
        writeQueueSizeRecordStream.update(num);
    }

    public void hashQueueSizeRecordStream(final int num) {
        hashQueueSizeRecordStream.update(num);
    }

    public void recordGasPerConsSec(final long gas) {
        gasPerConsSec.update(gas);
    }

    public static final class Names {
        static final String GAS_PER_CONSENSUS_SEC = "gasPerConsSec";
        static final String HANDLED_SUBMIT_MESSAGE_SIZE = "avgHdlSubMsgSize";

        static final String WRITE_QUEUE_SIZE_RECORD_STREAM = "writeQueueSizeRecordStream";
        static final String HASH_QUEUE_SIZE_RECORD_STREAM = "hashQueueSizeRecordStream";

        private Names() {
            throw new UnsupportedOperationException("Utility Class");
        }
    }

    public static final class Descriptions {
        static final String GAS_PER_CONSENSUS_SEC =
                "average EVM gas used per second of consensus time";
        static final String HANDLED_SUBMIT_MESSAGE_SIZE =
                "average size of the handled HCS submit message transaction";
        static final String WRITE_QUEUE_SIZE_RECORD_STREAM =
                "size of the queue from which we take records and write to RecordStream file";
        static final String HASH_QUEUE_SIZE_RECORD_STREAM =
                "size of working queue for calculating hash and runningHash";

        private Descriptions() {
            throw new UnsupportedOperationException("Utility Class");
        }
    }

    @VisibleForTesting
    void setGasPerConsSec(RunningAverageMetric gasPerConsSec) {
        this.gasPerConsSec = gasPerConsSec;
    }

    @VisibleForTesting
    void setHandledSubmitMessageSize(RunningAverageMetric handledSubmitMessageSize) {
        this.handledSubmitMessageSize = handledSubmitMessageSize;
    }

    @VisibleForTesting
    void setWriteQueueSizeRecordStream(RunningAverageMetric writeQueueSizeRecordStream) {
        this.writeQueueSizeRecordStream = writeQueueSizeRecordStream;
    }

    @VisibleForTesting
    void setHashQueueSizeRecordStream(RunningAverageMetric hashQueueSizeRecordStream) {
        this.hashQueueSizeRecordStream = hashQueueSizeRecordStream;
    }
}
