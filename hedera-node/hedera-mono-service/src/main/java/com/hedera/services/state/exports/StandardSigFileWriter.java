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

import com.google.common.primitives.Ints;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.GZIPOutputStream;

public class StandardSigFileWriter implements SigFileWriter {

    static final String SIG_FILE_EXTENSION = "_sig";

    @Override
    public String writeSigFile(
            final String signedFilePath, final byte[] sig, final byte[] signedFileHash) {
        final var isCompressed = signedFilePath.endsWith(COMPRESSION_ALGORITHM_EXTENSION);
        final var sigFilePath =
                isCompressed
                        ? signedFilePath.replace(
                                COMPRESSION_ALGORITHM_EXTENSION,
                                SIG_FILE_EXTENSION + COMPRESSION_ALGORITHM_EXTENSION)
                        : signedFilePath + SIG_FILE_EXTENSION;
        try (final var outputStream =
                isCompressed
                        ? new GZIPOutputStream(new FileOutputStream(sigFilePath, false))
                        : new FileOutputStream(sigFilePath, false)) {
            outputStream.write(TYPE_FILE_HASH);
            outputStream.write(signedFileHash);
            outputStream.write(TYPE_SIGNATURE);
            outputStream.write(Ints.toByteArray(sig.length));
            outputStream.write(sig);
            outputStream.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    String.format("I/O error writing sig of '%s'!", signedFilePath), e);
        }
        return sigFilePath;
    }
}
