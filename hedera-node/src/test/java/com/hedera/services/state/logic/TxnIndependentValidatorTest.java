package com.hedera.services.state.logic;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.swirlds.common.crypto.TransactionSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.BiPredicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith({ MockitoExtension.class })
class TxnIndependentValidatorTest {
    @Mock SignatureScreen signatureScreen;
    @Mock InHandleActivationHelper activationHelper;
    @Mock BiPredicate<JKey, TransactionSignature> validityTest;
    @Mock PlatformTxnAccessor accessor;

    private TxnIndependentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TxnIndependentValidator(signatureScreen, activationHelper, validityTest);
    }

    @Test
    void successfulValidation() {
        given(accessor.getValidationStatus()).willReturn(null);
        given(signatureScreen.applyTo(accessor)).willReturn(OK);
        given(activationHelper.areOtherPartiesActive(accessor, validityTest)).willReturn(true);

        // when:
        validator.accept(accessor);

        // then:
        verify(accessor).setValidationStatus(OK);
    }

    @Test
    void alreadyValidated() {
        given(accessor.getValidationStatus()).willReturn(OK);

        // when:
        validator.accept(accessor);

        // then:
        verify(accessor, never()).setValidationStatus(OK);
        verifyNoInteractions(signatureScreen);
        verifyNoInteractions(activationHelper);
    }

    @Test
    void notTerminalSigStatus() {
        given(accessor.getValidationStatus()).willReturn(null);
        given(signatureScreen.applyTo(accessor)).willReturn(INVALID_SIGNATURE);

        // when:
        validator.accept(accessor);

        // then:
        verify(accessor).setValidationStatus(INVALID_SIGNATURE);
        verifyNoInteractions(activationHelper);
    }

    @Test
    void otherPartiesNotActive() {
        given(accessor.getValidationStatus()).willReturn(null);
        given(signatureScreen.applyTo(accessor)).willReturn(OK);
        given(activationHelper.areOtherPartiesActive(accessor, validityTest)).willReturn(false);

        // when:
        validator.accept(accessor);

        // then:
        verify(accessor).setValidationStatus(INVALID_SIGNATURE);
    }

    @Test
    void errorDuringValidation() {
        given(accessor.getValidationStatus()).willReturn(null);
        given(signatureScreen.applyTo(accessor)).willThrow(new RuntimeException("bad"));

        // when:
        validator.accept(accessor);

        // then:
        verify(accessor).setValidationStatus(INVALID_TRANSACTION);
    }
}