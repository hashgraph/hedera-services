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
package com.hedera.services.utils.forensics;

import static com.hedera.services.exports.recordstreaming.RecordStreamingUtils.readRecordStreamFile;
import static com.hedera.services.utils.MiscUtils.timestampToInstant;
import static com.hedera.services.utils.accessors.SignedTxnAccessor.uncheckedFrom;
import static java.util.Comparator.comparing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides a helper to parse the <i>.rcd.gz</i> files in a directory into a list of {@link
 * RecordStreamEntry} objects.
 *
 * <p><b>NOTE:</b> This class is only for offline analysis and debugging; it is not used at node
 * runtime.
 */
public class RecordParsers {
    private static final String V6_FILE_EXT = ".rcd.gz";

    private RecordParsers() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Given a directory of compressed V6 record files, returns a list of all the {@code
     * (Transaction, TransactionRecord)} entries contained in those files, in order of ascending
     * consensus time.
     *
     * @param streamDir a directory with compressed V6 record files
     * @return all the contained stream entries
     * @throws IOException if the files cannot be read or parsed
     */
    @SuppressWarnings("java:S3655")
    public static List<RecordStreamEntry> parseV6RecordStreamEntriesIn(final String streamDir)
            throws IOException {
        final var recordFiles = orderedRecordFilesFrom(streamDir);
        final List<RecordStreamEntry> entries = new ArrayList<>();
        for (final var recordFile : recordFiles) {
            final var readResult = readRecordStreamFile(recordFile);
            assert readResult.getRight().isPresent();
            final var records = readResult.getRight().get();
            records.getRecordStreamItemsList()
                    .forEach(
                            item -> {
                                final var itemRecord = item.getRecord();
                                entries.add(
                                        new RecordStreamEntry(
                                                uncheckedFrom(item.getTransaction()),
                                                itemRecord,
                                                timestampToInstant(
                                                        itemRecord.getConsensusTimestamp())));
                            });
        }
        return entries;
    }

    @SuppressWarnings("java:S2095")
    private static List<String> orderedRecordFilesFrom(final String streamDir) throws IOException {
        return Files.walk(Path.of(streamDir))
                .map(Path::toString)
                .filter(s -> s.endsWith(V6_FILE_EXT))
                .sorted(comparing(RecordParsers::parseRecordFileConsensusTime))
                .toList();
    }

    private static Instant parseRecordFileConsensusTime(final String recordFile) {
        final var s = recordFile.lastIndexOf("/");
        final var n = recordFile.length();
        return Instant.parse(
                recordFile.substring(s + 1, n - V6_FILE_EXT.length()).replace("_", ":"));
    }
}
