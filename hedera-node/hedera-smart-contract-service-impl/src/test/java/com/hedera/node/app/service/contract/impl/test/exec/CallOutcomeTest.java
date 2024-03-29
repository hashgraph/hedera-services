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

package com.hedera.node.app.service.contract.impl.test.exec;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CALLED_CONTRACT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NETWORK_GAS_PRICE;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SUCCESS_RESULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.node.app.service.contract.impl.exec.CallOutcome;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import com.hedera.node.app.service.contract.impl.records.ContractCreateRecordBuilder;
import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CallOutcomeTest {
    @Mock
    private RootProxyWorldUpdater updater;

    @Mock
    private ContractCallRecordBuilder contractCallRecordBuilder;

    @Mock
    private ContractCreateRecordBuilder contractCreateRecordBuilder;

    @Test
    void doesNotSetAbortCallResultIfNotRequested() {
        final var abortedCall =
                new CallOutcome(ContractFunctionResult.DEFAULT, INSUFFICIENT_GAS, CALLED_CONTRACT_ID, 123L, null, null);
        abortedCall.addCallDetailsTo(contractCallRecordBuilder, CallOutcome.ExternalizeAbortResult.NO);
        verify(contractCallRecordBuilder, never()).contractCallResult(any());
    }

    @Test
    void setsAbortCallResultIfRequested() {
        final var abortedCall =
                new CallOutcome(ContractFunctionResult.DEFAULT, INSUFFICIENT_GAS, CALLED_CONTRACT_ID, 123L, null, null);
        abortedCall.addCallDetailsTo(contractCallRecordBuilder, CallOutcome.ExternalizeAbortResult.YES);
        verify(contractCallRecordBuilder).contractCallResult(any());
    }

    @Test
    void onlySetsCreateResultIfNotAborted() {
        final var abortedCreate =
                new CallOutcome(ContractFunctionResult.DEFAULT, INSUFFICIENT_GAS, null, 123L, null, null);
        abortedCreate.addCreateDetailsTo(contractCreateRecordBuilder, CallOutcome.ExternalizeAbortResult.NO);
        verify(contractCreateRecordBuilder, never()).contractCreateResult(any());
    }

    @Test
    void recognizesCreatedIdWhenEvmAddressIsSet() {
        given(updater.getCreatedContractIds()).willReturn(List.of(CALLED_CONTRACT_ID));
        final var outcome =
                new CallOutcome(SUCCESS_RESULT.asProtoResultOf(updater), SUCCESS, null, NETWORK_GAS_PRICE, null, null);
        assertEquals(CALLED_CONTRACT_ID, outcome.recipientIdIfCreated());
    }

    @Test
    void recognizesNoCreatedIdWhenEvmAddressNotSet() {
        final var outcome =
                new CallOutcome(SUCCESS_RESULT.asProtoResultOf(updater), SUCCESS, null, NETWORK_GAS_PRICE, null, null);
        assertNull(outcome.recipientIdIfCreated());
    }

    @Test
    void calledIdIsFromResult() {
        final var outcome = new CallOutcome(
                SUCCESS_RESULT.asProtoResultOf(updater),
                INVALID_CONTRACT_ID,
                CALLED_CONTRACT_ID,
                SUCCESS_RESULT.gasPrice(),
                null,
                null);
        assertEquals(CALLED_CONTRACT_ID, outcome.recipientId());
    }
}
