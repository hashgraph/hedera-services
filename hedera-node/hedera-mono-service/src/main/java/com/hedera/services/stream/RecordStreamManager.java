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
package com.hedera.services.stream;

import static com.swirlds.common.utility.Units.MB_TO_BYTES;
import static com.swirlds.common.utility.Units.SECONDS_TO_MILLISECONDS;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.state.logic.StandardProcessLogic;
import com.hedera.services.stats.MiscRunningAvgs;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.stream.HashCalculatorForStream;
import com.swirlds.common.stream.MultiStream;
import com.swirlds.common.stream.QueueThreadObjectStream;
import com.swirlds.common.stream.QueueThreadObjectStreamConfiguration;
import com.swirlds.common.stream.RunningHashCalculatorForStream;
import com.swirlds.common.stream.internal.TimestampStreamFileWriter;
import com.swirlds.common.system.Platform;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is used for generating record stream files when record streaming is enabled, and for
 * calculating runningHash for {@link RecordStreamObject}s
 */
public class RecordStreamManager {
    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger log = LogManager.getLogger(RecordStreamManager.class);

    /**
     * receives {@link RecordStreamObject}s from {@link StandardProcessLogic} * .addForStreaming,
     * then passes to hashQueueThread and writeQueueThread
     */
    private final MultiStream<RecordStreamObject> multiStream;

    /** receives {@link RecordStreamObject}s from multiStream, then passes to hashCalculator */
    private QueueThreadObjectStream<RecordStreamObject> hashQueueThread;
    /**
     * receives {@link RecordStreamObject}s from hashQueueThread, calculates this object's Hash,
     * then passes to runningHashQueueThread
     */
    private HashCalculatorForStream<RecordStreamObject> hashCalculator;

    /** receives {@link RecordStreamObject}s from multiStream, then passes to streamFileWriter */
    private QueueThreadObjectStream<RecordStreamObject> writeQueueThread;

    /**
     * receives {@link RecordStreamObject}s from writeQueueThread, serializes {@link
     * RecordStreamObject}s to record stream files. <b>Should be deleted after migration to V6 is
     * done</b>.
     */
    private TimestampStreamFileWriter<RecordStreamObject> v5StreamFileWriter;

    /**
     * receives {@link RecordStreamObject}s from writeQueueThread, serializes {@link
     * RecordStreamObject}s to record stream files. Will be used from V6 onwards.
     */
    private RecordStreamFileWriter protobufStreamFileWriter;

