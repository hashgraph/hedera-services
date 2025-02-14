// SPDX-License-Identifier: Apache-2.0
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
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.node.app.service.contract.impl.exec.CallOutcome;
import com.hedera.node.app.service.contract.impl.records.ContractCallStreamBuilder;
import com.hedera.node.app.service.contract.impl.records.ContractCreateStreamBuilder;
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
    private ContractCallStreamBuilder contractCallRecordBuilder;

    @Mock
    private ContractCreateStreamBuilder contractCreateRecordBuilder;

    @Test
    void setsAbortCallResult() {
        final var abortedCall =
                new CallOutcome(ContractFunctionResult.DEFAULT, INSUFFICIENT_GAS, CALLED_CONTRACT_ID, 123L, null, null);
        abortedCall.addCallDetailsTo(contractCallRecordBuilder);
        verify(contractCallRecordBuilder).contractCallResult(any());
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
