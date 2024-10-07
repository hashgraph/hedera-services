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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts;

import static com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaOperations.ZERO_ENTROPY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EXPECTED_RANDOM_NUMBER;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.PRECOMPILE_CONTRACT_FAILED_RESULT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.PRECOMPILE_CONTRACT_SUCCESS_RESULT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.PSEUDO_RANDOM_SYSTEM_CONTRACT_ADDRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.SystemContractOperations;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.PrngSystemContract;
import com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater.Enhancement;
import com.hedera.node.app.service.contract.impl.records.ContractCallStreamBuilder;
import com.hedera.node.app.service.contract.impl.state.ProxyEvmContract;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import java.util.ArrayDeque;
import java.util.Deque;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.PrecompiledContract.PrecompileContractResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrngSystemContractTest {

    private static final long GAS_REQUIRED = 200L;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private MessageFrame messageFrame;

    @Mock
    BlockValues blockValues;

    @Mock
    ProxyWorldUpdater proxyWorldUpdater;

    @Mock
    ContractCallStreamBuilder contractCallRecordBuilder;

    @Mock
    private ProxyEvmContract mutableAccount;

    @Mock
    private AccountID accountID;

    @Mock
    private Enhancement enhancement;

    @Mock
    private SystemContractOperations systemContractOperations;

    @Mock
    private MessageFrame initialFrame;

    @Mock
    private SystemContractGasCalculator systemContractGasCalculator;

    private Deque<MessageFrame> stack = new ArrayDeque<>();

    private PrngSystemContract subject;

    @BeforeEach
    void setUp() {
        subject = new PrngSystemContract(gasCalculator);
    }

    @Test
    void gasRequirementRequiresFrameToBeSupported() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.gasRequirement(PSEUDO_RANDOM_SYSTEM_CONTRACT_ADDRESS));
    }

    @Test
    void onlyFullResultsAreSupported() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.computePrecompile(PSEUDO_RANDOM_SYSTEM_CONTRACT_ADDRESS, messageFrame));
    }

    @Test
    void computePrecompileStaticSuccessTest() {
        // given:
        givenInitialFrame();
        given(messageFrame.isStatic()).willReturn(true);
        given(messageFrame.getWorldUpdater()).willReturn(proxyWorldUpdater);
        given(proxyWorldUpdater.entropy()).willReturn(EXPECTED_RANDOM_NUMBER);

        // when:
        var actual = subject.computeFully(PSEUDO_RANDOM_SYSTEM_CONTRACT_ADDRESS, messageFrame);

        // then:
        assertEqualContractResult(PRECOMPILE_CONTRACT_SUCCESS_RESULT, actual, 100L);
    }

    @Test
    void computePrecompileMutableSuccessTest() {
        // given:
        givenCommon();
        commonMocks();
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getWorldUpdater()).willReturn(proxyWorldUpdater);
        given(proxyWorldUpdater.entropy()).willReturn(EXPECTED_RANDOM_NUMBER);
        given(systemContractGasCalculator.canonicalGasRequirement(any())).willReturn(GAS_REQUIRED);

        when(systemContractOperations.dispatch(any(), any(), any(), any())).thenReturn(contractCallRecordBuilder);
        when(contractCallRecordBuilder.contractCallResult(any())).thenReturn(contractCallRecordBuilder);
        when(contractCallRecordBuilder.entropyBytes(any())).thenReturn(contractCallRecordBuilder);

        // when:
        var actual = subject.computeFully(PSEUDO_RANDOM_SYSTEM_CONTRACT_ADDRESS, messageFrame);

        // then:
        assertEqualContractResult(PRECOMPILE_CONTRACT_SUCCESS_RESULT, actual, GAS_REQUIRED);
    }

    @Test
    void computePrecompileFailedTest() {
        // given:
        givenCommon();
        commonMocks();
        given(systemContractGasCalculator.canonicalGasRequirement(any())).willReturn(GAS_REQUIRED);
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getWorldUpdater()).willReturn(proxyWorldUpdater);
        given(proxyWorldUpdater.entropy()).willReturn(Bytes.wrap(ZERO_ENTROPY.toByteArray()));
        when(systemContractOperations.externalizePreemptedDispatch(any(), any()))
                .thenReturn(mock(ContractCallStreamBuilder.class));

        // when:
        var actual = subject.computeFully(PSEUDO_RANDOM_SYSTEM_CONTRACT_ADDRESS, messageFrame);

        // then:
        assertEqualContractResult(PRECOMPILE_CONTRACT_FAILED_RESULT, actual, GAS_REQUIRED);
    }

    @Test
    void computePrecompileInvalidFeeSubmittedFailedTest() {
        // given:
        givenCommon();
        commonMocks();
        given(systemContractGasCalculator.canonicalGasRequirement(any())).willReturn(GAS_REQUIRED);
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getWorldUpdater()).willReturn(proxyWorldUpdater);
        when(systemContractOperations.externalizePreemptedDispatch(any(), any()))
                .thenReturn(mock(ContractCallStreamBuilder.class));

        // when:
        var actual = subject.computeFully(PSEUDO_RANDOM_SYSTEM_CONTRACT_ADDRESS, messageFrame);
        // then:
        assertEqualContractResult(PRECOMPILE_CONTRACT_FAILED_RESULT, actual, GAS_REQUIRED);
    }

    @Test
    void wrongFunctionSelectorFailedTest() {
        // given:
        commonMocks();
        givenCommon();

        given(systemContractGasCalculator.canonicalGasRequirement(any())).willReturn(GAS_REQUIRED);
        when(systemContractOperations.externalizePreemptedDispatch(any(), any()))
                .thenReturn(mock(ContractCallStreamBuilder.class));

        // when:
        var actual = subject.computeFully(EXPECTED_RANDOM_NUMBER, messageFrame);

        // then:
        assertEqualContractResult(PRECOMPILE_CONTRACT_FAILED_RESULT, actual, GAS_REQUIRED);
    }

    private void givenInitialFrame() {
        given(systemContractGasCalculator.viewGasRequirement()).willReturn(100L);
        given(initialFrame.getContextVariable(FrameUtils.SYSTEM_CONTRACT_GAS_CALCULATOR_CONTEXT_VARIABLE))
                .willReturn(systemContractGasCalculator);
        stack.push(initialFrame);
        stack.addFirst(messageFrame);
        given(messageFrame.getMessageFrameStack()).willReturn(stack);
    }

    private void givenCommon() {
        given(messageFrame.getWorldUpdater()).willReturn(proxyWorldUpdater);
        givenInitialFrame();
    }

    private void assertEqualContractResult(PrecompileContractResult expected, FullResult actual, long gasRequirement) {
        assertEquals(gasRequirement, actual.gasRequirement());
        assertEquals(expected.getState(), actual.result().getState());
        assertEquals(expected.getOutput(), actual.result().getOutput());
        assertEquals(expected.getHaltReason(), actual.result().getHaltReason());
    }

    private void commonMocks() {
        final var address = Address.fromHexString("0x100");
        final var remainingGas = 10000L;
        when(messageFrame.getSenderAddress()).thenReturn(address);
        when(messageFrame.getRemainingGas()).thenReturn(remainingGas);
        when(messageFrame.getInputData()).thenReturn(org.apache.tuweni.bytes.Bytes.EMPTY);

        when(proxyWorldUpdater.getAccount(any())).thenReturn(mutableAccount);
        when(proxyWorldUpdater.enhancement()).thenReturn(enhancement);
        when(enhancement.systemOperations()).thenReturn(systemContractOperations);
        when(mutableAccount.hederaId()).thenReturn(accountID);
    }
}
