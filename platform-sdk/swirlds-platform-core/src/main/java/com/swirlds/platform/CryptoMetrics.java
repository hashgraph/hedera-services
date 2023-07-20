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

package com.swirlds.platform;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_11_3;

import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.LongAccumulator;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.metrics.config.MetricsConfig;

/**
 * This class collects and reports various statistics about advanced cryptography module operation.
 */
public final class CryptoMetrics {

    private static final String CATEGORY = "crypto";

    private static final LongAccumulator.Config MIN_DIGEST_BATCH_SIZE_CONFIG = new LongAccumulator.Config(
            CATEGORY, "MinDigBatchSz")
            .withAccumulator(Math::min)
            .withDescription("minimum digest batch size")
            .withInitialValue(Long.MAX_VALUE);
    private static final LongAccumulator.Config MAX_DIGEST_BATCH_SIZE_CONFIG = new LongAccumulator.Config(
            CATEGORY, "MaxDigBatchSz")
            .withDescription("maximum digest batch size")
            .withInitialValue(Long.MIN_VALUE);
    private static final LongAccumulator.Config MIN_SIG_BATCH_SIZE_CONFIG = new LongAccumulator.Config(
            CATEGORY, "MinSigBatchSz")
            .withAccumulator(Math::min)
            .withDescription("minimum signature batch size")
            .withFormat("%,d")
            .withInitialValue(Long.MAX_VALUE);
    private static final LongAccumulator.Config MAX_SIG_BATCH_SIZE_CONFIG = new LongAccumulator.Config(
            CATEGORY, "MaxSigBatchSz")
            .withDescription("maximum signature batch size")
            .withFormat("%,d")
            .withInitialValue(Long.MIN_VALUE);
    private static final Counter.Config TOTAL_DIGESTS_CONFIG =
            new Counter.Config(CATEGORY, "TtlDig").withDescription("running total: digests computed");
    private static final Counter.Config TOTAL_SIG_CONFIG =
            new Counter.Config(CATEGORY, "TtlSig").withDescription("running total: Signatures Verified");
    private static final Counter.Config TOTAL_SIG_VALID_CONFIG =
            new Counter.Config(CATEGORY, "TtlSigVal").withDescription("running total: valid signatures verified");
    private static final Counter.Config TOTAL_SIG_INVALID_CONFIG =
            new Counter.Config(CATEGORY, "TtlSigInval").withDescription("running total: invalid signatures verified");

    private final RunningAverageMetric avgDigestQueueDepth;
    private final RunningAverageMetric avgDigestBatchSize;
    private final SpeedometerMetric digWorkPulsePerSecond;
    private final RunningAverageMetric avgDigestTime;
    private final RunningAverageMetric avgDigestWorkItemSubmitTime;
    private final SpeedometerMetric digLockUpgradesPerSecond;
    private final SpeedometerMetric digSpansPerSecond;
    private final SpeedometerMetric digBatchesPerSecond;
    private final RunningAverageMetric avgDigestSliceSize;
    private final SpeedometerMetric digPerSec;
    private final Counter totalDigests;
    private final LongAccumulator minDigestBatchSize;
    private final LongAccumulator maxDigestBatchSize;
    private final RunningAverageMetric avgSigQueueDepth;
    private final RunningAverageMetric avgSigBatchSize;
    private final SpeedometerMetric sigWorkPulsePerSecond;
    private final RunningAverageMetric avgSigTime;
    private final RunningAverageMetric avgSigWorkItemSubmitTime;
    private final SpeedometerMetric sigLockUpgradesPerSecond;
    private final SpeedometerMetric sigSpansPerSecond;
    private final SpeedometerMetric sigBatchesPerSecond;
    private final RunningAverageMetric avgSigSliceSize;
    private final SpeedometerMetric sigPerSec;
    private final SpeedometerMetric sigValidPerSec;
    private final SpeedometerMetric sigInvalidPerSec;
    private final RunningAverageMetric avgSigIntakeQueueDepth;
    private final SpeedometerMetric sigIntakePulsePerSecond;
    private final RunningAverageMetric avgSigIntakePulseTime;
    private final RunningAverageMetric avgSigIntakeEnqueueTime;
    private final RunningAverageMetric avgSigIntakeListSize;
    private final RunningAverageMetric avgPlatformEnqueueTime;
    private final RunningAverageMetric avgPlatformExpandTime;
    private final Counter totalSig;
    private final Counter totalSigValid;
    private final Counter totalSigInvalid;
    private final LongAccumulator minSigBatchSize;
    private final LongAccumulator maxSigBatchSize;

