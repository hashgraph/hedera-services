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

import static com.hedera.services.cli.test.sign.TestUtils.loadResourceFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.cli.sign.AccountBalanceSigningUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;

class AccountBalanceSigningUtilsTest {
    @Mock
    PublicKey publicKey;

    @Mock
    PrivateKey privateKey;

    @TempDir
    private File tmpDir;

    @Test
    @DisplayName("Failed to generate signature file with invalid KeyPair")
    void failureGenerateSignatureFileInvalidKeyPair() {
        // given:
        final var signatureFileDestination = Path.of("testPath");
        final var keyPair = new KeyPair(publicKey, privateKey);
        final var fileToSign = loadResourceFile("2023-03-23T14_25_29.576022Z_Balances.pb");

        // then:
        assertFalse(AccountBalanceSigningUtils.signAccountBalanceFile(signatureFileDestination, fileToSign, keyPair));
    }

    @Test
    @DisplayName("Succeed to generate signature file")
    void succeedGenerateSignatureFile() {
        // given:
        final var signatureFileDestination = Path.of(tmpDir.getPath() + "/2023-03-23T14_25_29.576022Z_Balances.pb_sig");

        final var fileToSign = loadResourceFile("2023-03-23T14_25_29.576022Z_Balances.pb");
        // then:
        assertTrue(AccountBalanceSigningUtils.signAccountBalanceFile(
                signatureFileDestination, fileToSign, TestUtils.loadKey()));
    }

    @Test
    @DisplayName("Failed to generate signature file with invalid file")
    void failureGenerateSignatureFileInvalidFile() {
        // given:
        final var signatureFileDestination = Path.of(tmpDir.getPath());
        final var fileToSign = Path.of(tmpDir.getPath() + "/2023-03-23T14_25_29.576022Z_Balances.pb");

        // then:
        assertFalse(AccountBalanceSigningUtils.signAccountBalanceFile(
                signatureFileDestination, fileToSign, TestUtils.loadKey()));
    }

    @Test
    @DisplayName("Failed to generate signature file with invalid path")
    void failureGenerateSignatureFileInvalidPath() {
        // given:
        final var signatureFileDestination = Path.of(tmpDir.getPath());
        final var fileToSign = loadResourceFile("2023-03-23T14_25_29.576022Z_Balances.pb");

        // then:
        assertFalse(AccountBalanceSigningUtils.signAccountBalanceFile(
                signatureFileDestination, fileToSign, TestUtils.loadKey()));
    }

    @Test
    @DisplayName("signed file matches original signed file")
    void matchOriginalSignatureFile() throws IOException {
        // given:
        final var signatureFileDestination =
                Path.of(tmpDir.getPath() + "/2023-05-04T07_45_00.089060542Z_Balances.pb_sig");

        final var fileToSign = loadResourceFile("2023-05-04T07_45_00.089060542Z_Balances.pb");
        final var origSign = loadResourceFile("2023-05-04T07_45_00.089060542Z_Balances.pb_sig");
        // then:
        assertTrue(AccountBalanceSigningUtils.signAccountBalanceFile(
                signatureFileDestination, fileToSign, TestUtils.loadNode0Key("private-node0000.pfx")));
        assertEquals(-1, Files.mismatch(signatureFileDestination, origSign));
    }
}
