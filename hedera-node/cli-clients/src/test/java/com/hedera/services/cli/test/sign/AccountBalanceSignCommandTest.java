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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.cli.sign.AccountBalanceSignCommand;
import com.hedera.services.cli.sign.AccountBalanceType;
import java.io.File;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;

class AccountBalanceSignCommandTest {
    @Mock
    PublicKey publicKey;

    @Mock
    PrivateKey privateKey;

    @TempDir
    private File tmpDir;

    @Test
    @DisplayName("Failure to generate signature file")
    void failureGenerateSignatureFile() {
        // given:
        final var signatureFileDestination = Path.of("testPath");
        final Path fileToSign;
        fileToSign = signatureFileDestination;
        final var keyPair = new KeyPair(publicKey, privateKey);

        // when:
        final var accountBalanceSignCommand = new AccountBalanceSignCommand();

        // then:
        assertFalse(accountBalanceSignCommand.generateSignatureFile(signatureFileDestination, fileToSign, keyPair));
    }

    @Test
    @DisplayName("Succeed to generate signature file")
    void succeedGenerateSignatureFile() {
        // given:
        final var signatureFileDestination = Path.of(tmpDir.getPath() + "/2023-03-23T14_25_29.576022Z_Balances.pb_sig");
        final var fileToSign = Path.of(Objects.requireNonNull(AccountBalanceSignCommandTest.class
                        .getClassLoader()
                        .getResource("com.hedera.services.cli.test.sign/2023-03-23T14_25_29.576022Z_Balances.pb"))
                .getPath());

        // when:
        final var accountBalanceSignCommand = new AccountBalanceSignCommand();

        // then:
        assertTrue(accountBalanceSignCommand.generateSignatureFile(
                signatureFileDestination, fileToSign, TestUtils.loadKey()));
    }

    @Test
    @DisplayName("File supported for account balance")
    void isFileSupported() {
        // given:
        final var signatureFileDestination = Path.of("testPath.pb");

        // then:
        assertTrue(AccountBalanceType.getInstance()
                .isCorrectFile(signatureFileDestination.toFile().getName()));
    }

    @Test
    @DisplayName("File not supported for account balance")
    void isFileNotSupported() {
        // given:
        final var signatureFileDestination = Path.of("testPath.lv");

        // then:
        assertFalse(AccountBalanceType.getInstance()
                .isCorrectFile(signatureFileDestination.toFile().getName()));
    }
}
