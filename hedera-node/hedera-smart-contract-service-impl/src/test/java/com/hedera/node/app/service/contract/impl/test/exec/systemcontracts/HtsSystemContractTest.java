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

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.haltResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.callTypeOf;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.isDelegateCall;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.proxyUpdaterFor;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.assertSamePrecompileResult;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import com.hedera.node.app.service.contract.impl.exec.scope.SystemContractOperations;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsSystemContract;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallFactory;
import com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import java.nio.ByteBuffer;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HtsSystemContractTest {
    @Mock
    private HtsCall call;

    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private MessageFrame frame;

    @Mock
    private ProxyWorldUpdater updater;

    @Mock
    private HederaWorldUpdater.Enhancement enhancement;

    @Mock
    private SystemContractOperations systemOperations;

    @Mock
    private HtsCallFactory attemptFactory;

    @Mock
    private GasCalculator gasCalculator;

    private MockedStatic<FrameUtils> frameUtils;

    private HtsSystemContract subject;
    private final Bytes validInput = Bytes.fromHexString("91548228");

    @BeforeEach
    void setUp() {
        frameUtils = Mockito.mockStatic(FrameUtils.class);
        subject = new HtsSystemContract(gasCalculator, attemptFactory);
    }

    @AfterEach
    void clear() {
        frameUtils.close();
    }

    @Test
    void returnsResultFromImpliedCall() {
        givenValidCallAttempt();
        frameUtils.when(() -> callTypeOf(frame)).thenReturn(FrameUtils.CallType.DIRECT_OR_TOKEN_REDIRECT);

        final var pricedResult = gasOnly(successResult(ByteBuffer.allocate(1), 123L), SUCCESS, true);
        given(call.execute(frame)).willReturn(pricedResult);
        given(attempt.senderId()).willReturn(SENDER_ID);

        assertSame(pricedResult.fullResult(), subject.computeFully(validInput, frame));
    }

    @Test
    void invalidCallAttemptHaltsAndConsumesRemainingGas() {
        given(attemptFactory.createCallAttemptFrom(Bytes.EMPTY, FrameUtils.CallType.DIRECT_OR_TOKEN_REDIRECT, frame))
                .willThrow(RuntimeException.class);
        final var expected = haltResult(ExceptionalHaltReason.INVALID_OPERATION, frame.getRemainingGas());
        final var result = subject.computeFully(validInput, frame);
        assertSamePrecompileResult(expected, result);
    }

    @Test
    void internalErrorAttemptHaltsAndConsumesRemainingGas() {
        givenValidCallAttempt();
        frameUtils.when(() -> callTypeOf(frame)).thenReturn(FrameUtils.CallType.DIRECT_OR_TOKEN_REDIRECT);
        given(call.execute(frame)).willThrow(RuntimeException.class);

        final var expected = haltResult(ExceptionalHaltReason.PRECOMPILE_ERROR, frame.getRemainingGas());
        final var result = subject.computeFully(validInput, frame);
        assertSamePrecompileResult(expected, result);
    }

    @Test
    void testComputeFullyWithEmptyBytes() {
        final var expected = haltResult(ExceptionalHaltReason.INVALID_OPERATION, frame.getRemainingGas());
        final var result = subject.computeFully(Bytes.EMPTY, frame);
        assertSamePrecompileResult(expected, result);
    }

    private void givenValidCallAttempt() {
        frameUtils.when(() -> isDelegateCall(frame)).thenReturn(false);
        frameUtils.when(() -> proxyUpdaterFor(frame)).thenReturn(updater);
        lenient().when(updater.enhancement()).thenReturn(enhancement);
        lenient().when(enhancement.systemOperations()).thenReturn(systemOperations);
        given(attemptFactory.createCallAttemptFrom(validInput, FrameUtils.CallType.DIRECT_OR_TOKEN_REDIRECT, frame))
                .willReturn(attempt);
        given(attempt.asExecutableCall()).willReturn(call);
    }
}