    // private instance, so that it can be
    // accessed by only by getInstance() method
    private static volatile CryptoMetrics instance;
    private static boolean isRecording = false;
    private static volatile MetricsConfig metricsConfig;
    private static volatile Metrics metrics;

    public static CryptoMetrics getInstance() {
        // Double-Checked Locking works if the field is volatile from JDK5 onwards
        if (instance == null) {
            // synchronized block to remove overhead
            synchronized (CryptoMetrics.class) {
                if (instance == null) {
                    // if instance is null, initialize
                    instance = new CryptoMetrics();
                }
            }
        }
        return instance;
    }

    public static void registerMetrics(final MetricsConfig metricsConfig, final Metrics metrics) {
        CryptoMetrics.metricsConfig = metricsConfig;
        CryptoMetrics.metrics = metrics;
        getInstance(); // enforce initialization
    }

    private CryptoMetrics() {
        if (metrics == null || metricsConfig == null) {
            throw new IllegalStateException("Metrics has not been set");
        }
        avgDigestQueueDepth = metrics.getOrCreate(new RunningAverageMetric.Config(metricsConfig,
                CATEGORY, "DigQuDepth")
                .withDescription("average digest queue depth")
                .withFormat(FORMAT_11_3));
        avgSigQueueDepth = metrics.getOrCreate(new RunningAverageMetric.Config(metricsConfig,
                CATEGORY, "SigQuDepth")
                .withDescription("average signature queue depth")
                .withFormat(FORMAT_11_3));
        avgDigestBatchSize = metrics.getOrCreate(new RunningAverageMetric.Config(metricsConfig,
                CATEGORY, "DigBatchSz")
                .withDescription("average digest batch size")
                .withFormat(FORMAT_11_3));
        minDigestBatchSize = metrics.getOrCreate(MIN_DIGEST_BATCH_SIZE_CONFIG);
        maxDigestBatchSize = metrics.getOrCreate(MAX_DIGEST_BATCH_SIZE_CONFIG);
        avgSigBatchSize = metrics.getOrCreate(new RunningAverageMetric.Config(metricsConfig,
                CATEGORY, "SigBatchSz")
                .withDescription("average signature batch size")
                .withFormat(FORMAT_11_3));
        minSigBatchSize = metrics.getOrCreate(MIN_SIG_BATCH_SIZE_CONFIG);
        maxSigBatchSize = metrics.getOrCreate(MAX_SIG_BATCH_SIZE_CONFIG);
        digWorkPulsePerSecond = metrics.getOrCreate(new SpeedometerMetric.Config(metricsConfig,
                CATEGORY, "DigPulse/sec")
                .withDescription("average digest worker pulses per second")
                .withFormat(FORMAT_11_3));
        digLockUpgradesPerSecond = metrics.getOrCreate(new SpeedometerMetric.Config(metricsConfig,
                CATEGORY, "DigLockUp/sec")
                .withDescription("average digest lock upgrades per second")
                .withFormat(FORMAT_11_3));
        digSpansPerSecond = metrics.getOrCreate(new SpeedometerMetric.Config(metricsConfig,
                CATEGORY, "DigSpans/sec")
                .withDescription("average: digest batch spans per second")
                .withFormat(FORMAT_11_3));
        digBatchesPerSecond = metrics.getOrCreate(new SpeedometerMetric.Config(metricsConfig,
                CATEGORY, "DigBatches/sec")
                .withDescription("average: digest batches created per second")
                .withFormat(FORMAT_11_3));
        digPerSec = metrics.getOrCreate(new SpeedometerMetric.Config(metricsConfig, CATEGORY, "Dig/sec")
                .withDescription("number of digests per second (complete)")
                .withFormat(FORMAT_11_3));
        sigWorkPulsePerSecond = metrics.getOrCreate(new SpeedometerMetric.Config(metricsConfig,
                CATEGORY, "SigPulse/sec")
                .withDescription("average Signature worker pulses per second")
                .withFormat(FORMAT_11_3));
        avgDigestTime = metrics.getOrCreate(new RunningAverageMetric.Config(metricsConfig,
                CATEGORY, "DigWrkTime")
                .withDescription("average: time spent (in millis) in digest worker pulses")
                .withFormat(FORMAT_11_3));
        avgSigTime = metrics.getOrCreate(new RunningAverageMetric.Config(metricsConfig,
                CATEGORY, "SigWrkTime")
                .withDescription("average: time spent (in millis) in signature worker pulses")
                .withFormat(FORMAT_11_3));
        avgDigestWorkItemSubmitTime = metrics.getOrCreate(
                new RunningAverageMetric.Config(metricsConfig, CATEGORY, "DigSubWrkItmTime")
                        .withDescription("average: time spent (in millis) in digest submission")
                        .withFormat(FORMAT_11_3));
        avgSigWorkItemSubmitTime = metrics.getOrCreate(
                new RunningAverageMetric.Config(metricsConfig, CATEGORY, "SigSubWrkItmTime")
                        .withDescription("average: time spent (in millis) in signature verification submission")
                        .withFormat(FORMAT_11_3));
        sigLockUpgradesPerSecond = metrics.getOrCreate(new SpeedometerMetric.Config(metricsConfig,
                CATEGORY, "SigLockUp/sec")
                .withDescription("average Signature lock upgrades per second")
                .withFormat(FORMAT_11_3));
        sigSpansPerSecond = metrics.getOrCreate(new SpeedometerMetric.Config(metricsConfig,
                CATEGORY, "SigSpans/sec")
                .withDescription("average: signature verification batch spans per second")
                .withFormat(FORMAT_11_3));
        sigBatchesPerSecond = metrics.getOrCreate(new SpeedometerMetric.Config(metricsConfig,
                CATEGORY, "SigBatches/sec")
                .withDescription("average: signature verification batches created per second")
                .withFormat(FORMAT_11_3));
        avgDigestSliceSize = metrics.getOrCreate(new RunningAverageMetric.Config(metricsConfig,
                CATEGORY, "DigSliceSz")
                .withDescription("average digest slice size")
                .withFormat(FORMAT_11_3));
        avgSigSliceSize = metrics.getOrCreate(new RunningAverageMetric.Config(metricsConfig,
                CATEGORY, "SigSliceSz")
                .withDescription("average signature slice size")
                .withFormat(FORMAT_11_3));
        sigPerSec = metrics.getOrCreate(new SpeedometerMetric.Config(metricsConfig, CATEGORY, "Sig/sec")
                .withDescription("number of signature verifications per second (complete)")
                .withFormat(FORMAT_11_3));
        sigValidPerSec = metrics.getOrCreate(new SpeedometerMetric.Config(metricsConfig,
                CATEGORY, "SigVal/sec")
                .withDescription("number of valid signatures per second")
                .withFormat(FORMAT_11_3));
        sigInvalidPerSec = metrics.getOrCreate(new SpeedometerMetric.Config(metricsConfig,
                CATEGORY, "SigInval/sec")
                .withDescription("number of invalid signatures per second")
                .withFormat(FORMAT_11_3));
        avgSigIntakeQueueDepth = metrics.getOrCreate(
                new RunningAverageMetric.Config(metricsConfig, CATEGORY, "SigIntakeQueueDepth")
                        .withDescription("depth of the signature intake queue")
                        .withFormat(FORMAT_11_3));
        sigIntakePulsePerSecond = metrics.getOrCreate(new SpeedometerMetric.Config(metricsConfig,
                CATEGORY, "SigIntakePulse/sec")
                .withDescription("number of times the signature intake worker thread is executed per second")
                .withFormat(FORMAT_11_3));
        avgSigIntakePulseTime = metrics.getOrCreate(new RunningAverageMetric.Config(metricsConfig,
                CATEGORY, "SigIntakePulseTime")
                .withDescription("average time spent (in millis) of each signature intake execution")
                .withFormat(FORMAT_11_3));
        avgSigIntakeEnqueueTime = metrics.getOrCreate(
                new RunningAverageMetric.Config(metricsConfig, CATEGORY, "SigIntakeEnqueueTime")
                        .withDescription("average time spent (in millis) of each intake enqueue call")
                        .withFormat(FORMAT_11_3));
        avgSigIntakeListSize = metrics.getOrCreate(new RunningAverageMetric.Config(metricsConfig,
                CATEGORY, "SigIntakeListSize")
                .withDescription("average size of each list sent to the intake worker")
                .withFormat(FORMAT_11_3));
        avgPlatformEnqueueTime = metrics.getOrCreate(new RunningAverageMetric.Config(metricsConfig,
                CATEGORY, "PlatSigEnqueueTime")
                .withDescription("average time spent (in millis) by the platform enqueuing signatures")
                .withFormat(FORMAT_11_3));
        avgPlatformExpandTime = metrics.getOrCreate(new RunningAverageMetric.Config(metricsConfig,
                CATEGORY, "PlatSigExpandTime")
                .withDescription(
                        "average time spent (in millis) by the platform calling the expandSignatures " + "method")
                .withFormat(FORMAT_11_3));
        totalDigests = metrics.getOrCreate(TOTAL_DIGESTS_CONFIG);
        totalSig = metrics.getOrCreate(TOTAL_SIG_CONFIG);
        totalSigValid = metrics.getOrCreate(TOTAL_SIG_VALID_CONFIG);
        totalSigInvalid = metrics.getOrCreate(TOTAL_SIG_INVALID_CONFIG);
    }

