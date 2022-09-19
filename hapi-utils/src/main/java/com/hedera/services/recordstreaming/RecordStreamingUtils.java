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
package com.hedera.services.recordstreaming;

import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.SidecarFile;
import com.hedera.services.stream.proto.SignatureFile;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import org.apache.commons.lang3.tuple.Pair;

/** Minimal utility to read record stream files and their corresponding signature files. */
public class RecordStreamingUtils {
    private RecordStreamingUtils() {}

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
        final var uncompressedFileContents = getUncompressedStreamFileBytes(fileLoc);
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

    public static SidecarFile readUncompressedSidecarFile(final String fileLoc) throws IOException {
        try (final var fin = new FileInputStream(fileLoc)) {
            return SidecarFile.parseFrom(fin);
        }
    }

    public static SidecarFile readSidecarFile(final String fileLoc) throws IOException {
        return SidecarFile.parseFrom(getUncompressedStreamFileBytes(fileLoc));
    }

    private static byte[] getUncompressedStreamFileBytes(final String fileLoc) throws IOException {
        try (final var fin = new GZIPInputStream(new FileInputStream(fileLoc));
                final var byteArrayOutputStream = new ByteArrayOutputStream()) {
            final var buffer = new byte[1024];
            int len;
            while ((len = fin.read(buffer)) > 0) {
                byteArrayOutputStream.write(buffer, 0, len);
            }

            return byteArrayOutputStream.toByteArray();
        }
    }
}
