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

package com.hedera.node.app.signature;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_PREFIX_MISMATCH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.legacy.exception.InvalidAccountIDException;
import com.hedera.node.app.service.mono.legacy.exception.KeyPrefixMismatchException;
import com.hedera.node.app.service.mono.sigs.verification.PrecheckVerifier;
import com.hederahashgraph.api.proto.java.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonoSignaturePreparerTest {
    private static final Transaction MOCK_TXN = Transaction.getDefaultInstance();

    @Mock
    private PrecheckVerifier precheckVerifier;

    private MonoSignaturePreparer subject;

    @BeforeEach
    void setUp() {
        subject = new MonoSignaturePreparer(precheckVerifier);
    }

    @Test
    void delegatesPayerSigCheck() throws Exception {
        given(precheckVerifier.hasNecessarySignatures(any())).willReturn(true);
        final var status = subject.syncGetPayerSigStatus(MOCK_TXN);
        assertEquals(OK, status);
    }

    @Test
    void translatesKeyPrefixMismatch() throws Exception {
        given(precheckVerifier.hasNecessarySignatures(any())).willThrow(KeyPrefixMismatchException.class);
        final var status = subject.syncGetPayerSigStatus(MOCK_TXN);
        assertEquals(KEY_PREFIX_MISMATCH, status);
    }

    @Test
    void translatesInvalidIdException() throws Exception {
        given(precheckVerifier.hasNecessarySignatures(any())).willThrow(InvalidAccountIDException.class);
        final var status = subject.syncGetPayerSigStatus(MOCK_TXN);
        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void translatesUnrecognizedFailure() throws Exception {
        given(precheckVerifier.hasNecessarySignatures(any())).willThrow(IllegalArgumentException.class);
        final var status = subject.syncGetPayerSigStatus(MOCK_TXN);
        assertEquals(INVALID_SIGNATURE, status);
    }
}
