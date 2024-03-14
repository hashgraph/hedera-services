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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.balanceof.BalanceOfTranslator.BALANCE_OF;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_CONFIG;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_SYSTEM_LONG_ZERO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.bytesForRedirect;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAddressChecks;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallFactory;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.SyntheticIds;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.balanceof.BalanceOfCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.balanceof.BalanceOfTranslator;
import com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class HtsCallFactoryTest extends HtsCallTestBase {
    @Mock
    private HtsCallAddressChecks addressChecks;

    @Mock
    private SystemContractGasCalculator systemContractGasCalculator;

    @Mock
    private VerificationStrategies verificationStrategies;

    @Mock
    private AddressIdConverter idConverter;

    @Mock
    private SyntheticIds syntheticIds;

    @Mock
    private MessageFrame frame;

    @Mock
    private MessageFrame initialFrame;

    private Deque<MessageFrame> stack = new ArrayDeque<>();

    @Mock
    private ProxyWorldUpdater updater;

    private HtsCallFactory subject;

    @BeforeEach
    void setUp() {
        subject = new HtsCallFactory(
                syntheticIds, addressChecks, verificationStrategies, List.of(new BalanceOfTranslator()));
    }

    @Test
    void instantiatesCallWithInContextEnhancementAndDelegateCallInfo() {
        given(initialFrame.getContextVariable(FrameUtils.CONFIG_CONTEXT_VARIABLE))
                .willReturn(DEFAULT_CONFIG);
        given(initialFrame.getContextVariable(FrameUtils.SYSTEM_CONTRACT_GAS_CALCULATOR_CONTEXT_VARIABLE))
                .willReturn(systemContractGasCalculator);
        stack.push(initialFrame);
        stack.addFirst(frame);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.enhancement()).willReturn(mockEnhancement());
        given(nativeOperations.getToken(FUNGIBLE_TOKEN_ID.tokenNum())).willReturn(FUNGIBLE_TOKEN);
        given(frame.getSenderAddress()).willReturn(EIP_1014_ADDRESS);
        given(addressChecks.hasParentDelegateCall(frame)).willReturn(true);
        given(syntheticIds.converterFor(nativeOperations)).willReturn(idConverter);

        final var input = bytesForRedirect(
                BALANCE_OF.encodeCallWithArgs(asHeadlongAddress(NON_SYSTEM_LONG_ZERO_ADDRESS)), FUNGIBLE_TOKEN_ID);
        final var attempt = subject.createCallAttemptFrom(input, FrameUtils.CallType.DIRECT_OR_TOKEN_REDIRECT, frame);
        final var call = Objects.requireNonNull(attempt.asExecutableCall());

        assertInstanceOf(BalanceOfCall.class, call);
    }

    @Test
    void instantiatesQualifiedDelegateCallWithRecipientAsSender() {
        given(initialFrame.getContextVariable(FrameUtils.CONFIG_CONTEXT_VARIABLE))
                .willReturn(DEFAULT_CONFIG);
        given(initialFrame.getContextVariable(FrameUtils.SYSTEM_CONTRACT_GAS_CALCULATOR_CONTEXT_VARIABLE))
                .willReturn(systemContractGasCalculator);
        stack.push(initialFrame);
        stack.addFirst(frame);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.enhancement()).willReturn(mockEnhancement());
        given(nativeOperations.getToken(FUNGIBLE_TOKEN_ID.tokenNum())).willReturn(FUNGIBLE_TOKEN);
        given(frame.getSenderAddress()).willReturn(Address.ALTBN128_ADD);
        given(idConverter.convertSender(Address.ALTBN128_ADD)).willReturn(A_NEW_ACCOUNT_ID);
        given(frame.getRecipientAddress()).willReturn(EIP_1014_ADDRESS);
        given(addressChecks.hasParentDelegateCall(frame)).willReturn(true);
        given(syntheticIds.converterFor(nativeOperations)).willReturn(idConverter);

        final var input = bytesForRedirect(
                BALANCE_OF.encodeCallWithArgs(asHeadlongAddress(NON_SYSTEM_LONG_ZERO_ADDRESS)), FUNGIBLE_TOKEN_ID);
        final var attempt = subject.createCallAttemptFrom(input, FrameUtils.CallType.QUALIFIED_DELEGATE, frame);
        final var call = Objects.requireNonNull(attempt.asExecutableCall());

        assertInstanceOf(BalanceOfCall.class, call);
        assertEquals(A_NEW_ACCOUNT_ID, attempt.senderId());
    }
}