    static void startRecording() {
        if (!isRecording) {
            isRecording = true;
        }
    }

    public static boolean recordingStatus() {
        return isRecording;
    }

    public void setSigIntakeWorkerValues(final int queueDepth, final double workerTime, final int listSize) {
        sigIntakePulsePerSecond.cycle();
        avgSigIntakeQueueDepth.update(queueDepth);
        avgSigIntakePulseTime.update(workerTime);
        avgSigIntakeListSize.update(listSize);
    }

    public void setSigIntakeEnqueueValues(final double enqueueTime) {
        avgSigIntakeEnqueueTime.update(enqueueTime);
    }

    public void setPlatformSigIntakeValues(final double enqueueTime, final double expandTime) {
        avgPlatformEnqueueTime.update(enqueueTime);
        avgPlatformExpandTime.update(expandTime);
    }

    private void setDigestWorkerValues(long digestQueueDepth, long digestBatchSize, double time) {
        avgDigestQueueDepth.update(digestQueueDepth);
        avgDigestBatchSize.update(digestBatchSize);
        digWorkPulsePerSecond.cycle();
        avgDigestTime.update(time);
        minDigestBatchSize.update(digestBatchSize);
        maxDigestBatchSize.update(digestBatchSize);
    }

    private void setSigWorkerValues(long sigQueueDepth, long sigBatchSize, double time) {
        avgSigQueueDepth.update(sigQueueDepth);
        avgSigBatchSize.update(sigBatchSize);
        sigWorkPulsePerSecond.cycle();
        avgSigTime.update(time);
        minSigBatchSize.update(sigBatchSize);
        maxSigBatchSize.update(sigBatchSize);
    }

    private void setSigSubmitWorkItem(double time, boolean lockUpgraded) {
        avgSigWorkItemSubmitTime.update(time);
        if (lockUpgraded) {
            sigLockUpgradesPerSecond.cycle();
        }
    }

    private void setDigestSubmitWorkItem(double time, boolean lockUpgraded) {
        avgDigestWorkItemSubmitTime.update(time);
        if (lockUpgraded) {
            digLockUpgradesPerSecond.cycle();
        }
    }

    public void setDigestHandleExecution(double sliceSize) {
        avgDigestSliceSize.update(sliceSize);
        digPerSec.cycle();
        totalDigests.increment();
    }

    public void setSigHandleExecution(double sliceSize, boolean isValid) {
        avgSigSliceSize.update(sliceSize);
        sigPerSec.cycle();
        totalSig.increment();
        if (isValid) {
            sigValidPerSec.cycle();
            totalSigValid.increment();
        } else {
            sigInvalidPerSec.cycle();
            totalSigInvalid.increment();
        }
    }
}
