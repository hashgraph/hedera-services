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
package com.hedera.services.utils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class UnzipUtility {
    private static final Logger log = LogManager.getLogger(UnzipUtility.class);

    private static final int BUFFER_SIZE = 4096;

    private UnzipUtility() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static void unzip(final byte[] bytes, final String dstDir) throws IOException {
        final File destDir = new File(dstDir);
        if (!destDir.exists()) {
            if (!destDir.mkdirs()) {
                log.fatal("Unable to create the directory for update assets: {}", destDir);
                return;
            }
            log.info("Created directory {} for update assets", destDir);
        }

        final var zipIn = new ZipInputStream(new ByteArrayInputStream(bytes));
        ZipEntry entry = zipIn.getNextEntry();
        while (entry != null) {
            var filePath = dstDir + File.separator + entry.getName();
            final var fileOrDir = new File(filePath);
            filePath = fileOrDir.getCanonicalPath();

            if (!filePath.startsWith(destDir.getCanonicalPath() + File.separator)) {
                throw new IllegalArgumentException(
                        "Zip file entry " + filePath + " has an invalid path prefix!");
            }

            if (!entry.isDirectory()) {
                ensureDirectoriesExist(fileOrDir);
                extractSingleFile(zipIn, filePath);
                log.info(" - Extracted update file {}", filePath);
            } else {
                if (!fileOrDir.mkdirs()) {
                    log.error(" - Unable to create assets sub-directory: {}", fileOrDir);
                }
                log.info(" - Created assets sub-directory {}", fileOrDir);
            }
            zipIn.closeEntry();
            entry = zipIn.getNextEntry();
        }
        zipIn.close();
    }

    /**
     * Extracts a zip entry (file entry)
     *
     * @param inputStream Input stream of zip file content
     * @param filePath Output file name
     */
    static void extractSingleFile(ZipInputStream inputStream, String filePath) {
        try (var bos = new BufferedOutputStream(new FileOutputStream(filePath))) {
            final var bytesIn = new byte[BUFFER_SIZE];
            int read;
            while ((read = inputStream.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
            }
        } catch (IOException e) {
            log.error("Unable to write to file {}", filePath, e);
        }
    }

    static void ensureDirectoriesExist(final File file) {
        final File directory = file.getParentFile();

        if (!directory.exists() && !directory.mkdirs()) {
            log.fatal("Unable to create the parent directories for the file: {}", file);
        }
    }
}
