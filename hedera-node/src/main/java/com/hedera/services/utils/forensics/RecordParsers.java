package com.hedera.services.utils.forensics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.recordstreaming.RecordStreamingUtils.readRecordStreamFile;
import static com.hedera.services.utils.MiscUtils.timestampToInstant;
import static com.hedera.services.utils.accessors.SignedTxnAccessor.uncheckedFrom;
import static java.util.Comparator.comparing;

public class RecordParsers {
    private static final String V6_FILE_EXT = ".rcd.gz";

    private RecordParsers() {
        throw new UnsupportedOperationException("Utility Class");
    }

    @SuppressWarnings("java:S3655")
    public static List<RecordStreamEntry> parseV6RecordStreamEntriesIn(final String streamDir) throws IOException {
        final var recordFiles = orderedRecordFilesFrom(streamDir);
        final List<RecordStreamEntry> entries = new ArrayList<>();
        for (final var recordFile : recordFiles) {
            final var readResult = readRecordStreamFile(recordFile);
            assert readResult.getRight().isPresent();
            final var records = readResult.getRight().get();
            records.getRecordStreamItemsList().forEach(item -> {
                final var itemRecord = item.getRecord();
                entries.add(new RecordStreamEntry(
                        uncheckedFrom(item.getTransaction()),
                        itemRecord,
                        timestampToInstant(itemRecord.getConsensusTimestamp())));
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
        return Instant.parse(recordFile.substring(s + 1, n - V6_FILE_EXT.length()).replace("_", ":"));
    }
}
