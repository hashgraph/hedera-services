/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.logic;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.legacy.core.jproto.JKey;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NonPayerKeysScreenTest {
    @Mock private TransactionContext txnCtx;
    @Mock private InHandleActivationHelper activationHelper;
    @Mock private BiPredicate<JKey, TransactionSignature> validityTest;

    private NonPayerKeysScreen subject;

    @BeforeEach
    void setUp() {
        subject = new NonPayerKeysScreen(txnCtx, activationHelper, validityTest);
    }

    @Test
    void terminatesWithFailedSigStatus() {
        final var result = subject.reqKeysAreActiveGiven(INVALID_ACCOUNT_ID);

        assertFalse(result);
        verify(txnCtx).setStatus(INVALID_ACCOUNT_ID);
    }

    @Test
    void terminatesWhenOtherPartySigsNotActive() {
        // when:
        final var result = subject.reqKeysAreActiveGiven(OK);

        // then:
        assertFalse(result);
        // and:
        verify(activationHelper).areOtherPartiesActive(validityTest);
        verify(txnCtx).setStatus(INVALID_SIGNATURE);
    }

    @Test
    void oksValidSigs() {
        given(activationHelper.areOtherPartiesActive(validityTest)).willReturn(true);

        // when:
        final var result = subject.reqKeysAreActiveGiven(OK);

        // then:
        assertTrue(result);
    }
}
