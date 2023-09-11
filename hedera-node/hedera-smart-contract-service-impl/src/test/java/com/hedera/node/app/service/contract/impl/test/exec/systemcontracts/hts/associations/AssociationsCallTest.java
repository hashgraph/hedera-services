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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.associations;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asBytesResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.associations.AssociationsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.Erc20TransfersCall;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.HtsCallTestBase;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class AssociationsCallTest extends HtsCallTestBase {
    @Mock
    private VerificationStrategy verificationStrategy;

    @Mock
    private VerificationStrategies verificationStrategies;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private SingleTransactionRecordBuilder recordBuilder;

    @Mock
    private HtsCallAttempt attempt;

    private AssociationsCall subject;

    @BeforeEach
    void setUp() {
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.nativeOperations()).willReturn(nativeOperations);
        given(attempt.verificationStrategies()).willReturn(verificationStrategies);
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(addressIdConverter.convertSender(EIP_1014_ADDRESS)).willReturn(A_NEW_ACCOUNT_ID);
        given(verificationStrategies.activatingOnlyContractKeysFor(EIP_1014_ADDRESS, true, nativeOperations))
                .willReturn(verificationStrategy);

        subject = new AssociationsCall(true, attempt, EIP_1014_ADDRESS, TransactionBody.DEFAULT);
    }

    @Test
    void associationsHappyPathCompletesWithSuccessResponseCode() {
        given(systemContractOperations.dispatch(
                        TransactionBody.DEFAULT,
                        verificationStrategy,
                        A_NEW_ACCOUNT_ID,
                        SingleTransactionRecordBuilder.class))
                .willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(asBytesResult(ReturnTypes.encodedStatus(SUCCESS)), result.getOutput());
    }

    @Test
    void throwsOnUnrecognizedClassicSelector() {
        given(attempt.selector()).willReturn(Erc20TransfersCall.ERC_20_TRANSFER.selector());
        final var e = assertThrows(
                IllegalArgumentException.class, () -> AssociationsCall.from(attempt, EIP_1014_ADDRESS, true));
        assertTrue(e.getMessage().endsWith("is not a classic association/dissociation"));
    }
}
