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
package com.hedera.services.state.exports;

import static com.hedera.services.exports.FileCompressionUtils.COMPRESSION_ALGORITHM_EXTENSION;
import static com.hedera.services.state.exports.SigFileWriter.TYPE_FILE_HASH;
import static com.hedera.services.state.exports.SigFileWriter.TYPE_SIGNATURE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.primitives.Ints;
import com.hedera.services.exports.FileCompressionUtils;
import com.swirlds.common.crypto.Cryptography;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class StandardSigFileWriterTest {
    String toSign = "src/test/resources/bootstrap/standard.properties";
    String cannotSign = "src/test/resources/oops/bootstrap/not-so-standard.properties";
    byte[] pretendSig = "not-really-a-sig-at-all".getBytes();
    FileHashReader hashReader = new Sha384HashReader();

    SigFileWriter subject = new StandardSigFileWriter();

    @Test
    void rethrowsIaeOnIoFailure() {
        // expect:
        Assertions.assertThrows(
                UncheckedIOException.class,
                () -> subject.writeSigFile(cannotSign, new byte[0], new byte[0]));
    }

    @Test
    void writesExpectedFile() {
        // setup:
        var hash = hashReader.readHash(toSign);

        // when:
        var actualWritten = subject.writeSigFile(toSign, pretendSig, hash);

        // then
        try (final var inputStream = new FileInputStream(actualWritten)) {
            final int typeHash = inputStream.read();
            final var actualHashInSigFile =
                    inputStream.readNBytes(Cryptography.DEFAULT_DIGEST_TYPE.digestLength());
            final int typeSignature = inputStream.read();
            final int length = Ints.fromByteArray(inputStream.readNBytes(4));
            final var actualSignatureInSigFile = inputStream.readNBytes(length);

            assertEquals(TYPE_FILE_HASH, typeHash);
            assertArrayEquals(actualHashInSigFile, hash);
            assertEquals(TYPE_SIGNATURE, typeSignature);
            assertArrayEquals(actualSignatureInSigFile, pretendSig);
        } catch (IOException e) {
            fail("IOException while reading signature file.");
        }
        assertTrue(actualWritten.endsWith(StandardSigFileWriter.SIG_FILE_EXTENSION));
        new File(actualWritten).delete();
    }

    @Test
    void writesExpectedFileWhenCompressionIsEnabled() throws IOException {
        // setup:
        var hash = hashReader.readHash(toSign);

        // when:
        var actualWritten =
                subject.writeSigFile(toSign + COMPRESSION_ALGORITHM_EXTENSION, pretendSig, hash);

        // then
        final var uncompressedStreamFileBytes =
                FileCompressionUtils.readUncompressedFileBytes(actualWritten);
        try (final var inputStream = new ByteArrayInputStream(uncompressedStreamFileBytes)) {
            final int typeHash = inputStream.read();
            final var actualHashInSigFile =
                    inputStream.readNBytes(Cryptography.DEFAULT_DIGEST_TYPE.digestLength());
            final int typeSignature = inputStream.read();
            final int length = Ints.fromByteArray(inputStream.readNBytes(4));
            final var actualSignatureInSigFile = inputStream.readNBytes(length);

            assertEquals(TYPE_FILE_HASH, typeHash);
            assertArrayEquals(actualHashInSigFile, hash);
            assertEquals(TYPE_SIGNATURE, typeSignature);
            assertArrayEquals(actualSignatureInSigFile, pretendSig);
        } catch (IOException e) {
            fail("IOException while reading signature file.");
        }
        assertTrue(
                actualWritten.endsWith(
                        StandardSigFileWriter.SIG_FILE_EXTENSION
                                + COMPRESSION_ALGORITHM_EXTENSION));
        new File(actualWritten).delete();
    }
}
