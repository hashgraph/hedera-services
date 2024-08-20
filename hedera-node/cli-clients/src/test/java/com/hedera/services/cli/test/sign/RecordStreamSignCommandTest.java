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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.cli.sign.RecordStreamSignCommand;
import java.io.File;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;

class RecordStreamSignCommandTest {
    @Mock
    PublicKey publicKey;

    @Mock
    PrivateKey privateKey;

    @TempDir
    private File tmpDir;

    @Mock
    private RecordStreamSignCommand subject;

    private String hapiVersion;

    @BeforeEach
    void setUp() {
        subject = new RecordStreamSignCommand();
        hapiVersion = HAPI_VERSION;
    }

    @Test
    @DisplayName("Failure to generate signature file for file record stream with inbvalid key")
    void failureGenerateSignatureFileRecordStreamWithInvalidKey() {
        // given:
        final var signatureFileDestination = Path.of("testPath");
        final Path fileToSign = loadResourceFile("2023-04-18T14_08_20.465612003Z.rcd");
        final var keyPair = new KeyPair(publicKey, privateKey);
        // when:
        subject.setHapiVersion(hapiVersion);

        // then:
        assertFalse(subject.generateSignatureFile(signatureFileDestination, fileToSign, keyPair));
    }

    @Test
    @DisplayName("Failure to generate signature file for file record stream")
    void failureGenerateSignatureFileRecordStream() {
        // given:
        final var signatureFileDestination = Path.of("testPath");
        final Path fileToSign;
        fileToSign = signatureFileDestination;
        final var keyPair = new KeyPair(publicKey, privateKey);
        // when:
        subject.setHapiVersion(hapiVersion);

        // then:
        assertFalse(subject.generateSignatureFile(signatureFileDestination, fileToSign, keyPair));
    }

    @Test
    @DisplayName("Succeed to generate signature file for file record stream")
    void succeedToGenerateSignatureFileRecordStream() {
        // given:
        final var signatureFileDestination = Path.of(tmpDir.getPath() + "/2023-04-18T14_08_20.465612003Z.rcd_sig");
        final var fileToSign = loadResourceFile("2023-04-18T14_08_20.465612003Z.rcd");

        // when:
        subject.setHapiVersion(HAPI_VERSION);

        // then:
        assertTrue(subject.generateSignatureFile(signatureFileDestination, fileToSign, TestUtils.loadKey()));
    }

    @Test
    @DisplayName("Succeed to generate signature file for gz file record stream")
    void succeedToGenerateSignatureFileGzipRecordStream() {
        // given:
        final var signatureFileDestination = Path.of(tmpDir.getPath() + "/2022-09-19T21_09_17.348788413Z.rcd.gz");
        final var fileToSign = loadResourceFile("2022-09-19T21_09_17.348788413Z.rcd.gz");

        // when:
        subject.setHapiVersion(HAPI_VERSION);

        // then:
        assertTrue(subject.generateSignatureFile(signatureFileDestination, fileToSign, TestUtils.loadKey()));
    }

    @Test
    @DisplayName("File supported for record stream")
    void isFileSupported() {
        // given:
        final var signatureFileDestination = Path.of("testPath.rcd");

        // then:
        assertTrue(subject.isFileSupported(signatureFileDestination));
    }

    @Test
    @DisplayName("File supported for gz record stream")
    void isGzFileSupported() {
        // given:
        final var signatureFileDestination = Path.of("testPath.rcd.gz");

        // then:
        assertTrue(subject.isFileSupported(signatureFileDestination));
    }

    @Test
    @DisplayName("File not supported for account balance")
    void isFileNotSupported() {
        // given:
        final var signatureFileDestination = Path.of("testPath.lv");

        // then:
        assertFalse(subject.isFileSupported(signatureFileDestination));
    }
}