    /** initial running Hash of records */
    private Hash initialHash = new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]);

    /**
     * when record streaming is started after reconnect, or at state recovering,
     * startWriteAtCompleteWindow should be set to be true; when record streaming is started after
     * restart, it should be set to be false
     */
    private boolean startWriteAtCompleteWindow = false;

    /** whether the platform is in freeze period */
    private volatile boolean inFreeze = false;

    /** an instance for recording the average value of recordStream queue size */
    private final MiscRunningAvgs runningAvgs;

    /**
     * @param platform the platform which initializes this RecordStreamManager instance
     * @param runningAvgs an instance for recording the average value of recordStream queue size
     * @param nodeLocalProperties the node-local property source, which says four things: (1) is the
     *     record stream enabled?, (2) how many seconds should elapse before creating the next
     *     record file, and (3) how large a capacity the record stream blocking queue should have.
     * @param accountMemo the account of this node from the address book memo
     * @param initialHash the initial hash
     * @throws NoSuchAlgorithmException is thrown when fails to get required MessageDigest instance
     * @throws IOException is thrown when fails to create directory for record streaming
     */
    public RecordStreamManager(
            final Platform platform,
            final MiscRunningAvgs runningAvgs,
            final NodeLocalProperties nodeLocalProperties,
            final String accountMemo,
            final Hash initialHash,
            final RecordStreamType streamType,
            final GlobalDynamicProperties globalDynamicProperties)
            throws NoSuchAlgorithmException, IOException {
        final var nodeScopedRecordLogDir =
                effectiveLogDir(nodeLocalProperties.recordLogDir(), accountMemo);
        final var nodeScopedSidecarDir =
                effectiveSidecarDir(nodeScopedRecordLogDir, nodeLocalProperties.sidecarDir());
        if (nodeLocalProperties.isRecordStreamEnabled()) {
            // the directory to which record stream files are written
            Files.createDirectories(Paths.get(nodeScopedRecordLogDir));
            Files.createDirectories(Paths.get(nodeScopedSidecarDir));
            if (globalDynamicProperties.recordFileVersion() >= 6) {
                protobufStreamFileWriter =
                        new RecordStreamFileWriter(
                                nodeScopedRecordLogDir,
                                nodeLocalProperties.recordLogPeriod() * SECONDS_TO_MILLISECONDS,
                                platform,
                                startWriteAtCompleteWindow,
                                streamType,
                                nodeScopedSidecarDir,
                                globalDynamicProperties.getSidecarMaxSizeMb() * MB_TO_BYTES,
                                globalDynamicProperties);
            } else {
                v5StreamFileWriter =
                        new TimestampStreamFileWriter<>(
                                nodeScopedRecordLogDir,
                                nodeLocalProperties.recordLogPeriod() * SECONDS_TO_MILLISECONDS,
                                platform,
                                startWriteAtCompleteWindow,
                                streamType);
            }
            writeQueueThread =
                    new QueueThreadObjectStreamConfiguration<RecordStreamObject>()
                            .setNodeId(platform.getSelfId().getId())
                            .setCapacity(nodeLocalProperties.recordStreamQueueCapacity())
                            .setForwardTo(
                                    protobufStreamFileWriter == null
                                            ? v5StreamFileWriter
                                            : protobufStreamFileWriter)
                            .setThreadName("writeQueueThread")
                            .setComponent("recordStream")
                            .build();
        }

        this.runningAvgs = runningAvgs;

        // receives {@link RecordStreamObject}s from hashCalculator, calculates and set runningHash
        // for this object
        final RunningHashCalculatorForStream<RecordStreamObject> runningHashCalculator =
                new RunningHashCalculatorForStream<>();

        hashCalculator = new HashCalculatorForStream<>(runningHashCalculator);
        hashQueueThread =
                new QueueThreadObjectStreamConfiguration<RecordStreamObject>()
                        .setNodeId(platform.getSelfId().getId())
                        .setCapacity(nodeLocalProperties.recordStreamQueueCapacity())
                        .setForwardTo(hashCalculator)
                        .setThreadName("hashQueueThread")
                        .setComponent("recordStream")
                        .build();

        multiStream =
                new MultiStream<>(
                        nodeLocalProperties.isRecordStreamEnabled()
                                ? List.of(hashQueueThread, writeQueueThread)
                                : List.of(hashQueueThread));
        this.initialHash = initialHash;
        multiStream.setRunningHash(initialHash);

        hashQueueThread.start();
        if (writeQueueThread != null) {
            writeQueueThread.start();
        }

        log.info(
                "Finish initializing RecordStreamManager with: enableRecordStreaming: {},"
                    + " recordStreamDir: {}, sidecarRecordStreamDir: {}, recordsLogPeriod: {} secs,"
                    + " recordStreamQueueCapacity: {}, initialHash: {}",
                nodeLocalProperties::isRecordStreamEnabled,
                () -> nodeScopedRecordLogDir,
                () -> nodeScopedSidecarDir,
                nodeLocalProperties::recordLogPeriod,
                nodeLocalProperties::recordStreamQueueCapacity,
                () -> initialHash);
    }

    /**
     * Is used for unit testing
     *
     * @param multiStream the instance which receives {@link RecordStreamObject}s then passes to
     *     nextStreams
     * @param writeQueueThread receives {@link RecordStreamObject}s from multiStream, then passes to
     *     streamFileWriter
     * @param runningAvgs an instance for recording the average value of recordStream queue size
     */
    RecordStreamManager(
            final MultiStream<RecordStreamObject> multiStream,
            final QueueThreadObjectStream<RecordStreamObject> writeQueueThread,
            final MiscRunningAvgs runningAvgs) {
        this.multiStream = multiStream;
        this.writeQueueThread = writeQueueThread;
        multiStream.setRunningHash(initialHash);
        this.runningAvgs = runningAvgs;
    }

    /**
     * receives a consensus record from {@link StandardProcessLogic} each time, sends it to
     * multiStream which then sends to two queueThread for calculating runningHash and writing to
     * file
     *
     * @param recordStreamObject the {@link RecordStreamObject} object to be added
     */
    public void addRecordStreamObject(final RecordStreamObject recordStreamObject) {
        if (!inFreeze) {
            try {
                multiStream.addObject(recordStreamObject);
            } catch (Exception e) {
                log.warn("Unhandled exception while streaming {}", recordStreamObject, e);
            }
        }
        if (writeQueueThread != null) {
            runningAvgs.writeQueueSizeRecordStream(getWriteQueueSize());
        }
        runningAvgs.hashQueueSizeRecordStream(getHashQueueSize());
    }

    /**
     * set `inFreeze` to be the given value
     *
     * @param inFreeze Whether the RecordStream is frozen or not.
     */
    public void setInFreeze(boolean inFreeze) {
        this.inFreeze = inFreeze;
        log.info("RecordStream inFreeze is set to be {} ", inFreeze);
        if (inFreeze) {
            multiStream.close();
        }
    }

    /**
     * sets initialHash after loading from signed state
     *
     * @param initialHash current runningHash of all {@link RecordStreamObject}s
     */
    public void setInitialHash(final Hash initialHash) {
        this.initialHash = initialHash;
        log.info("RecordStreamManager::setInitialHash: {}", () -> initialHash);
        multiStream.setRunningHash(initialHash);
    }

    /**
     * sets startWriteAtCompleteWindow: it should be set to be true after reconnect; it should be
     * set to be false at restart
     *
     * @param startWriteAtCompleteWindow whether the writer should not write until the first
     *     complete window
     */
    public void setStartWriteAtCompleteWindow(boolean startWriteAtCompleteWindow) {
        if (v5StreamFileWriter != null) {
            v5StreamFileWriter.setStartWriteAtCompleteWindow(startWriteAtCompleteWindow);
            log.info(
                    "RecordStreamManager::setStartWriteAtCompleteWindow: {}",
                    startWriteAtCompleteWindow);
        } else if (protobufStreamFileWriter != null) {
            protobufStreamFileWriter.setStartWriteAtCompleteWindow(startWriteAtCompleteWindow);
            log.info(
                    "RecordStreamManager::setStartWriteAtCompleteWindow: {}",
                    startWriteAtCompleteWindow);
        }
    }

    public static String effectiveLogDir(String baseDir, final String accountMemo) {
        if (!baseDir.endsWith(File.separator)) {
            baseDir += File.separator;
        }
        return baseDir + "record" + accountMemo;
    }

    public static String effectiveSidecarDir(String baseRecordFileDir, final String sidecarDir) {
        if (!baseRecordFileDir.endsWith(File.separator)) {
            baseRecordFileDir += File.separator;
        }
        return baseRecordFileDir + sidecarDir;
    }

    /**
     * returns current size of working queue for calculating hash and runningHash
     *
     * @return current size of working queue for calculating hash and runningHash
     */
    int getHashQueueSize() {
        return hashQueueThread == null ? 0 : hashQueueThread.getQueue().size();
    }

    /**
     * returns current size of working queue for writing to record stream files
     *
     * @return current size of working queue for writing to record stream files
     */
    int getWriteQueueSize() {
        return writeQueueThread == null ? 0 : writeQueueThread.getQueue().size();
    }

    /**
     * for unit testing
     *
     * @return current multiStream instance
     */
    MultiStream<RecordStreamObject> getMultiStream() {
        return multiStream;
    }

    /**
     * for unit testing
     *
     * @return current TimestampStreamFileWriter instance
     */
    TimestampStreamFileWriter<RecordStreamObject> getV5StreamFileWriter() {
        return v5StreamFileWriter;
    }

    /**
     * for unit testing
     *
     * @return current RecordStreamFileWriter instance
     */
    RecordStreamFileWriter getProtobufStreamFileWriter() {
        return protobufStreamFileWriter;
    }

    /**
     * for unit testing
     *
     * @return current HashCalculatorForStream instance
     */
    HashCalculatorForStream<RecordStreamObject> getHashCalculator() {
        return hashCalculator;
    }

    /**
     * for unit testing
     *
     * @return whether freeze period has started
     */
    boolean getInFreeze() {
        return inFreeze;
    }

    /**
     * for unit testing
     *
     * @return a copy of initialHash
     */
    public Hash getInitialHash() {
        return new Hash(initialHash);
    }
}
