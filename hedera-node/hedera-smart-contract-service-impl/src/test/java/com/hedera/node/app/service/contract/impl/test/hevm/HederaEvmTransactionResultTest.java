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

package com.hedera.node.app.service.contract.impl.test.hevm;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.SELF_DESTRUCT_TO_SELF;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.BESU_LOGS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CALLED_CONTRACT_EVM_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CALLED_CONTRACT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CHILD_CONTRACT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ETH_DATA_WITH_TO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.GAS_LIMIT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NONCES;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OUTPUT_DATA;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SOME_REVERT_REASON;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SOME_STORAGE_ACCESSES;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.TWO_STORAGE_ACCESSES;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.WEI_NETWORK_GAS_PRICE;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.bloomForAll;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjLogsFrom;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToTuweniBytes;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason;
import com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransactionResult;
import com.hedera.node.app.service.contract.impl.infra.StorageAccessTracker;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaEvmTransactionResultTest {
    @Mock
    private MessageFrame frame;

    @Mock
    private Deque<MessageFrame> stack;

    @Mock
    private ProxyWorldUpdater proxyWorldUpdater;

    @Mock
    private RootProxyWorldUpdater rootProxyWorldUpdater;

    @Mock
    private StorageAccessTracker accessTracker;

    @BeforeEach
    void setUp() {
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.isEmpty()).willReturn(true);
    }

    @Test
    void finalStatusFromHaltUsesCorrespondingStatusIfFromCustom() {
        given(frame.getGasPrice()).willReturn(WEI_NETWORK_GAS_PRICE);
        given(frame.getExceptionalHaltReason()).willReturn(Optional.of(SELF_DESTRUCT_TO_SELF));
        final var subject = HederaEvmTransactionResult.failureFrom(GAS_LIMIT / 2, SENDER_ID, frame);
        assertEquals(OBTAINER_SAME_CONTRACT_ID, subject.finalStatus());
        final var protoResult = subject.asProtoResultOf(rootProxyWorldUpdater);
        assertEquals(SELF_DESTRUCT_TO_SELF.toString(), protoResult.errorMessage());
    }

    @Test
    void finalStatusFromHaltUsesCorrespondingStatusIfFromStandard() {
        given(frame.getGasPrice()).willReturn(WEI_NETWORK_GAS_PRICE);
        given(frame.getExceptionalHaltReason()).willReturn(Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
        final var subject = HederaEvmTransactionResult.failureFrom(GAS_LIMIT / 2, SENDER_ID, frame);
        assertEquals(INSUFFICIENT_GAS, subject.finalStatus());
        final var protoResult = subject.asProtoResultOf(rootProxyWorldUpdater);
        assertEquals(ExceptionalHaltReason.INSUFFICIENT_GAS.toString(), protoResult.errorMessage());
    }

    @Test
    void finalStatusFromInsufficientGasHaltImplemented() {
        given(frame.getGasPrice()).willReturn(WEI_NETWORK_GAS_PRICE);
        given(frame.getExceptionalHaltReason()).willReturn(Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
        final var subject = HederaEvmTransactionResult.failureFrom(GAS_LIMIT / 2, SENDER_ID, frame);
        assertEquals(ResponseCodeEnum.INSUFFICIENT_GAS, subject.finalStatus());
    }

    @Test
    void finalStatusFromMissingAddressHaltImplemented() {
        given(frame.getGasPrice()).willReturn(WEI_NETWORK_GAS_PRICE);
        given(frame.getExceptionalHaltReason())
                .willReturn(Optional.of(CustomExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS));
        final var subject = HederaEvmTransactionResult.failureFrom(GAS_LIMIT / 2, SENDER_ID, frame);
        assertEquals(ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS, subject.finalStatus());
    }

    @Test
    void givenAccessTrackerIncludesFullContractStorageChangesAndNonNullNoncesOnSuccess() {
        given(frame.getContextVariable(FrameUtils.TRACKER_CONTEXT_VARIABLE)).willReturn(accessTracker);
        given(frame.getWorldUpdater()).willReturn(proxyWorldUpdater);
        final var pendingWrites = List.of(TWO_STORAGE_ACCESSES);
        given(proxyWorldUpdater.pendingStorageUpdates()).willReturn(pendingWrites);
        given(accessTracker.getReadsMergedWith(pendingWrites)).willReturn(SOME_STORAGE_ACCESSES);
        given(frame.getGasPrice()).willReturn(WEI_NETWORK_GAS_PRICE);
        given(frame.getLogs()).willReturn(BESU_LOGS);
        given(frame.getOutputData()).willReturn(pbjToTuweniBytes(OUTPUT_DATA));
        final var createdIds = List.of(CALLED_CONTRACT_ID, CHILD_CONTRACT_ID);
        given(rootProxyWorldUpdater.getCreatedContractIds()).willReturn(createdIds);
        given(rootProxyWorldUpdater.getUpdatedContractNonces()).willReturn(NONCES);

        final var result = HederaEvmTransactionResult.successFrom(
                GAS_LIMIT / 2, SENDER_ID, CALLED_CONTRACT_ID, CALLED_CONTRACT_EVM_ADDRESS, frame);
        final var protoResult = result.asProtoResultOf(rootProxyWorldUpdater);
        assertEquals(GAS_LIMIT / 2, protoResult.gasUsed());
        assertEquals(bloomForAll(BESU_LOGS), protoResult.bloom());
        assertEquals(OUTPUT_DATA, protoResult.contractCallResult());
        assertNull(protoResult.errorMessage());
        assertNull(protoResult.senderId());
        assertEquals(CALLED_CONTRACT_ID, protoResult.contractID());
        assertEquals(pbjLogsFrom(BESU_LOGS), protoResult.logInfo());
        assertEquals(createdIds, protoResult.createdContractIDs());
        assertEquals(CALLED_CONTRACT_EVM_ADDRESS.evmAddressOrThrow(), protoResult.evmAddress());
        assertEquals(NONCES, protoResult.contractNonces());

        final var expectedChanges = ConversionUtils.asPbjStateChanges(SOME_STORAGE_ACCESSES);
        assertEquals(expectedChanges, result.stateChanges());
        assertEquals(SUCCESS, result.finalStatus());
    }

    @Test
    void givenEthTxDataIncludesSpecialFields() {
        given(frame.getContextVariable(FrameUtils.TRACKER_CONTEXT_VARIABLE)).willReturn(accessTracker);
        given(frame.getWorldUpdater()).willReturn(proxyWorldUpdater);
        final var pendingWrites = List.of(TWO_STORAGE_ACCESSES);
        given(proxyWorldUpdater.pendingStorageUpdates()).willReturn(pendingWrites);
        given(accessTracker.getReadsMergedWith(pendingWrites)).willReturn(SOME_STORAGE_ACCESSES);
        given(frame.getGasPrice()).willReturn(WEI_NETWORK_GAS_PRICE);
        given(frame.getLogs()).willReturn(BESU_LOGS);
        given(frame.getOutputData()).willReturn(pbjToTuweniBytes(OUTPUT_DATA));
        final var createdIds = List.of(CALLED_CONTRACT_ID, CHILD_CONTRACT_ID);
        given(rootProxyWorldUpdater.getCreatedContractIds()).willReturn(createdIds);
        given(rootProxyWorldUpdater.getUpdatedContractNonces()).willReturn(NONCES);

        final var result = HederaEvmTransactionResult.successFrom(
                GAS_LIMIT / 2, SENDER_ID, CALLED_CONTRACT_ID, CALLED_CONTRACT_EVM_ADDRESS, frame);
        final var protoResult = result.asProtoResultOf(ETH_DATA_WITH_TO_ADDRESS, rootProxyWorldUpdater);
        assertEquals(ETH_DATA_WITH_TO_ADDRESS.gasLimit(), protoResult.gas());
        assertEquals(ETH_DATA_WITH_TO_ADDRESS.getAmount(), protoResult.amount());
        assertArrayEquals(
                ETH_DATA_WITH_TO_ADDRESS.callData(),
                protoResult.functionParameters().toByteArray());
        assertEquals(SENDER_ID, protoResult.senderId());
        assertEquals(GAS_LIMIT / 2, protoResult.gasUsed());
        assertEquals(bloomForAll(BESU_LOGS), protoResult.bloom());
        assertEquals(OUTPUT_DATA, protoResult.contractCallResult());
        assertNull(protoResult.errorMessage());
        assertEquals(CALLED_CONTRACT_ID, protoResult.contractID());
        assertEquals(pbjLogsFrom(BESU_LOGS), protoResult.logInfo());
        assertEquals(createdIds, protoResult.createdContractIDs());
        assertEquals(CALLED_CONTRACT_EVM_ADDRESS.evmAddressOrThrow(), protoResult.evmAddress());
        assertEquals(NONCES, protoResult.contractNonces());

        final var expectedChanges = ConversionUtils.asPbjStateChanges(SOME_STORAGE_ACCESSES);
        assertEquals(expectedChanges, result.stateChanges());
        assertEquals(SUCCESS, result.finalStatus());
    }

    @Test
    void givenAccessTrackerIncludesReadStorageAccessesOnlyOnFailure() {
        given(frame.getContextVariable(FrameUtils.TRACKER_CONTEXT_VARIABLE)).willReturn(accessTracker);
        given(accessTracker.getJustReads()).willReturn(SOME_STORAGE_ACCESSES);
        given(frame.getGasPrice()).willReturn(WEI_NETWORK_GAS_PRICE);

        final var result = HederaEvmTransactionResult.failureFrom(GAS_LIMIT / 2, SENDER_ID, frame);

        final var expectedChanges = ConversionUtils.asPbjStateChanges(SOME_STORAGE_ACCESSES);
        assertEquals(expectedChanges, result.stateChanges());
    }

    @Test
    void withoutAccessTrackerReturnsNullStateChanges() {
        given(frame.getGasPrice()).willReturn(WEI_NETWORK_GAS_PRICE);
        given(frame.getOutputData()).willReturn(pbjToTuweniBytes(OUTPUT_DATA));

        final var result = HederaEvmTransactionResult.successFrom(
                GAS_LIMIT / 2, SENDER_ID, CALLED_CONTRACT_ID, CALLED_CONTRACT_EVM_ADDRESS, frame);

        assertNull(result.stateChanges());
    }

    @Test
    void QueryResultOnSuccess() {
        given(frame.getGasPrice()).willReturn(WEI_NETWORK_GAS_PRICE);
        given(frame.getLogs()).willReturn(BESU_LOGS);
        given(frame.getOutputData()).willReturn(pbjToTuweniBytes(OUTPUT_DATA));

        final var result = HederaEvmTransactionResult.successFrom(
                GAS_LIMIT / 2, SENDER_ID, CALLED_CONTRACT_ID, CALLED_CONTRACT_EVM_ADDRESS, frame);
        final var queryResult = result.asQueryResult();
        assertEquals(GAS_LIMIT / 2, queryResult.gasUsed());
        assertEquals(bloomForAll(BESU_LOGS), queryResult.bloom());
        assertEquals(OUTPUT_DATA, queryResult.contractCallResult());
        assertNull(queryResult.errorMessage());
        assertNull(queryResult.senderId());
        assertEquals(CALLED_CONTRACT_ID, queryResult.contractID());
        assertEquals(pbjLogsFrom(BESU_LOGS), queryResult.logInfo());
    }

    @Test
    void QueryResultOnHalt() {
        given(frame.getGasPrice()).willReturn(WEI_NETWORK_GAS_PRICE);
        given(frame.getExceptionalHaltReason()).willReturn(Optional.of(ExceptionalHaltReason.INVALID_OPERATION));

        final var result = HederaEvmTransactionResult.failureFrom(GAS_LIMIT / 2, SENDER_ID, frame);
        final var protoResult = result.asQueryResult();
        assertEquals(ExceptionalHaltReason.INVALID_OPERATION.toString(), protoResult.errorMessage());
    }

    @Test
    void QueryResultOnRevert() {
        given(frame.getGasPrice()).willReturn(WEI_NETWORK_GAS_PRICE);
        given(frame.getRevertReason()).willReturn(Optional.of(SOME_REVERT_REASON));

        final var result = HederaEvmTransactionResult.failureFrom(GAS_LIMIT / 2, SENDER_ID, frame);
        final var protoResult = result.asQueryResult();
        assertEquals(SOME_REVERT_REASON.toString(), protoResult.errorMessage());
    }
}
