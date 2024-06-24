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

package com.hedera.node.app.service.contract.impl.test.exec.scope;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.service.contract.impl.exec.scope.EitherOrVerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EitherOrVerificationStrategyTest {
    @Mock
    private VerificationStrategy firstStrategy;

    @Mock
    private VerificationStrategy secondStrategy;

    private EitherOrVerificationStrategy subject;

    @BeforeEach
    void setUp() {
        subject = new EitherOrVerificationStrategy(firstStrategy, secondStrategy);
    }

    @Test
    void firstStrategyValidSuffices() {
        given(firstStrategy.decideForPrimitive(Key.DEFAULT)).willReturn(VerificationStrategy.Decision.VALID);
        assertSame(VerificationStrategy.Decision.VALID, subject.decideForPrimitive(Key.DEFAULT));
    }

    @Test
    void secondStrategyValidSuffices() {
        given(firstStrategy.decideForPrimitive(Key.DEFAULT))
                .willReturn(VerificationStrategy.Decision.DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION);
        given(secondStrategy.decideForPrimitive(Key.DEFAULT)).willReturn(VerificationStrategy.Decision.VALID);
        assertSame(VerificationStrategy.Decision.VALID, subject.decideForPrimitive(Key.DEFAULT));
    }

    @Test
    void invalidIfNeitherStrategyValid() {
        given(firstStrategy.decideForPrimitive(Key.DEFAULT)).willReturn(VerificationStrategy.Decision.INVALID);
        given(secondStrategy.decideForPrimitive(Key.DEFAULT)).willReturn(VerificationStrategy.Decision.INVALID);
        assertSame(VerificationStrategy.Decision.INVALID, subject.decideForPrimitive(Key.DEFAULT));
    }

    @Test
    void delegatesIfPossibleAndNotAlreadyValid() {
        given(firstStrategy.decideForPrimitive(Key.DEFAULT)).willReturn(VerificationStrategy.Decision.INVALID);
        given(secondStrategy.decideForPrimitive(Key.DEFAULT))
                .willReturn(VerificationStrategy.Decision.DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION);
        assertSame(
                VerificationStrategy.Decision.DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION,
                subject.decideForPrimitive(Key.DEFAULT));
    }
}
