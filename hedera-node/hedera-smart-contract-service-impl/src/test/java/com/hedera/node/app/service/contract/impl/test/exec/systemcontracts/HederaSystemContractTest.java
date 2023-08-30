package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract;
import com.hedera.node.app.service.contract.impl.test.TestHelpers;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CALL_DATA;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OUTPUT_DATA;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.REQUIRED_GAS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.assertExhaustsResourceLimit;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToTuweniBytes;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class HederaSystemContractTest {
    @Mock
    private MessageFrame messageFrame;

    @Test
    void defaultFullComputationDelegates() {
        final var input = pbjToTuweniBytes(CALL_DATA);
        final var output = pbjToTuweniBytes(OUTPUT_DATA);
        final var expectedResult = PrecompiledContract.PrecompileContractResult.success(output);
        final var subject = mock(HederaSystemContract.class);
        given(subject.computePrecompile(input, messageFrame)).willReturn(expectedResult);
        given(subject.gasRequirement(input)).willReturn(REQUIRED_GAS);
        doCallRealMethod().when(subject).computeFully(input, messageFrame);
        
        final var fullResult = subject.computeFully(input, messageFrame);
        Assertions.assertEquals(expectedResult, fullResult.result());
        Assertions.assertEquals(REQUIRED_GAS, fullResult.gasRequirement());
    }
}