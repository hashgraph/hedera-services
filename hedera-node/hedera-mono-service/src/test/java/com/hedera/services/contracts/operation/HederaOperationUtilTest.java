/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.WorldStateAccount;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaOperationUtilTest {
    private static final Address PRETEND_RECIPIENT_ADDR = Address.ALTBN128_ADD;
    private static final Address PRETEND_CONTRACT_ADDR = Address.ALTBN128_MUL;

    @Mock private MessageFrame messageFrame;
    @Mock private HederaStackedWorldStateUpdater hederaWorldUpdater;
    @Mock private HederaWorldState.Updater updater;
    @Mock private WorldStateAccount worldStateAccount;
    @Mock private EvmSigsVerifier sigsVerifier;
    @Mock private LongSupplier gasSupplier;
    @Mock private Supplier<Operation.OperationResult> executionSupplier;
    @Mock private Map<String, PrecompiledContract> precompiledContractMap;
    @Mock private WorldLedgers ledgers;

    private final long expectedHaltGas = 10L;
    private final long expectedSuccessfulGas = 100L;

    @Test
    void shortCircuitsForPrecompileSigCheck() {
        final var degenerateResult =
                new Operation.OperationResult(OptionalLong.empty(), Optional.empty());
        given(precompiledContractMap.containsKey(PRETEND_RECIPIENT_ADDR.toShortHexString()))
                .willReturn(true);
        given(executionSupplier.get()).willReturn(degenerateResult);

        final var result =
                HederaOperationUtil.addressSignatureCheckExecution(
                        sigsVerifier,
                        messageFrame,
                        PRETEND_RECIPIENT_ADDR,
                        gasSupplier,
                        executionSupplier,
                        (a, b) -> false,
                        precompiledContractMap);

        assertSame(degenerateResult, result);
    }

    @Test
    void throwsUnderflowExceptionWhenGettingAddress() {
        // given:
        given(messageFrame.getStackItem(0)).willThrow(new FixedStack.UnderflowException());
        given(gasSupplier.getAsLong()).willReturn(expectedHaltGas);

        // when:
        final var result =
                HederaOperationUtil.addressCheckExecution(
                        messageFrame,
                        () -> messageFrame.getStackItem(0),
                        gasSupplier,
                        executionSupplier,
                        (a, b) -> true);

        // then:
        assertEquals(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS, result.getHaltReason().get());
        assertEquals(expectedHaltGas, result.getGasCost().getAsLong());
        // and:
        verify(messageFrame).getStackItem(0);
        verify(messageFrame, never()).getWorldUpdater();
        verify(gasSupplier).getAsLong();
        verify(executionSupplier, never()).get();
    }

    @Test
    void haltsWithInvalidSolidityAddressWhenAccountCheckExecution() {
        // given:
        given(messageFrame.getStackItem(0)).willReturn(Address.ZERO);
        given(gasSupplier.getAsLong()).willReturn(expectedHaltGas);

        // when:
        final var result =
                HederaOperationUtil.addressCheckExecution(
                        messageFrame,
                        () -> messageFrame.getStackItem(0),
                        gasSupplier,
                        executionSupplier,
                        (a, b) -> false);

        // then:
        assertEquals(
                HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS, result.getHaltReason().get());
        assertEquals(expectedHaltGas, result.getGasCost().getAsLong());
        // and:
        verify(messageFrame).getStackItem(0);
        verify(gasSupplier).getAsLong();
        verify(executionSupplier, never()).get();
    }

    @Test
    void successfulWhenAddressCheckExecution() {
        // given:
        given(messageFrame.getStackItem(0)).willReturn(Address.ZERO);
        given(executionSupplier.get())
                .willReturn(
                        new Operation.OperationResult(
                                OptionalLong.of(expectedSuccessfulGas), Optional.empty()));

        // when:
        final var result =
                HederaOperationUtil.addressCheckExecution(
                        messageFrame,
                        () -> messageFrame.getStackItem(0),
                        gasSupplier,
                        executionSupplier,
                        (a, b) -> true);

        // when:
        assertTrue(result.getHaltReason().isEmpty());
        assertEquals(expectedSuccessfulGas, result.getGasCost().getAsLong());
        // and:
        verify(messageFrame).getStackItem(0);
        verify(gasSupplier, never()).getAsLong();
        verify(executionSupplier).get();
    }

    @Test
    void haltsWithInvalidSolidityAddressWhenAccountSignatureCheckExecution() {
        // given:
        given(messageFrame.getWorldUpdater()).willReturn(hederaWorldUpdater);
        given(gasSupplier.getAsLong()).willReturn(expectedHaltGas);

        // when:
        final var result =
                HederaOperationUtil.addressSignatureCheckExecution(
                        sigsVerifier,
                        messageFrame,
                        Address.ZERO,
                        gasSupplier,
                        executionSupplier,
                        (a, b) -> false,
                        precompiledContractMap);

        // then:
        assertEquals(
                HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS, result.getHaltReason().get());
        assertEquals(expectedHaltGas, result.getGasCost().getAsLong());
        // and:
        verify(messageFrame).getWorldUpdater();
        verify(hederaWorldUpdater).get(Address.ZERO);
        verify(gasSupplier).getAsLong();
        verify(executionSupplier, never()).get();
    }

    @Test
    void haltsWithInvalidSignatureWhenAccountSignatureCheckExecution() {
        // given:
        final var mockTarget = Address.ZERO;
        given(messageFrame.getRecipientAddress()).willReturn(Address.ALTBN128_ADD);
        given(messageFrame.getContractAddress()).willReturn(Address.ALTBN128_MUL);
        given(messageFrame.getWorldUpdater()).willReturn(hederaWorldUpdater);
        given(hederaWorldUpdater.get(Address.ZERO)).willReturn(worldStateAccount);
        given(worldStateAccount.getAddress()).willReturn(Address.ZERO);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                true, mockTarget, Address.ALTBN128_ADD, ledgers))
                .willReturn(false);
        given(gasSupplier.getAsLong()).willReturn(expectedHaltGas);
        given(hederaWorldUpdater.trackingLedgers()).willReturn(ledgers);

        // when:
        final var result =
                HederaOperationUtil.addressSignatureCheckExecution(
                        sigsVerifier,
                        messageFrame,
                        Address.ZERO,
                        gasSupplier,
                        executionSupplier,
                        (a, b) -> true,
                        precompiledContractMap);

        // then:
        assertEquals(HederaExceptionalHaltReason.INVALID_SIGNATURE, result.getHaltReason().get());
        assertEquals(expectedHaltGas, result.getGasCost().getAsLong());
        // and:
        verify(messageFrame).getWorldUpdater();
        verify(hederaWorldUpdater).get(Address.ZERO);
        verify(worldStateAccount).getAddress();
        verify(sigsVerifier)
                .hasActiveKeyOrNoReceiverSigReq(true, mockTarget, PRETEND_RECIPIENT_ADDR, ledgers);
        verify(gasSupplier).getAsLong();
        verify(executionSupplier, never()).get();
    }

    @Test
    void haltsWithInvalidSignatureWhenAccountSignatureCheckExecutionForNonDelegateCall() {
        // given:
        final var mockTarget = Address.ZERO;
        given(messageFrame.getRecipientAddress()).willReturn(Address.ALTBN128_MUL);
        given(messageFrame.getContractAddress()).willReturn(Address.ALTBN128_MUL);
        given(messageFrame.getWorldUpdater()).willReturn(hederaWorldUpdater);
        given(hederaWorldUpdater.get(Address.ZERO)).willReturn(worldStateAccount);
        given(worldStateAccount.getAddress()).willReturn(Address.ZERO);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                false, mockTarget, Address.ALTBN128_MUL, ledgers))
                .willReturn(false);
        given(gasSupplier.getAsLong()).willReturn(expectedHaltGas);
        given(hederaWorldUpdater.trackingLedgers()).willReturn(ledgers);

        // when:
        final var result =
                HederaOperationUtil.addressSignatureCheckExecution(
                        sigsVerifier,
                        messageFrame,
                        Address.ZERO,
                        gasSupplier,
                        executionSupplier,
                        (a, b) -> true,
                        precompiledContractMap);

        // then:
        assertEquals(HederaExceptionalHaltReason.INVALID_SIGNATURE, result.getHaltReason().get());
        assertEquals(expectedHaltGas, result.getGasCost().getAsLong());
        // and:
        verify(messageFrame).getWorldUpdater();
        verify(hederaWorldUpdater).get(Address.ZERO);
        verify(worldStateAccount).getAddress();
        verify(sigsVerifier)
                .hasActiveKeyOrNoReceiverSigReq(false, mockTarget, PRETEND_CONTRACT_ADDR, ledgers);
        verify(gasSupplier).getAsLong();
        verify(executionSupplier, never()).get();
    }

    @Test
    void successfulWhenAddressSignatureCheckExecution() {
        // given:
        final var mockTarget = Address.ZERO;
        givenFrameAddresses();
        given(messageFrame.getWorldUpdater()).willReturn(hederaWorldUpdater);
        given(hederaWorldUpdater.trackingLedgers()).willReturn(ledgers);
        given(hederaWorldUpdater.get(Address.ZERO)).willReturn(worldStateAccount);
        given(worldStateAccount.getAddress()).willReturn(Address.ZERO);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                true, mockTarget, PRETEND_RECIPIENT_ADDR, ledgers))
                .willReturn(true);
        given(executionSupplier.get())
                .willReturn(
                        new Operation.OperationResult(
                                OptionalLong.of(expectedSuccessfulGas), Optional.empty()));

        // when:
        final var result =
                HederaOperationUtil.addressSignatureCheckExecution(
                        sigsVerifier,
                        messageFrame,
                        Address.ZERO,
                        gasSupplier,
                        executionSupplier,
                        (a, b) -> true,
                        precompiledContractMap);

        // then:
        assertTrue(result.getHaltReason().isEmpty());
        assertEquals(expectedSuccessfulGas, result.getGasCost().getAsLong());
        // and:
        verify(messageFrame).getWorldUpdater();
        verify(hederaWorldUpdater).get(Address.ZERO);
        verify(worldStateAccount).getAddress();
        verify(sigsVerifier)
                .hasActiveKeyOrNoReceiverSigReq(true, mockTarget, PRETEND_RECIPIENT_ADDR, ledgers);
        verify(gasSupplier, never()).getAsLong();
        verify(executionSupplier).get();
    }

    @Test
    void setOriginalReadValue() {
        given(messageFrame.getRecipientAddress()).willReturn(PRETEND_RECIPIENT_ADDR);
        given(messageFrame.popStackItem()).willReturn(Bytes.of(1));
        given(messageFrame.getWorldUpdater()).willReturn(hederaWorldUpdater);
        given(hederaWorldUpdater.parentUpdater()).willReturn(Optional.of(updater));
        var frameStack = new ArrayDeque<MessageFrame>();
        frameStack.add(messageFrame);

        given(messageFrame.getMessageFrameStack()).willReturn(frameStack);
        TreeMap<Address, Map<Bytes, Pair<Bytes, Bytes>>> map = new TreeMap<>();
        given(updater.getStateChanges()).willReturn(map);
        given(hederaWorldUpdater.get(PRETEND_RECIPIENT_ADDR)).willReturn(worldStateAccount);

        Bytes32 key = UInt256.fromBytes(messageFrame.popStackItem());
        Account account = messageFrame.getWorldUpdater().get(messageFrame.getRecipientAddress());
        HederaOperationUtil.cacheExistingValue(
                messageFrame,
                PRETEND_RECIPIENT_ADDR,
                key,
                account.getStorageValue(UInt256.fromBytes(key)));

        assertTrue(updater.getStateChanges().containsKey(PRETEND_RECIPIENT_ADDR));
        assertTrue(updater.getStateChanges().get(PRETEND_RECIPIENT_ADDR).containsKey(UInt256.ONE));
    }

    private void givenFrameAddresses() {
        given(messageFrame.getRecipientAddress()).willReturn(PRETEND_RECIPIENT_ADDR);
        given(messageFrame.getContractAddress()).willReturn(PRETEND_CONTRACT_ADDR);
    }
}
