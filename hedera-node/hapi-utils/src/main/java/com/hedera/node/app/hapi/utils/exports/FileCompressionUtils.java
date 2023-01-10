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
package com.hedera.node.app.hapi.utils.exports;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

/**
 * Minimal utility denoting the compression algorithm used throughout the project and utility
 * methods for reading the compressed files
 */
public class FileCompressionUtils {

    private FileCompressionUtils() {}

    public static final String COMPRESSION_ALGORITHM_EXTENSION = ".gz";

    public static byte[] readUncompressedFileBytes(final String fileLoc) throws IOException {
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
