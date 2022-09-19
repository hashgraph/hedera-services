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

import static com.hedera.services.contracts.operation.HederaSStoreOperation.ILLEGAL_STATE_CHANGE_RESULT;
import static com.hedera.services.stream.proto.SidecarType.CONTRACT_STATE_CHANGE;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.ILLEGAL_STATE_CHANGE;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.hyperledger.besu.evm.operation.SStoreOperation.EIP_1706_MINIMUM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.store.contracts.HederaWorldUpdater;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaSStoreOperationTest {
    @Mock private EVM evm;
    @Mock private EvmAccount evmAccount;
    @Mock private GasCalculator gasCalculator;
    @Mock private MessageFrame frame;
    @Mock private MutableAccount mutableAccount;
    @Mock private HederaWorldUpdater updater;
    @Mock private GlobalDynamicProperties dynamicProperties;

    private HederaSStoreOperation subject;

    @BeforeEach
    void setUp() {
        subject = new HederaSStoreOperation(EIP_1706_MINIMUM, gasCalculator, dynamicProperties);
    }

    @Test
    void recognizesImmutableAccount() {
        givenStackItemsAndRecipientAccount();

        final var result = subject.execute(frame, evm);

        assertSame(ILLEGAL_STATE_CHANGE_RESULT, result);
    }

    @Test
    void recognizesIllegalStateChange() {
        givenStackItemsAndMutableRecipientAccount();
        givenColdSlot();
        given(frame.isStatic()).willReturn(true);

        final var expected =
                new Operation.OperationResult(
                        OptionalLong.of(storageCost + coldSloadCost),
                        Optional.of(ILLEGAL_STATE_CHANGE));

        final var actual = subject.execute(frame, evm);

        assertResultsEqual(expected, actual);
    }

    @Test
    void recognizesInsufficientRemainingGas() {
        givenStackItemsAndMutableRecipientAccount();
        givenWarmSlot();
        givenRemainingGas(insufficientRemainingGas);

        final var actual = subject.execute(frame, evm);

        assertSame(subject.getInsufficientMinimumGasRemainingResult(), actual);
    }

    @Test
    void recognizesInsufficientGas() {
        subject = new HederaSStoreOperation(0, gasCalculator, dynamicProperties);

        givenStackItemsAndMutableRecipientAccount();
        givenColdSlot();
        givenRemainingGas(storageCost);
        final var expected =
                new Operation.OperationResult(
                        OptionalLong.of(storageCost + coldSloadCost),
                        Optional.of(INSUFFICIENT_GAS));

        final var actual = subject.execute(frame, evm);

        assertResultsEqual(expected, actual);
    }

    @Test
    void happyPathWithoutTraceability() {
        givenStackItemsAndMutableRecipientAccount();
        givenColdSlot();
        givenRemainingGas(sufficientRemainingGas);
        given(gasCalculator.calculateStorageRefundAmount(mutableAccount, key, value))
                .willReturn(storageRefundAmount);
        final var expected =
                new Operation.OperationResult(
                        OptionalLong.of(storageCost + coldSloadCost), Optional.empty());

        final var actual = subject.execute(frame, evm);

        assertResultsEqual(expected, actual);
        verify(frame).incrementGasRefund(storageRefundAmount);
        verify(mutableAccount).setStorageValue(key, value);
        verify(frame).storageWasUpdated(key, value);
        verify(frame).warmUpStorage(recipient, key);
    }

    @Test
    void happyPathWithTraceability() {
        givenStackItemsAndMutableRecipientAccount();
        givenColdSlot();
        givenRemainingGas(sufficientRemainingGas);
        given(gasCalculator.calculateStorageRefundAmount(mutableAccount, key, value))
                .willReturn(storageRefundAmount);
        final var expected =
                new Operation.OperationResult(
                        OptionalLong.of(storageCost + coldSloadCost), Optional.empty());
        final var messageStack = new ArrayDeque<MessageFrame>();
        messageStack.add(frame);
        given(frame.getMessageFrameStack()).willReturn(messageStack);
        given(updater.parentUpdater()).willReturn(Optional.empty());
        given(dynamicProperties.enabledSidecars()).willReturn(Set.of(CONTRACT_STATE_CHANGE));
        given(mutableAccount.getAddress()).willReturn(unaliasedRecipient);

        final var actual = subject.execute(frame, evm);

        assertResultsEqual(expected, actual);
        verify(frame).incrementGasRefund(storageRefundAmount);
        verify(mutableAccount).setStorageValue(key, value);
        verify(frame).storageWasUpdated(key, value);
        verify(frame).warmUpStorage(unaliasedRecipient, key);
    }

    private void givenStackItemsAndMutableRecipientAccount() {
        givenStackItemsAndRecipientAccount();
        given(evmAccount.getMutable()).willReturn(mutableAccount);
        given(mutableAccount.getAddress()).willReturn(recipient);
        given(gasCalculator.calculateStorageCost(any(), any(), any())).willReturn(storageCost);
    }

    private void givenStackItemsAndRecipientAccount() {
        given(frame.popStackItem()).willReturn(key.toBytes()).willReturn(value.toBytes());
        given(frame.getWorldUpdater()).willReturn(updater);
        given(frame.getRecipientAddress()).willReturn(recipient);
        given(updater.getAccount(recipient)).willReturn(evmAccount);
    }

    private void givenWarmSlot() {
        given(frame.warmUpStorage(recipient, key)).willReturn(true);
    }

    private void givenColdSlot() {
        given(gasCalculator.getColdSloadCost()).willReturn(coldSloadCost);
    }

    private void assertResultsEqual(
            final Operation.OperationResult expected, final Operation.OperationResult actual) {
        assertEquals(expected.getGasCost(), actual.getGasCost());
        assertEquals(expected.getHaltReason(), actual.getHaltReason());
    }

    private void givenRemainingGas(final long amount) {
        given(frame.getRemainingGas()).willReturn(amount);
    }

    private static final UInt256 key = UInt256.fromBytes(Bytes.fromHexStringLenient("0x1234"));
    private static final UInt256 value = UInt256.fromBytes(Bytes.fromHexStringLenient("0x5678"));
    private static final long storageCost = 10;
    private static final long coldSloadCost = 32;
    private static final long storageRefundAmount = 16;
    private static final long sufficientRemainingGas = EIP_1706_MINIMUM + 1L;
    private static final long insufficientRemainingGas = EIP_1706_MINIMUM - 1L;
    private static final Address recipient = Address.ALTBN128_ADD;
    private static final Address unaliasedRecipient = Address.BLS12_G2MULTIEXP;
}
