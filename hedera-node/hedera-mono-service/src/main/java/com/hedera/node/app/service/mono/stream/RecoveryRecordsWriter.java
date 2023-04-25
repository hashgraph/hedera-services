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

package com.hedera.node.app.service.mono.stream;

import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.orderedRecordFilesFrom;
import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.parseRecordFileConsensusTime;
import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.readMaybeCompressedRecordStreamFile;
import static com.hedera.node.app.service.mono.utils.forensics.RecordParsers.parseV6RecordStreamEntriesIn;
import static com.hedera.node.app.service.mono.utils.forensics.RecordParsers.parseV6SidecarRecordsByConsTimeIn;
import static com.hedera.node.app.service.mono.utils.forensics.RecordParsers.visitWithSidecars;
import static java.util.Objects.requireNonNull;

import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.crypto.RunningHashable;
import com.swirlds.common.stream.MultiStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A helper class for {@link RecordStreamManager} to use when generating record stream files
 * (and sidecars) during recovery.
 *
 * <p>Given the first item in the recovery event stream, this class will scan any on-disk record
 * stream files to detect if one of them "overlaps" with the first recovery item's consensus time.
 *
 * <p>If it finds an overlap file, it will read the "prefix" of items from this file that <b>precede</b>
 * event recovery, and call {@link MultiStream#addObject(RunningHashable)} with each item to ensure the
 * first golden record file written during recovery is a full 2-second block that includes not just the
 * items replayed during recovery, but <b>also</b> the prefix of items on disk from the overlap file.
 */
@Singleton
public class RecoveryRecordsWriter {
    private static final Logger logger = LogManager.getLogger(RecoveryRecordsWriter.class);

    /**
     * The period of time that each record file/block includes items for.
     */
    private final long blockPeriodMs;

    /**
     * The directory containing stream files (both records and sidecars) from before the recovery.
     */
    private final String onDiskRecordsLoc;

    public RecoveryRecordsWriter(final long blockPeriodMs, @NonNull final String onDiskRecordsLoc) {
        this.blockPeriodMs = blockPeriodMs;
        this.onDiskRecordsLoc = onDiskRecordsLoc;
    }

    /**
     * Given the first item in the recovery stream, scans any on-disk record stream files to
     * see if one of these files contains record stream items that should make up the leading
     * prefix of the first "golden" record file written during recovery.
     *
     * <p>If such a file exists, updates the {@link RecordStreamManager} with the initial
     * hash from the overlap file. For the file's items, calls {@link MultiStream#addObject(RunningHashable)}
     * to add each to the recovery stream.
     *
     * @param firstItem the first item in the recovery stream
     * @param recordStreamManager the {@link RecordStreamManager} to update the initial hash
     * @param multiStream the {@link MultiStream} to add the overlap items to
     * @throws IllegalStateException if there are multiple overlap files on disk
     */
    public void writeAnyPrefixRecordsGiven(
            final RecordStreamObject firstItem,
            final RecordStreamManager recordStreamManager,
            final MultiStream<RecordStreamObject> multiStream) {
        try {
            final var recoveryStartTime = firstItem.getTimestamp();
            final var filter = timeOverlapTestFor(recoveryStartTime, blockPeriodMs);
            final var overlapBlockNo = new AtomicLong();
            computeOverlapMetadata(overlapBlockNo, recordStreamManager, filter);

            final var entries = parseV6RecordStreamEntriesIn(onDiskRecordsLoc, filter);
            // It's inefficient to read all sidecar files here, since we only need them for
            // a single file; but this is fine for recovery, which happens almost never
            final var sidecarRecords = parseV6SidecarRecordsByConsTimeIn(onDiskRecordsLoc);

            // We only want to mark the first record stream item in the prefix as the start of a new file
            final var itemIsFirst = new AtomicBoolean(true);
            visitWithSidecars(entries, sidecarRecords, (entry, sidecars) -> {
                if (entry.consensusTime().isBefore(recoveryStartTime)) {
                    final var rso = new RecordStreamObject(
                                    entry.txnRecord(),
                                    entry.submittedTransaction(),
                                    entry.consensusTime(),
                                    sidecars.stream()
                                            .map(TransactionSidecarRecord::toBuilder)
                                            .toList())
                            .withBlockNumber(overlapBlockNo.get());
                    if (itemIsFirst.get()) {
                        rso.setWriteNewFile();
                        itemIsFirst.set(false);
                    }
                    multiStream.addObject(rso);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to determine record prefix for recovery", e);
        }
    }

    /**
     * Returns a predicate that will return true for a record file whose consensus time range
     * includes the given first recovery time.
     *
     * @param firstRecoveryTime the first recovery time
     * @param blockPeriodMs the block period in milliseconds
     * @return a predicate that will return true for a record file with a prefix for the recovery stream
     */
    static Predicate<String> timeOverlapTestFor(@NonNull final Instant firstRecoveryTime, final long blockPeriodMs) {
        return recordFile -> {
            final var fileTime = parseRecordFileConsensusTime(recordFile);
            final var includesFirstRecoveryTime =
                    !requireNonNull(firstRecoveryTime).isBefore(fileTime)
                            && fileTime.plusMillis(blockPeriodMs).isAfter(firstRecoveryTime);
            if (includesFirstRecoveryTime) {
                logger.info(
                        "Found overlap record file '{}' with a possible prefix for the recovery stream", recordFile);
            }
            return includesFirstRecoveryTime;
        };
    }

    /**
     * Updates its parameters with information from any overlap file it finds in the {@code onDiskRecordsLoc} directory,
     * that also passes the given {@code filter}.
     *
     * <p>Specifically, if this method finds an overlap file {@code F}, it will:
     * <ul>
     *     <li>Update the {@code overlapBlockNo} with {@code F}'s block number.</li>
     *     <li>Update the {@link RecordStreamManager}'s initial hash to the start running hash of {@code F}.</li>
     * </ul>
     *
     * @param overlapBlockNo a reference to hold the block number of the overlap file
     * @param recordStreamManager the {@link RecordStreamManager} to update the initial hash
     * @param filter the filter to apply to any record files
     * @throws IOException if there is an error reading the record files
     * @throws IllegalStateException if there are multiple overlap files on disk
     */
    private void computeOverlapMetadata(
            @NonNull final AtomicLong overlapBlockNo,
            @NonNull final RecordStreamManager recordStreamManager,
            @NonNull final Predicate<String> filter)
            throws IOException {
        final var overlappingFilesOnDisk = orderedRecordFilesFrom(onDiskRecordsLoc, filter);
        if (overlappingFilesOnDisk.size() > 1) {
            throw new IllegalStateException(
                    "At most one overlap record file should be possible, but found " + overlappingFilesOnDisk);
        }
        if (!overlappingFilesOnDisk.isEmpty()) {
            final var overlapFile = overlappingFilesOnDisk.get(0);
            readMaybeCompressedRecordStreamFile(overlapFile).getValue().ifPresent(f -> {
                overlapBlockNo.set(f.getBlockNumber());
                final var startHash = new ImmutableHash(
                        f.getStartObjectRunningHash().getHash().toByteArray());
                recordStreamManager.setInitialHash(startHash);
            });
        }
    }
}
