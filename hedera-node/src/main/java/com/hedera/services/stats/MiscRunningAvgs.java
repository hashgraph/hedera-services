/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
    private RunningAverageMetric gasPerConsSec;
    private RunningAverageMetric accountRetryWaitMs;
    private RunningAverageMetric accountLookupRetries;
    private RunningAverageMetric handledSubmitMessageSize;
    private RunningAverageMetric writeQueueSizeRecordStream;
    private RunningAverageMetric hashQueueSizeRecordStream;

    public MiscRunningAvgs(final double halfLife) {
        gasPerConsSec =
                new RunningAverageMetric(
                        STAT_CATEGORY,
                        Names.GAS_PER_CONSENSUS_SEC,
                        Descriptions.GAS_PER_CONSENSUS_SEC,
                        RUNNING_AVG_FORMAT,
                        halfLife);
        accountRetryWaitMs =
                new RunningAverageMetric(
                        STAT_CATEGORY,
                        Names.ACCOUNT_RETRY_WAIT_MS,
                        Descriptions.ACCOUNT_RETRY_WAIT_MS,
                        RUNNING_AVG_FORMAT,
                        halfLife);
        accountLookupRetries =
                new RunningAverageMetric(
                        STAT_CATEGORY,
                        Names.ACCOUNT_LOOKUP_RETRIES,
                        Descriptions.ACCOUNT_LOOKUP_RETRIES,
                        RUNNING_AVG_FORMAT,
                        halfLife);
        handledSubmitMessageSize =
                new RunningAverageMetric(
                        STAT_CATEGORY,
                        Names.HANDLED_SUBMIT_MESSAGE_SIZE,
                        Descriptions.HANDLED_SUBMIT_MESSAGE_SIZE,
                        RUNNING_AVG_FORMAT,
                        halfLife);
        writeQueueSizeRecordStream =
                new RunningAverageMetric(
                        STAT_CATEGORY,
                        Names.WRITE_QUEUE_SIZE_RECORD_STREAM,
                        Descriptions.WRITE_QUEUE_SIZE_RECORD_STREAM,
                        RUNNING_AVG_FORMAT,
                        halfLife);
        hashQueueSizeRecordStream =
                new RunningAverageMetric(
                        STAT_CATEGORY,
                        Names.HASH_QUEUE_SIZE_RECORD_STREAM,
                        Descriptions.HASH_QUEUE_SIZE_RECORD_STREAM,
                        RUNNING_AVG_FORMAT,
                        halfLife);
    }

    public void registerWith(final Platform platform) {
        platform.addAppMetrics(
                gasPerConsSec,
                accountRetryWaitMs,
                accountLookupRetries,
                handledSubmitMessageSize,
                writeQueueSizeRecordStream,
                hashQueueSizeRecordStream);
    }

    public void recordAccountLookupRetries(final int num) {
        accountLookupRetries.recordValue(num);
    }

    public void recordAccountRetryWaitMs(final double time) {
        accountRetryWaitMs.recordValue(time);
    }

    public void recordHandledSubmitMessageSize(final int bytes) {
        handledSubmitMessageSize.recordValue(bytes);
    }

    public void writeQueueSizeRecordStream(final int num) {
        writeQueueSizeRecordStream.recordValue(num);
    }

    public void hashQueueSizeRecordStream(final int num) {
        hashQueueSizeRecordStream.recordValue(num);
    }

    public void recordGasPerConsSec(final long gas) {
        gasPerConsSec.recordValue(gas);
    }

    public static final class Names {
        static final String GAS_PER_CONSENSUS_SEC = "gasPerConsSec";
        static final String ACCOUNT_RETRY_WAIT_MS = "avgAcctRetryWaitMs";
        static final String ACCOUNT_LOOKUP_RETRIES = "avgAcctLookupRetryAttempts";
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
        static final String ACCOUNT_RETRY_WAIT_MS =
                "average time is millis spent waiting to lookup the account number";
        static final String ACCOUNT_LOOKUP_RETRIES =
                "average number of retry attempts made to lookup the account number";
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
    void setAccountRetryWaitMs(RunningAverageMetric accountRetryWaitMs) {
        this.accountRetryWaitMs = accountRetryWaitMs;
    }

    @VisibleForTesting
    void setAccountLookupRetries(RunningAverageMetric accountLookupRetries) {
        this.accountLookupRetries = accountLookupRetries;
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
