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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.transfer;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ApprovalSwitchHelper;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ClassicTransfersCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.Erc20TransfersCall;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.HtsCallTestBase;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class ClassicTransfersCallTest extends HtsCallTestBase {
    @Mock
    private VerificationStrategies verificationStrategies;

    @Mock
    private VerificationStrategy verificationStrategy;

    @Mock
    private ApprovalSwitchHelper approvalSwitchHelper;

    @Mock
    private HtsCallAttempt attempt;

    private ClassicTransfersCall subject;

    @Test
    void doesNotRetryWithInitialSuccess() {
        givenRetryingSubject();
    }

    @Test
    void throwsOnUnrecognizedSelector() {
        given(attempt.selector()).willReturn(Erc20TransfersCall.ERC_20_TRANSFER.selector());
        given(attempt.enhancement()).willReturn(mockEnhancement());
        final var e = assertThrows(
                IllegalArgumentException.class, () -> ClassicTransfersCall.from(attempt, EIP_1014_ADDRESS, true));
        assertTrue(e.getMessage().endsWith("is not a classic transfer"));
    }

    private void givenRetryingSubject() {
        subject = new ClassicTransfersCall(
                mockEnhancement(), TransactionBody.DEFAULT, approvalSwitchHelper, verificationStrategy);
    }

    private void givenNonRetryingSubject() {
        subject = new ClassicTransfersCall(mockEnhancement(), TransactionBody.DEFAULT, null, verificationStrategy);
    }
}
