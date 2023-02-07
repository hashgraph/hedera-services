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
package com.hedera.node.app.hapi.utils.exports.recordstreaming;

import static java.util.Comparator.comparing;

import com.hedera.node.app.hapi.utils.exports.FileCompressionUtils;
import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.SidecarFile;
import com.hedera.services.stream.proto.SignatureFile;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.apache.commons.lang3.tuple.Pair;

/** Minimal utility to read record stream files and their corresponding signature files. */
public class RecordStreamingUtils {
    // A token that we can use to distinguish sidecar files with names like,
    //   2022-12-05T14_23_46.192841556Z_01.rcd.gz
    // from record stream files whose names do *not* include a _XX index
    // like _01, _02, etc.
    public static final String SIDECAR_ONLY_TOKEN = "Z_";
    public static final String V6_FILE_EXT = ".rcd";
    public static final String V6_GZ_FILE_EXT = ".rcd.gz";

    private RecordStreamingUtils() {}

    public static Pair<Integer, Optional<RecordStreamFile>> readMaybeCompressedRecordStreamFile(
            final String loc) throws IOException {
        final var isCompressed = loc.endsWith(V6_GZ_FILE_EXT);
        return isCompressed ? readRecordStreamFile(loc) : readUncompressedRecordStreamFile(loc);
    }

    public static Pair<Integer, Optional<RecordStreamFile>> readUncompressedRecordStreamFile(
            final String fileLoc) throws IOException {
        try (final var fin = new FileInputStream(fileLoc)) {
            final var recordFileVersion = ByteBuffer.wrap(fin.readNBytes(4)).getInt();
            final var recordStreamFile = RecordStreamFile.parseFrom(fin);
            return Pair.of(recordFileVersion, Optional.ofNullable(recordStreamFile));
        }
    }

    public static Pair<Integer, Optional<RecordStreamFile>> readRecordStreamFile(
            final String fileLoc) throws IOException {
        final var uncompressedFileContents =
                FileCompressionUtils.readUncompressedFileBytes(fileLoc);
        final var recordFileVersion = ByteBuffer.wrap(uncompressedFileContents, 0, 4).getInt();
        final var recordStreamFile =
                RecordStreamFile.parseFrom(
                        ByteBuffer.wrap(
                                uncompressedFileContents, 4, uncompressedFileContents.length - 4));
        return Pair.of(recordFileVersion, Optional.ofNullable(recordStreamFile));
    }

    public static Pair<Integer, Optional<SignatureFile>> readSignatureFile(final String fileLoc)
            throws IOException {
        try (final var fin = new FileInputStream(fileLoc)) {
            final var recordFileVersion = fin.read();
            final var recordStreamSignatureFile = SignatureFile.parseFrom(fin);
            return Pair.of(recordFileVersion, Optional.ofNullable(recordStreamSignatureFile));
        }
    }

    public static SidecarFile readMaybeCompressedSidecarFile(final String loc) throws IOException {
        final var isCompressed = loc.endsWith(V6_GZ_FILE_EXT);
        return isCompressed ? readSidecarFile(loc) : readUncompressedSidecarFile(loc);
    }

    public static SidecarFile readUncompressedSidecarFile(final String fileLoc) throws IOException {
        try (final var fin = new FileInputStream(fileLoc)) {
            return SidecarFile.parseFrom(fin);
        }
    }

    public static SidecarFile readSidecarFile(final String fileLoc) throws IOException {
        return SidecarFile.parseFrom(FileCompressionUtils.readUncompressedFileBytes(fileLoc));
    }

    public static Instant parseRecordFileConsensusTime(final String recordFile) {
        final var s = recordFile.lastIndexOf("/");
        final var n = recordFile.length();
        if (recordFile.endsWith(V6_FILE_EXT)) {
            return parseInstantFrom(recordFile, s + 1, n - V6_FILE_EXT.length());
        } else if (recordFile.endsWith(V6_GZ_FILE_EXT)) {
            return parseInstantFrom(recordFile, s + 1, n - V6_GZ_FILE_EXT.length());
        } else {
            throw new IllegalArgumentException("Invalid record file name '" + recordFile + "'");
        }
    }

    public static Pair<Instant, Integer> parseSidecarFileConsensusTimeAndSequenceNo(
            final String sidecarFile) {
        final var s = sidecarFile.lastIndexOf("/");
        final var n = sidecarFile.indexOf(SIDECAR_ONLY_TOKEN, s);
        // Note we assume the sidecar sequence file number is always two digits
        return Pair.of(
                parseInstantFrom(sidecarFile, s + 1, n + 1),
                Integer.parseInt(sidecarFile.substring(n + 2, n + 4)));
    }

    private static Instant parseInstantFrom(
            final String recordOrSidecarFile, final int start, final int end) {
        return Instant.parse(recordOrSidecarFile.substring(start, end).replace("_", ":"));
    }

    @SuppressWarnings("java:S2095")
    public static List<String> filteredFilesFrom(
            final String streamDir,
            final Predicate<String> criteria,
            final Comparator<String> order)
            throws IOException {
        return Files.walk(Path.of(streamDir))
                .map(Path::toString)
                .filter(criteria)
                .sorted(order)
                .toList();
    }

    public static List<String> orderedRecordFilesFrom(final String streamDir) throws IOException {
        return filteredFilesFrom(
                streamDir,
                RecordStreamingUtils::isRecordFile,
                comparing(RecordStreamingUtils::parseRecordFileConsensusTime));
    }

    public static List<String> orderedSidecarFilesFrom(final String streamDir) throws IOException {
        return filteredFilesFrom(
                streamDir,
                RecordStreamingUtils::isSidecarFile,
                RecordStreamingUtils::compareSidecarFiles);
    }

    public static boolean isRecordFile(final String file) {
        return isRelevant(file) && !file.contains(SIDECAR_ONLY_TOKEN);
    }

    private static boolean isSidecarFile(final String file) {
        return isRelevant(file) && file.contains(SIDECAR_ONLY_TOKEN);
    }

    private static boolean isRelevant(final String file) {
        return file.endsWith(V6_FILE_EXT) || file.endsWith(V6_GZ_FILE_EXT);
    }

    public static int compareSidecarFiles(final String a, final String b) {
        return SIDECAR_FILE_COMPARATOR.compare(a, b);
    }

    public static final Comparator<String> SIDECAR_FILE_COMPARATOR =
            comparing(RecordStreamingUtils::parseSidecarFileConsensusTimeAndSequenceNo);
}
