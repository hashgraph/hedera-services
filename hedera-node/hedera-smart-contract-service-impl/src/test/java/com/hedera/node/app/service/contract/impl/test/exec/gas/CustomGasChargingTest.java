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

package com.hedera.node.app.service.contract.impl.test.exec.gas;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.GAS_LIMIT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.MAX_GAS_ALLOWANCE;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NETWORK_GAS_PRICE;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.RELAYER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.assertFailsWith;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.wellKnownContextWith;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.wellKnownHapiCall;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.wellKnownRelayedHapiCallWithGasLimit;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.wellKnownRelayedHapiCallWithUserGasPriceAndMaxAllowance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.hedera.node.app.service.contract.impl.exec.gas.CustomGasCharging;
import com.hedera.node.app.service.contract.impl.exec.gas.TinybarValues;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmBlocks;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.HederaEvmAccount;
import com.hedera.node.app.service.contract.impl.test.TestHelpers;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomGasChargingTest {
    @Mock
    private HederaEvmAccount sender;

    @Mock
    private HederaEvmAccount relayer;

    @Mock
    private HederaEvmBlocks blocks;

    @Mock
    private TinybarValues tinybarValues;

    @Mock
    private HederaWorldUpdater worldUpdater;

    @Mock
    private GasCalculator gasCalculator;

    private CustomGasCharging subject;

    @BeforeEach
    void setUp() {
        subject = new CustomGasCharging(gasCalculator);
    }

    @Test
    void staticCallsDoNotChargeGas() {
        final var chargingResult = subject.chargeForGas(
                sender, relayer, wellKnownContextWith(blocks, true, tinybarValues), worldUpdater, wellKnownHapiCall());
        assertEquals(0, chargingResult.relayerAllowanceUsed());
        verifyNoInteractions(gasCalculator, worldUpdater);
    }

    @Test
    void zeroPriceGasDoesNoChargingWork() {
        final var context = new HederaEvmContext(0L, false, blocks, tinybarValues);
        final var chargingResult = subject.chargeForGas(sender, relayer, context, worldUpdater, wellKnownHapiCall());
        assertEquals(0, chargingResult.relayerAllowanceUsed());
        verifyNoInteractions(gasCalculator, worldUpdater);
    }

    @Test
    void zeroPriceGasReturnsImmediately() {
        final var context = new HederaEvmContext(0L, false, blocks, tinybarValues);
        final var chargingResult = subject.chargeForGas(sender, relayer, context, worldUpdater, wellKnownHapiCall());
        assertEquals(0, chargingResult.relayerAllowanceUsed());
        verifyNoInteractions(gasCalculator);
    }

    @Test
    void staticCallsDoNotRefundGas() {
        subject.maybeRefundGiven(
                GAS_LIMIT / 2,
                MAX_GAS_ALLOWANCE / 2,
                sender,
                relayer,
                wellKnownContextWith(blocks, true, tinybarValues),
                worldUpdater);
        verifyNoInteractions(worldUpdater);
    }

    @Test
    void consumedGasDoesNotRefundAnything() {
        subject.maybeRefundGiven(
                0, MAX_GAS_ALLOWANCE / 2, sender, relayer, wellKnownContextWith(blocks, tinybarValues), worldUpdater);
        verifyNoInteractions(worldUpdater);
    }

    @Test
    void withNoRelayerSenderGetsFullRefund() {
        given(sender.hederaId()).willReturn(SENDER_ID);
        final var unusedGas = GAS_LIMIT / 2;
        subject.maybeRefundGiven(unusedGas, 0, sender, null, wellKnownContextWith(blocks, tinybarValues), worldUpdater);
        verify(worldUpdater).refundFee(SENDER_ID, unusedGas * NETWORK_GAS_PRICE);
    }

    @Test
    void relayerGetsPriorityAndSenderGetsRemainder() {
        given(sender.hederaId()).willReturn(SENDER_ID);
        given(relayer.hederaId()).willReturn(RELAYER_ID);
        final var unusedGas = GAS_LIMIT / 2;
        final var refund = unusedGas * NETWORK_GAS_PRICE;
        final var allowanceUsed = 2 * refund / 3;
        subject.maybeRefundGiven(
                unusedGas, allowanceUsed, sender, relayer, wellKnownContextWith(blocks, tinybarValues), worldUpdater);
        verify(worldUpdater).refundFee(RELAYER_ID, allowanceUsed);
        verify(worldUpdater).refundFee(SENDER_ID, refund - allowanceUsed);
    }

    @Test
    void senderGetsNoRefundIfNoneRemaining() {
        given(relayer.hederaId()).willReturn(RELAYER_ID);
        final var unusedGas = GAS_LIMIT / 2;
        final var refund = unusedGas * NETWORK_GAS_PRICE;
        final var allowanceUsed = 3 * refund / 2;
        subject.maybeRefundGiven(
                unusedGas, allowanceUsed, sender, relayer, wellKnownContextWith(blocks, tinybarValues), worldUpdater);
        verify(worldUpdater).refundFee(RELAYER_ID, refund);
        verifyNoMoreInteractions(worldUpdater);
    }

    @Test
    void failsImmediatelyIfGasLimitBelowIntrinsicGas() {
        givenWellKnownIntrinsicGasCost();
        assertFailsWith(
                INSUFFICIENT_GAS,
                () -> subject.chargeForGas(
                        sender,
                        relayer,
                        wellKnownContextWith(blocks, tinybarValues),
                        worldUpdater,
                        wellKnownRelayedHapiCallWithGasLimit(TestHelpers.INTRINSIC_GAS - 1)));
    }

    @Test
    void failsImmediatelyIfPayerBalanceBelowUpfrontCost() {
        givenWellKnownIntrinsicGasCost();
        final var transaction = wellKnownHapiCall();
        given(sender.getBalance()).willReturn(Wei.of(transaction.upfrontCostGiven(NETWORK_GAS_PRICE) - 1));
        assertFailsWith(
                INSUFFICIENT_PAYER_BALANCE,
                () -> subject.chargeForGas(
                        sender, relayer, wellKnownContextWith(blocks, tinybarValues), worldUpdater, transaction));
    }

    @Test
    void deductsGasCostIfUpfrontCostIsAfforded() {
        givenWellKnownIntrinsicGasCost();
        final var transaction = wellKnownHapiCall();
        given(sender.hederaId()).willReturn(SENDER_ID);
        given(sender.getBalance()).willReturn(Wei.of(transaction.upfrontCostGiven(NETWORK_GAS_PRICE)));
        final var chargingResult = subject.chargeForGas(
                sender, relayer, wellKnownContextWith(blocks, tinybarValues), worldUpdater, transaction);
        assertEquals(0, chargingResult.relayerAllowanceUsed());
        verify(worldUpdater).collectFee(SENDER_ID, transaction.gasCostGiven(NETWORK_GAS_PRICE));
    }

    @Test
    void requiresSufficientGasAllowanceIfUserOfferedPriceIsZero() {
        givenWellKnownIntrinsicGasCost();
        final var insufficientMaxAllowance = 123L;
        final var transaction = wellKnownRelayedHapiCallWithUserGasPriceAndMaxAllowance(0, insufficientMaxAllowance);
        assertFailsWith(
                INSUFFICIENT_TX_FEE,
                () -> subject.chargeForGas(
                        sender, relayer, wellKnownContextWith(blocks, tinybarValues), worldUpdater, transaction));
    }

    @Test
    void requiresRelayerToHaveSufficientBalanceIfUserOfferedPriceIsZero() {
        givenWellKnownIntrinsicGasCost();
        final var transaction = wellKnownRelayedHapiCallWithUserGasPriceAndMaxAllowance(0, Long.MAX_VALUE);
        given(relayer.getBalance()).willReturn(Wei.of(transaction.gasCostGiven(NETWORK_GAS_PRICE) - 1));
        assertFailsWith(
                INSUFFICIENT_PAYER_BALANCE,
                () -> subject.chargeForGas(
                        sender, relayer, wellKnownContextWith(blocks, tinybarValues), worldUpdater, transaction));
    }

    @Test
    void chargesRelayerOnlyIfUserOfferedPriceIsZero() {
        givenWellKnownIntrinsicGasCost();
        final var transaction = wellKnownRelayedHapiCallWithUserGasPriceAndMaxAllowance(0, Long.MAX_VALUE);
        final var gasCost = transaction.gasCostGiven(NETWORK_GAS_PRICE);
        given(relayer.getBalance()).willReturn(Wei.of(gasCost));
        given(relayer.hederaId()).willReturn(RELAYER_ID);
        final var chargingResult = subject.chargeForGas(
                sender, relayer, wellKnownContextWith(blocks, tinybarValues), worldUpdater, transaction);
        assertEquals(gasCost, chargingResult.relayerAllowanceUsed());
        verify(worldUpdater).collectFee(RELAYER_ID, gasCost);
    }

    @Test
    void chargesSenderOnlyIfUserOfferedPriceIsAtLeastNetworkPrice() {
        givenWellKnownIntrinsicGasCost();
        final var transaction = wellKnownRelayedHapiCallWithUserGasPriceAndMaxAllowance(NETWORK_GAS_PRICE, 0);
        final var gasCost = transaction.gasCostGiven(NETWORK_GAS_PRICE);
        given(sender.getBalance()).willReturn(Wei.of(gasCost));
        given(sender.hederaId()).willReturn(SENDER_ID);
        final var chargingResult = subject.chargeForGas(
                sender, relayer, wellKnownContextWith(blocks, tinybarValues), worldUpdater, transaction);
        assertEquals(0, chargingResult.relayerAllowanceUsed());
        verify(worldUpdater).collectFee(SENDER_ID, gasCost);
    }

    @Test
    void rejectsIfSenderCannotCoverOfferedGasCost() {
        givenWellKnownIntrinsicGasCost();
        final var transaction =
                wellKnownRelayedHapiCallWithUserGasPriceAndMaxAllowance(NETWORK_GAS_PRICE / 2, Long.MAX_VALUE);
        given(sender.getBalance()).willReturn(Wei.of(transaction.offeredGasCost() - 1));
        assertFailsWith(
                INSUFFICIENT_PAYER_BALANCE,
                () -> subject.chargeForGas(
                        sender, relayer, wellKnownContextWith(blocks, tinybarValues), worldUpdater, transaction));
    }

    @Test
    void rejectsIfRelayerCannotCoverRemainingGasCost() {
        givenWellKnownIntrinsicGasCost();
        final var transaction =
                wellKnownRelayedHapiCallWithUserGasPriceAndMaxAllowance(NETWORK_GAS_PRICE / 2, Long.MAX_VALUE);
        given(sender.getBalance()).willReturn(Wei.of(transaction.offeredGasCost()));
        given(relayer.getBalance()).willReturn(Wei.ZERO);
        assertFailsWith(
                INSUFFICIENT_PAYER_BALANCE,
                () -> subject.chargeForGas(
                        sender, relayer, wellKnownContextWith(blocks, tinybarValues), worldUpdater, transaction));
    }

    @Test
    void failsIfGasAllownaceLessThanRemainingGasCost() {
        givenWellKnownIntrinsicGasCost();
        final var transaction = wellKnownRelayedHapiCallWithUserGasPriceAndMaxAllowance(NETWORK_GAS_PRICE / 2, 0);
        assertFailsWith(
                INSUFFICIENT_TX_FEE,
                () -> subject.chargeForGas(
                        sender, relayer, wellKnownContextWith(blocks, tinybarValues), worldUpdater, transaction));
    }

    @Test
    void chargesSenderAndRelayerIfBothSolventAndWilling() {
        givenWellKnownIntrinsicGasCost();
        final var transaction =
                wellKnownRelayedHapiCallWithUserGasPriceAndMaxAllowance(NETWORK_GAS_PRICE / 2, Long.MAX_VALUE);
        final var gasCost = transaction.gasCostGiven(NETWORK_GAS_PRICE);
        final var relayerGasCost = gasCost - transaction.offeredGasCost();
        given(sender.getBalance()).willReturn(Wei.of(gasCost));
        given(sender.hederaId()).willReturn(SENDER_ID);
        given(relayer.getBalance()).willReturn(Wei.of(gasCost));
        given(relayer.hederaId()).willReturn(RELAYER_ID);
        final var chargingResult = subject.chargeForGas(
                sender, relayer, wellKnownContextWith(blocks, tinybarValues), worldUpdater, transaction);
        assertEquals(relayerGasCost, chargingResult.relayerAllowanceUsed());
        verify(worldUpdater).collectFee(SENDER_ID, transaction.offeredGasCost());
        verify(worldUpdater).collectFee(RELAYER_ID, relayerGasCost);
    }

    private void givenWellKnownIntrinsicGasCost() {
        givenWellKnownIntrinsicGasCost(false);
    }

    private void givenWellKnownIntrinsicGasCost(boolean isCreation) {
        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, isCreation))
                .willReturn(TestHelpers.INTRINSIC_GAS);
    }
}
