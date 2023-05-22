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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({LogCaptureExtension.class, MockitoExtension.class})
public class UnzipUtilityTest {
    @Mock
    private ZipInputStream zipIn;

    @LoggingTarget
    private LogCaptor logCaptor;

    @LoggingSubject
    private UnzipUtility subject;

    // This folder and the files created in it will be deleted after
    // tests are run, even in the event of failures or exceptions.
    @TempDir
    private Path tempDir;

    @Test
    void unzipSucceedsAndLogs() throws IOException {
        final var zipFile = "src/test/resources/testfiles/updateFeature/valid.zip";
        final var data = Files.readAllBytes(Paths.get(zipFile));

        assertDoesNotThrow(() -> UnzipUtility.unzip(data, tempDir.toString()));
        final Path path = tempDir.resolve("aaaa.txt");
        assert (path.toFile().exists());
        assert (logCaptor.infoLogs().contains("- Extracted update file /private" + path));
    }

    @Test
    void failsWhenArchiveIsInvalidZip() throws IOException {
        final byte[] data = new byte[] {'a', 'b', 'c'};
        assertThrows(IOException.class, () -> UnzipUtility.unzip(data, tempDir.toString()));
    }
}
