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

package com.hedera.services.cli.sign.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.cli.sign.AccountBalanceSigningUtils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;

import java.io.File;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

public class AccountBalanceSigningUtilsTest {
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
        final var fileToSign = Path.of("testPath");
        final var keyPair = new KeyPair(publicKey, privateKey);

        // then:
        assertEquals(
                AccountBalanceSigningUtils.signAccountBalanceFile(signatureFileDestination, fileToSign, keyPair),
                false);
    }

    @Test
    @DisplayName("Succeed to generate signature file")
    void generateSignatureFile() {
        // given:
        final var signatureFileDestination = Path.of(tmpDir.getPath() + "/2023-03-23T14_25_29.576022Z_Balances.pb_sig");
        final var fileToSign = Path.of(AccountBalanceSignCommandTest.class
                .getClassLoader()
                .getResource("com.hedera.services.cli.sign.test/2023-03-23T14_25_29.576022Z_Balances.pb")
                .getPath());

        // then:
        assertEquals(
                AccountBalanceSigningUtils.signAccountBalanceFile(
                        signatureFileDestination, fileToSign, TestUtils.loadKey()),
                true);
    }
}
