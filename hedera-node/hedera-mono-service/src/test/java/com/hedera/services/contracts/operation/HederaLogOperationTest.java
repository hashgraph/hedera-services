/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.contracts.operation;

import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.ILLEGAL_STATE_CHANGE;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hamcrest.Matchers;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({LogCaptureExtension.class, MockitoExtension.class})
class HederaLogOperationTest {
    private static final int numTopics = 2;
    private static final long reqGas = 1234L;
    private static final long numBytes = 12L;
    private static final long dataLocation = 13L;
    private static final Bytes firstLogTopic = Bytes.fromHexString("0xee");
    private static final Bytes secondLogTopic = Bytes.fromHexString("0xff");
    private static final byte[] rawNonMirrorAddress =
            unhex("abcdefabcdefabcdefbabcdefabcdefabcdefbbb");
    private static final EntityNum num = EntityNum.fromLong(1234L);
    private static final Address nonMirrorAddress = Address.wrap(Bytes.wrap(rawNonMirrorAddress));
    private static final Address mirrorAddress = num.toEvmAddress();
    private static final Address unknownAddress = EntityNum.MISSING_NUM.toEvmAddress();
    private static final Bytes data = Bytes.fromHexString("0xabcdef");
    private static final Operation.OperationResult insufficientGasResult =
            new Operation.OperationResult(OptionalLong.of(reqGas), Optional.of(INSUFFICIENT_GAS));
    private static final Operation.OperationResult illegalStateChangeResult =
            new Operation.OperationResult(
                    OptionalLong.of(reqGas), Optional.of(ILLEGAL_STATE_CHANGE));
    private static final Operation.OperationResult goodResult =
            new Operation.OperationResult(OptionalLong.of(reqGas), Optional.empty());

    @Mock private GasCalculator gasCalculator;
    @Mock private EVM evm;
    @Mock private MessageFrame frame;
    @Mock private HederaStackedWorldStateUpdater updater;
    @Mock private ContractAliases aliases;

    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private HederaLogOperation subject;

    @BeforeEach
    void setUp() {
        subject = new HederaLogOperation(numTopics, gasCalculator);
    }

    @Test
    void getsExpectedName() {
        assertEquals("LOG" + numTopics, subject.getName());
    }

    @Test
    void failsOnIllegalStateChange() {
        given(frame.popStackItem())
                .willReturn(Bytes.ofUnsignedLong(dataLocation))
                .willReturn(Bytes.ofUnsignedLong(numBytes));
        given(gasCalculator.logOperationGasCost(frame, dataLocation, numBytes, numTopics))
                .willReturn(reqGas);
        given(frame.isStatic()).willReturn(true);

        final var result = subject.execute(frame, evm);
        assertResultMatch(illegalStateChangeResult, result);
    }

    @Test
    void failsOnInsufficientGas() {
        final var insufficientGas = reqGas - 1L;
        given(frame.popStackItem())
                .willReturn(Bytes.ofUnsignedLong(dataLocation))
                .willReturn(Bytes.ofUnsignedLong(numBytes));
        given(gasCalculator.logOperationGasCost(frame, dataLocation, numBytes, numTopics))
                .willReturn(reqGas);
        given(frame.getRemainingGas()).willReturn(insufficientGas);

        final var result = subject.execute(frame, evm);
        assertResultMatch(insufficientGasResult, result);
    }

    @Test
    void getsExpectedResultForHappyPath() {
        final var captor = ArgumentCaptor.forClass(Log.class);
        final var expectedLog =
                new Log(
                        mirrorAddress,
                        data,
                        List.of(
                                LogTopic.create(Bytes32.leftPad(firstLogTopic)),
                                LogTopic.create(Bytes32.leftPad(secondLogTopic))));

        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.aliases()).willReturn(aliases);
        given(aliases.isMirror(mirrorAddress)).willReturn(true);
        given(aliases.resolveForEvm(nonMirrorAddress)).willReturn(mirrorAddress);

        final var adequateGas = reqGas + 1L;
        given(frame.popStackItem())
                .willReturn(Bytes.ofUnsignedLong(dataLocation))
                .willReturn(Bytes.ofUnsignedLong(numBytes))
                .willReturn(firstLogTopic)
                .willReturn(secondLogTopic);
        given(gasCalculator.logOperationGasCost(frame, dataLocation, numBytes, numTopics))
                .willReturn(reqGas);
        given(frame.getRemainingGas()).willReturn(adequateGas);
        given(frame.getRecipientAddress()).willReturn(nonMirrorAddress);
        given(frame.readMemory(dataLocation, numBytes)).willReturn(data);

        final var result = subject.execute(frame, evm);

        assertResultMatch(goodResult, result);
        verify(frame).addLog(captor.capture());
        assertEquals(expectedLog, captor.getValue());
    }

    @Test
    void getsExpectedResultForHappyPathWithUnresolvable() {
        final var captor = ArgumentCaptor.forClass(Log.class);
        final var expectedLog =
                new Log(
                        unknownAddress,
                        data,
                        List.of(
                                LogTopic.create(Bytes32.leftPad(firstLogTopic)),
                                LogTopic.create(Bytes32.leftPad(secondLogTopic))));

        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(nonMirrorAddress)).willReturn(nonMirrorAddress);

        final var adequateGas = reqGas + 1L;
        given(frame.popStackItem())
                .willReturn(Bytes.ofUnsignedLong(dataLocation))
                .willReturn(Bytes.ofUnsignedLong(numBytes))
                .willReturn(firstLogTopic)
                .willReturn(secondLogTopic);
        given(gasCalculator.logOperationGasCost(frame, dataLocation, numBytes, numTopics))
                .willReturn(reqGas);
        given(frame.getRemainingGas()).willReturn(adequateGas);
        given(frame.getRecipientAddress()).willReturn(nonMirrorAddress);
        given(frame.readMemory(dataLocation, numBytes)).willReturn(data);

        final var result = subject.execute(frame, evm);

        assertResultMatch(goodResult, result);
        verify(frame).addLog(captor.capture());
        assertEquals(expectedLog, captor.getValue());

        assertThat(
                logCaptor.warnLogs(),
                contains(Matchers.equalTo("Could not resolve logger address " + nonMirrorAddress)));
    }

    private void assertResultMatch(
            final Operation.OperationResult expected, final Operation.OperationResult actual) {
        assertEquals(expected.getGasCost(), actual.getGasCost());
        assertEquals(expected.getHaltReason(), actual.getHaltReason());
    }
}
