/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.cli.test.sign;

import static com.hedera.services.cli.test.sign.TestUtils.HAPI_VERSION;
import static com.hedera.services.cli.test.sign.TestUtils.loadResourceFile;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.cli.sign.RecordStreamSigningUtils;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;

class RecordStreamSigningUtilsTest {
    @Mock
    PublicKey publicKey;

    @Mock
    PrivateKey privateKey;

    @TempDir
    private File tmpDir;

    private String hapiVersion;

    @BeforeEach
    void setUp() {
        hapiVersion = HAPI_VERSION;
    }

    @Test
    @DisplayName("Failed to generate signature file with invalid KeyPair")
    void failureGenerateSignatureFileInvalidKeyPair() {
        // given:
        final var signatureFileDestination = Path.of("testPath");
        final var keyPair = new KeyPair(publicKey, privateKey);
        final var fileToSign = loadResourceFile("2023-04-18T14_08_20.465612003Z.rcd");

        // then:
        assertFalse(RecordStreamSigningUtils.signRecordStreamFile(
                signatureFileDestination, fileToSign, keyPair, hapiVersion));
    }

    @Test
    @DisplayName("Succeed to generate signature file")
    void succeedGenerateSignatureFile() {
        // given:
        final var signedFileDestination = Path.of(tmpDir.getPath() + "/2023-04-18T14_08_20.465612003Z.rcd_sig");
        final var fileToSign = loadResourceFile("2023-04-18T14_08_20.465612003Z.rcd");

        // then:
        assertTrue(RecordStreamSigningUtils.signRecordStreamFile(
                signedFileDestination, fileToSign, TestUtils.loadKey(), hapiVersion));
    }

    @Test
    @DisplayName("Succeed to generate signature file for gzipped file")
    void succeedGenerateSignatureFileForGzip() {
        // given:
        final var signedFileDestination = Path.of(tmpDir.getPath() + "/2022-09-19T21_09_17.348788413Z.rcd.gz_sig");
        final var fileToSign = loadResourceFile("2022-09-19T21_09_17.348788413Z.rcd.gz");

        // then:
        assertTrue(RecordStreamSigningUtils.signRecordStreamFile(
                signedFileDestination, fileToSign, TestUtils.loadKey(), hapiVersion));
    }

    @Test
    @DisplayName("Failed if hapi version is not correct format")
    void throwsOnInvalidProtobufVersionException() {
        final var signedFileDestination = Path.of(tmpDir.getPath() + "/2022-09-19T21_09_17.348788413Z.rcd.gz_sig");
        final var fileToSign = loadResourceFile("2022-09-19T21_09_17.348788413Z.rcd.gz");
        final var hapiVersion = "0.2";

        assertFalse(RecordStreamSigningUtils.signRecordStreamFile(
                signedFileDestination, fileToSign, TestUtils.loadKey(), hapiVersion));
    }

    @Test
    @DisplayName("Failed if record stream file is not version 6")
    void cannotSignVersion5RecordStreamFile() {
        final var signedFileDestination = Path.of(tmpDir.getPath() + "/2021-01-12T19_44_28.960705001Z.rcd_sig");
        final var fileToSign = loadResourceFile("2021-01-12T19_44_28.960705001Z.rcd");

        assertFalse(RecordStreamSigningUtils.signRecordStreamFile(
                signedFileDestination, fileToSign, TestUtils.loadKey(), hapiVersion));
    }

    @Test
    @DisplayName("Failed if hapi version is number format")
    void failedToSignWithWrongHapiFormat() {
        final var signedFileDestination = Path.of(tmpDir.getPath() + "/2022-09-19T21_09_17.348788413Z.rcd.gz_sig");
        final var fileToSign = loadResourceFile("2022-09-19T21_09_17.348788413Z.rcd.gz");
        final var hapiVersion = "a.b.c-test";

        assertFalse(RecordStreamSigningUtils.signRecordStreamFile(
                signedFileDestination, fileToSign, TestUtils.loadKey(), hapiVersion));
    }

    @Test
    @DisplayName("Failed to generate signature file with invalid file")
    void failedToSignWithInvalidFile() {
        // given:
        final var signedFileDestination = Path.of(tmpDir.getPath());
        final var fileToSign = loadResourceFile("2023-04-18T14_08_20.465612003Z.rcd");

        // then:
        assertFalse(RecordStreamSigningUtils.signRecordStreamFile(
                signedFileDestination, fileToSign, TestUtils.loadKey(), hapiVersion));
    }

    @Test
    @DisplayName("Failed to generate signature file with empty record stream file")
    void failedToSignWithEmptyRecordFile() {
        final var signedFileDestination = Path.of(tmpDir.getPath() + "/2022-09-19T21_09_17.348788413Z.rcd.gz_sig");
        final var tmpDirPath = tmpDir.toPath();
        final var fileToSignPath = tmpDirPath.resolve("testFile");
        final var fileToSign = fileToSignPath.toFile();
        try (final var fos = new SerializableDataOutputStream(new FileOutputStream(fileToSign))) {
            fos.writeInt(RecordStreamSigningUtils.SUPPORTED_STREAM_FILE_VERSION);
            fos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        assertFalse(RecordStreamSigningUtils.signRecordStreamFile(
                signedFileDestination, fileToSignPath, TestUtils.loadKey(), hapiVersion));
    }

    @Test
    @DisplayName("signed file matches original signed file")
    void matchOriginalSignatureFile() throws IOException {
        // given:
        final var fileToSign = loadResourceFile("2023-04-28T07_49_22.718079003Z.rcd");
        final var origSign = loadResourceFile("2023-04-28T07_49_22.718079003Z.rcd_sig");
        final var signedFileDestination = Path.of(tmpDir.getPath() + "/2023-04-28T07_49_22.718079003Z.rcd_sig");
        // then:
        assertTrue(RecordStreamSigningUtils.signRecordStreamFile(
                signedFileDestination, fileToSign, TestUtils.loadNode0Key("private-node0000-1.pfx"), hapiVersion));

        byte[] signdata = Files.readAllBytes(signedFileDestination);
        byte[] origdata = Files.readAllBytes(origSign);

        assertArrayEquals(signdata, origdata);
    }
}
