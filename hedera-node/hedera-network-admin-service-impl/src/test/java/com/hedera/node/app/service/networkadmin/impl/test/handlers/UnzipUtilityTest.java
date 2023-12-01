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

package com.hedera.node.app.service.networkadmin.impl.test.handlers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.app.service.networkadmin.impl.handlers.UnzipUtility;
import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import com.hedera.node.app.spi.fixtures.util.LogCaptureExtension;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.hedera.node.app.spi.fixtures.util.LoggingTarget;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({LogCaptureExtension.class, MockitoExtension.class})
class UnzipUtilityTest {

    @LoggingTarget
    private LogCaptor logCaptor;

    @LoggingSubject
    private UnzipUtility subject;

    // These @TempDir folders and the files created in them will be deleted after
    // tests are run, even in the event of failures or exceptions.
    @TempDir
    private Path unzipOutputDir; // unzip test zips to this directory

    @TempDir
    private Path zipSourceDir; // contains test zips

    private File testZipWithOneFile;
    private File testZipWithSubdirectory;
    private final String FILENAME_1 = "fileToZip.txt";
    private final String FILENAME_2 = "subdirectory/subdirectoryFileToZip.txt";

    @BeforeEach
    void setUp() throws IOException {
        zipSourceDir = Files.createTempDirectory("zipSourceDir");

        // set up test zip with one file in it
        testZipWithOneFile = new File(zipSourceDir + "/testZipWithOneFile.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(testZipWithOneFile))) {
            ZipEntry e = new ZipEntry(FILENAME_1);
            out.putNextEntry(e);

            String fileContent = "Time flies like an arrow but fruit flies like a banana";
            byte[] data = fileContent.getBytes();
            out.write(data, 0, data.length);
            out.closeEntry();
        }

        // set up test zip with a file and a subdirectory in it
        testZipWithSubdirectory = new File(zipSourceDir + "/testZipWithSubdirectory.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(testZipWithSubdirectory))) {
            ZipEntry e1 = new ZipEntry(FILENAME_1);
            out.putNextEntry(e1);
            byte[] data = "Time flies like an arrow but fruit flies like a banana".getBytes();
            out.write(data, 0, data.length);
            out.closeEntry();

            ZipEntry e2 = new ZipEntry(FILENAME_2);
            out.putNextEntry(e2);
            data = "If you aren't fired with enthusiasm, you will be fired, with enthusiasm".getBytes();
            out.write(data, 0, data.length);
            out.closeEntry();
        }
    }

    @Test
    void unzipOneFileSucceedsAndLogs() throws IOException {
        final var data = Files.readAllBytes(testZipWithOneFile.toPath());

        assertDoesNotThrow(() -> UnzipUtility.unzip(data, unzipOutputDir));
        final Path path = unzipOutputDir.resolve(FILENAME_1);
        assert (path.toFile().exists());
        assert (logCaptor.infoLogs().contains("- Extracted update file " + path));
    }

    @Test
    void unzipWithSubDirectorySucceedsAndLogs() throws IOException {
        final var data = Files.readAllBytes(testZipWithSubdirectory.toPath());

        assertDoesNotThrow(() -> UnzipUtility.unzip(data, unzipOutputDir));
        final Path path = unzipOutputDir.resolve(FILENAME_1);
        assert (path.toFile().exists());
        assert (logCaptor.infoLogs().contains("- Extracted update file " + path));

        final Path path2 = unzipOutputDir.resolve(FILENAME_2);
        assert (path2.toFile().exists());
        assert (logCaptor.infoLogs().contains("- Extracted update file " + path2));
    }

    @Test
    void failsWhenArchiveIsInvalidZip() {
        final byte[] data = new byte[] {'a', 'b', 'c'};
        assertThrows(IOException.class, () -> UnzipUtility.unzip(data, unzipOutputDir));
    }
}
