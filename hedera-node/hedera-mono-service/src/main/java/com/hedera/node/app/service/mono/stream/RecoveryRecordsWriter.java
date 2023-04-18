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

import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.parseRecordFileConsensusTime;
import static com.hedera.node.app.service.mono.utils.forensics.RecordParsers.parseV6RecordStreamEntriesIn;
import static com.hedera.node.app.service.mono.utils.forensics.RecordParsers.parseV6SidecarRecordsByConsTimeIn;
import static com.hedera.node.app.service.mono.utils.forensics.RecordParsers.visitWithSidecars;
import static java.util.Objects.requireNonNull;

import com.hedera.services.stream.proto.TransactionSidecarRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A helper class for {@link RecordStreamFileWriter} to use when generating record stream
 * files during recovery. Given the first item in the recovery stream, this class will scan
 * any on-disk record stream files and make appropriate calls to
 * {@link RecordStreamFileWriter#addObject(RecordStreamObject)} to ensure the first "golden"
 * record file written during recovery is a full 2-second block.
 */
@Singleton
public class RecoveryRecordsWriter {
    private static final Logger logger = LogManager.getLogger(RecoveryRecordsWriter.class);

    private final long blockPeriodMs;
    /**
     * The directory containing record files that were written near the recovery state's consensus time.
     *
     * <p><b>Important:</b> Any record stream items in this directory from before the recovery state's
     * consensus time must be trustworthy, as they might be repackaged as the prefix of a "golden" record
     * stream file generated during recovery.
     */
    private final String onDiskRecordsLoc;

    public RecoveryRecordsWriter(final long blockPeriodMs, @NonNull final String onDiskRecordsLoc) {
        this.blockPeriodMs = blockPeriodMs;
        this.onDiskRecordsLoc = onDiskRecordsLoc;
    }

    /**
     * Given the first item in the recovery stream, scans any on-disk record stream files to
     * detect record stream items that should make up the leading prefix of the first "golden"
     * record file written during recovery.
     *
     * <p>For any such items, calls {@link RecordStreamFileWriter#addObject(RecordStreamObject)} to
     * add them to the recovery stream.
     *
     * @param firstItem the first item in the recovery stream
     * @param recordStreamFileWriter the {@link RecordStreamFileWriter} to use for writing the recovery stream
     */
    public void writeRecordPrefixForRecoveryStartingWith(
            final RecordStreamObject firstItem, final RecordStreamFileWriter recordStreamFileWriter) {
        try {
            final var recoveryStartTime = firstItem.getTimestamp();
            final var filter = inclusionTestFor(recoveryStartTime, blockPeriodMs);
            final var entries = parseV6RecordStreamEntriesIn(onDiskRecordsLoc, filter);
            // It's obviously inefficient to read all sidecar files, but nbd for recovery
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
                                    .toList());
                    // The first item we encounter must necessarily be the first in the file with the items prefix
                    if (itemIsFirst.get()) {
                        rso.setWriteNewFile();
                        itemIsFirst.set(false);
                    }
                    recordStreamFileWriter.addObject(rso);
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
    static Predicate<String> inclusionTestFor(@NonNull final Instant firstRecoveryTime, final long blockPeriodMs) {
        return recordFile -> {
            final var fileTime = parseRecordFileConsensusTime(recordFile);
            final var includesFirstRecoveryTime =
                    !requireNonNull(firstRecoveryTime).isBefore(fileTime)
                            && fileTime.plusMillis(blockPeriodMs).isAfter(firstRecoveryTime);
            if (includesFirstRecoveryTime) {
                logger.info("Found record file '{}' with a possible prefix for the recovery stream", recordFile);
            }
            return includesFirstRecoveryTime;
        };
    }
}
