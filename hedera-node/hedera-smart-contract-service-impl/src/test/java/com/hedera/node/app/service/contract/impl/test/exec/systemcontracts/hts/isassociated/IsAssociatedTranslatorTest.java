/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.isassociated;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.isassociated.IsAssociatedCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.isassociated.IsAssociatedTranslator;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater.Enhancement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class IsAssociatedTranslatorTest {

    @Mock
    private HtsCallAttempt mockAttempt;

    @Mock
    private Enhancement enhancement;

    @Mock
    private SystemContractGasCalculator gasCalculator;

    private IsAssociatedTranslator translator;

    @BeforeEach
    public void setUp() {
        translator = new IsAssociatedTranslator();
    }

    @Test
    public void matchesWithCorrectSelectorAndTokenRedirectReturnsTrue() {
        when(mockAttempt.isTokenRedirect()).thenReturn(true);
        when(mockAttempt.selector()).thenReturn(IsAssociatedTranslator.IS_ASSOCIATED.selector());

        assertTrue(translator.matches(mockAttempt));
    }

    @Test
    public void matchesWithIncorrectSelectorReturnsFalse() {
        when(mockAttempt.isTokenRedirect()).thenReturn(true);
        byte[] incorrectSelector = new byte[] {1, 2, 3, 4};
        when(mockAttempt.selector()).thenReturn(incorrectSelector);

        assertFalse(translator.matches(mockAttempt));
    }

    @Test
    public void matchesWithTokenRedirectFalseReturnsFalse() {
        when(mockAttempt.isTokenRedirect()).thenReturn(false);
        assertFalse(translator.matches(mockAttempt));
    }

    @Test
    public void callFromWithValidAttemptReturnsIsAssociatedCall() {
        when(mockAttempt.systemContractGasCalculator()).thenReturn(gasCalculator);
        when(mockAttempt.enhancement()).thenReturn(enhancement);
        when(mockAttempt.senderId()).thenReturn(AccountID.DEFAULT);
        when(mockAttempt.redirectToken()).thenReturn(Token.DEFAULT);
        var result = translator.callFrom(mockAttempt);

        assertInstanceOf(IsAssociatedCall.class, result);
    }
}
