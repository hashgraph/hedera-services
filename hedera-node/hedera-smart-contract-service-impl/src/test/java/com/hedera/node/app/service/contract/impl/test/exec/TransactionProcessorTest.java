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

package com.hedera.node.app.service.contract.impl.test.exec;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_BALANCES_FOR_RENEWAL_FEES;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CALLED_CONTRACT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CALL_DATA;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CHARGING_RESULT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.GAS_LIMIT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.INVALID_CONTRACT_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.MAINNET_CHAIN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.MAX_GAS_ALLOWANCE;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NETWORK_GAS_PRICE;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NONCE;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_SYSTEM_LONG_ZERO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NO_ALLOWANCE_CHARGING_RESULT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.RELAYER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SUCCESS_RESULT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.USER_OFFERED_GAS_PRICE;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.VALID_CONTRACT_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.VALUE;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.assertFailsWith;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.wellKnownContextWith;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.wellKnownHapiCall;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.wellKnownHapiCreate;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.wellKnownRelayedHapiCall;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.wellKnownRelayedHapiCreate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.node.app.service.contract.impl.exec.FrameRunner;
import com.hedera.node.app.service.contract.impl.exec.TransactionProcessor;
import com.hedera.node.app.service.contract.impl.exec.gas.CustomGasCharging;
import com.hedera.node.app.service.contract.impl.exec.gas.TinybarValues;
import com.hedera.node.app.service.contract.impl.exec.processors.CustomMessageCallProcessor;
import com.hedera.node.app.service.contract.impl.exec.utils.FrameBuilder;
import com.hedera.node.app.service.contract.impl.hevm.ActionSidecarContentTracer;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmBlocks;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransaction;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransactionResult;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.HederaEvmAccount;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.node.app.spi.workflows.ResourceExhaustedException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionProcessorTest {
    @Mock
    private MessageFrame initialFrame;

    @Mock
    private FrameBuilder frameBuilder;

    @Mock
    private FrameRunner frameRunner;

    @Mock
    private CustomMessageCallProcessor messageCallProcessor;

    @Mock
    private ContractCreationProcessor contractCreationProcessor;

    @Mock
    private HederaEvmBlocks blocks;

    @Mock
    private TinybarValues tinybarValues;

    @Mock
    private HederaWorldUpdater worldUpdater;

    @Mock
    private HederaWorldUpdater feesOnlyUpdater;

    @Mock
    private ActionSidecarContentTracer tracer;

    @Mock
    private Configuration config;

    @Mock
    private HederaEvmAccount senderAccount;

    @Mock
    private HederaEvmAccount relayerAccount;

    @Mock
    private HederaEvmAccount receiverAccount;

    @Mock
    private CustomGasCharging gasCharging;

    private TransactionProcessor subject;

    @BeforeEach
    void setUp() {
        subject = new TransactionProcessor(
                frameBuilder, frameRunner, gasCharging, messageCallProcessor, contractCreationProcessor);
    }

    @Test
    void abortsOnMissingSender() {
        assertAbortsWith(INVALID_ACCOUNT_ID);
    }

    @Test
    void lazyCreationAttemptWithNoValueFailsFast() {
        givenSenderAccount();
        givenRelayerAccount();
        given(messageCallProcessor.isImplicitCreationEnabled(config)).willReturn(true);
        assertAbortsWith(wellKnownRelayedHapiCall(0), INVALID_CONTRACT_ID);
    }

    @Test
    void lazyCreationAttemptWithInvalidAddress() {
        givenSenderAccount();
        givenRelayerAccount();
        final var invalidCreation = new HederaEvmTransaction(
                SENDER_ID,
                RELAYER_ID,
                INVALID_CONTRACT_ADDRESS,
                NONCE,
                CALL_DATA,
                MAINNET_CHAIN_ID,
                VALUE,
                GAS_LIMIT,
                USER_OFFERED_GAS_PRICE,
                MAX_GAS_ALLOWANCE,
                null);
        given(messageCallProcessor.isImplicitCreationEnabled(config)).willReturn(true);
        assertAbortsWith(invalidCreation, INVALID_CONTRACT_ID);
    }

    @Test
    void lazyCreationAttemptWithValidAddress() {
        givenSenderAccount();
        givenRelayerAccount();
        final var transaction = new HederaEvmTransaction(
                SENDER_ID,
                RELAYER_ID,
                VALID_CONTRACT_ADDRESS,
                NONCE,
                CALL_DATA,
                MAINNET_CHAIN_ID,
                VALUE,
                GAS_LIMIT,
                USER_OFFERED_GAS_PRICE,
                MAX_GAS_ALLOWANCE,
                null);
        given(messageCallProcessor.isImplicitCreationEnabled(config)).willReturn(true);
        final var context = wellKnownContextWith(blocks, tinybarValues);
        given(gasCharging.chargeForGas(senderAccount, relayerAccount, context, worldUpdater, transaction))
                .willReturn(CHARGING_RESULT);
        given(senderAccount.getAddress()).willReturn(EIP_1014_ADDRESS);
        final var expectedToAddress = ConversionUtils.pbjToBesuAddress(VALID_CONTRACT_ADDRESS.evmAddressOrThrow());
        given(frameBuilder.buildInitialFrameWith(
                        transaction,
                        worldUpdater,
                        context,
                        config,
                        EIP_1014_ADDRESS,
                        expectedToAddress,
                        CHARGING_RESULT.intrinsicGas()))
                .willReturn(initialFrame);
        given(senderAccount.hederaId()).willReturn(SENDER_ID);
        given(frameRunner.runToCompletion(
                        transaction.gasLimit(),
                        SENDER_ID,
                        initialFrame,
                        tracer,
                        messageCallProcessor,
                        contractCreationProcessor))
                .willReturn(SUCCESS_RESULT);

        final var result =
                subject.processTransaction(transaction, worldUpdater, () -> feesOnlyUpdater, context, tracer, config);

        assertSame(SUCCESS_RESULT, result);
        verify(worldUpdater).setupTopLevelLazyCreate(expectedToAddress);
    }

    @Test
    void requiresEthTxToHaveNonNullRelayer() {
        givenSenderAccount();
        assertAbortsWith(wellKnownRelayedHapiCall(0), INVALID_ACCOUNT_ID);
    }

    @Test
    void nonLazyCreateCandidateMustHaveReceiver() {
        givenSenderAccount();
        givenRelayerAccount();
        assertAbortsWith(wellKnownRelayedHapiCall(0), INVALID_CONTRACT_ID);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ethCreateHappyPathAsExpected() {
        final var inOrder = inOrder(worldUpdater, frameBuilder, frameRunner, gasCharging, senderAccount);

        givenSenderAccount();
        givenRelayerAccount();

        final var context = wellKnownContextWith(blocks, tinybarValues);
        final var transaction = wellKnownRelayedHapiCreate();

        given(gasCharging.chargeForGas(senderAccount, relayerAccount, context, worldUpdater, transaction))
                .willReturn(CHARGING_RESULT);
        given(senderAccount.getAddress()).willReturn(EIP_1014_ADDRESS);
        given(senderAccount.getNonce()).willReturn(NONCE);
        final var expectedToAddress = Address.contractAddress(EIP_1014_ADDRESS, NONCE);
        given(frameBuilder.buildInitialFrameWith(
                        transaction,
                        worldUpdater,
                        context,
                        config,
                        EIP_1014_ADDRESS,
                        expectedToAddress,
                        CHARGING_RESULT.intrinsicGas()))
                .willReturn(initialFrame);
        given(senderAccount.hederaId()).willReturn(SENDER_ID);
        given(frameRunner.runToCompletion(
                        transaction.gasLimit(),
                        SENDER_ID,
                        initialFrame,
                        tracer,
                        messageCallProcessor,
                        contractCreationProcessor))
                .willReturn(SUCCESS_RESULT);
        given(initialFrame.getSelfDestructs()).willReturn(Set.of(NON_SYSTEM_LONG_ZERO_ADDRESS));

        final var result =
                subject.processTransaction(transaction, worldUpdater, () -> feesOnlyUpdater, context, tracer, config);

        inOrder.verify(worldUpdater)
                .setupAliasedTopLevelCreate(ContractCreateTransactionBody.DEFAULT, expectedToAddress);
        inOrder.verify(senderAccount).incrementNonce();
        inOrder.verify(gasCharging).chargeForGas(senderAccount, relayerAccount, context, worldUpdater, transaction);
        inOrder.verify(frameBuilder)
                .buildInitialFrameWith(
                        transaction,
                        worldUpdater,
                        context,
                        config,
                        EIP_1014_ADDRESS,
                        expectedToAddress,
                        CHARGING_RESULT.intrinsicGas());
        inOrder.verify(frameRunner)
                .runToCompletion(
                        transaction.gasLimit(),
                        SENDER_ID,
                        initialFrame,
                        tracer,
                        messageCallProcessor,
                        contractCreationProcessor);
        inOrder.verify(gasCharging)
                .maybeRefundGiven(
                        GAS_LIMIT - SUCCESS_RESULT.gasUsed(),
                        CHARGING_RESULT.relayerAllowanceUsed(),
                        senderAccount,
                        relayerAccount,
                        context,
                        worldUpdater);
        inOrder.verify(worldUpdater).deleteAccount(NON_SYSTEM_LONG_ZERO_ADDRESS);
        inOrder.verify(worldUpdater).commit();
        assertSame(SUCCESS_RESULT, result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void hapiCreateHappyPathAsExpected() {
        final var inOrder = inOrder(worldUpdater, frameBuilder, frameRunner, gasCharging, senderAccount);

        givenSenderAccount();

        final var context = wellKnownContextWith(blocks, tinybarValues);
        final var transaction = wellKnownHapiCreate();

        given(gasCharging.chargeForGas(senderAccount, null, context, worldUpdater, transaction))
                .willReturn(NO_ALLOWANCE_CHARGING_RESULT);
        given(senderAccount.getAddress()).willReturn(EIP_1014_ADDRESS);
        given(worldUpdater.setupTopLevelCreate(ContractCreateTransactionBody.DEFAULT))
                .willReturn(NON_SYSTEM_LONG_ZERO_ADDRESS);
        given(frameBuilder.buildInitialFrameWith(
                        transaction,
                        worldUpdater,
                        context,
                        config,
                        EIP_1014_ADDRESS,
                        NON_SYSTEM_LONG_ZERO_ADDRESS,
                        CHARGING_RESULT.intrinsicGas()))
                .willReturn(initialFrame);
        given(senderAccount.hederaId()).willReturn(SENDER_ID);
        given(frameRunner.runToCompletion(
                        transaction.gasLimit(),
                        SENDER_ID,
                        initialFrame,
                        tracer,
                        messageCallProcessor,
                        contractCreationProcessor))
                .willReturn(SUCCESS_RESULT);
        given(initialFrame.getSelfDestructs()).willReturn(Set.of(NON_SYSTEM_LONG_ZERO_ADDRESS));

        final var result =
                subject.processTransaction(transaction, worldUpdater, () -> feesOnlyUpdater, context, tracer, config);

        inOrder.verify(worldUpdater).setupTopLevelCreate(ContractCreateTransactionBody.DEFAULT);
        inOrder.verify(gasCharging).chargeForGas(senderAccount, null, context, worldUpdater, transaction);
        inOrder.verify(frameBuilder)
                .buildInitialFrameWith(
                        transaction,
                        worldUpdater,
                        context,
                        config,
                        EIP_1014_ADDRESS,
                        NON_SYSTEM_LONG_ZERO_ADDRESS,
                        CHARGING_RESULT.intrinsicGas());
        inOrder.verify(frameRunner)
                .runToCompletion(
                        transaction.gasLimit(),
                        SENDER_ID,
                        initialFrame,
                        tracer,
                        messageCallProcessor,
                        contractCreationProcessor);
        inOrder.verify(gasCharging)
                .maybeRefundGiven(GAS_LIMIT - SUCCESS_RESULT.gasUsed(), 0, senderAccount, null, context, worldUpdater);
        inOrder.verify(worldUpdater).deleteAccount(NON_SYSTEM_LONG_ZERO_ADDRESS);
        inOrder.verify(worldUpdater).commit();
        assertSame(SUCCESS_RESULT, result);
        verify(senderAccount, never()).incrementNonce();
    }

    @Test
    @SuppressWarnings("unchecked")
    void ethCallHappyPathAsExpected() {
        final var inOrder =
                inOrder(worldUpdater, frameBuilder, frameRunner, gasCharging, messageCallProcessor, senderAccount);

        givenSenderAccount();
        givenRelayerAccount();
        givenReceiverAccount();

        final var context = wellKnownContextWith(blocks, tinybarValues);
        final var transaction = wellKnownRelayedHapiCall(0);

        given(gasCharging.chargeForGas(senderAccount, relayerAccount, context, worldUpdater, transaction))
                .willReturn(CHARGING_RESULT);
        given(senderAccount.getAddress()).willReturn(EIP_1014_ADDRESS);
        given(receiverAccount.getAddress()).willReturn(NON_SYSTEM_LONG_ZERO_ADDRESS);
        given(frameBuilder.buildInitialFrameWith(
                        transaction,
                        worldUpdater,
                        context,
                        config,
                        EIP_1014_ADDRESS,
                        NON_SYSTEM_LONG_ZERO_ADDRESS,
                        CHARGING_RESULT.intrinsicGas()))
                .willReturn(initialFrame);
        given(senderAccount.hederaId()).willReturn(SENDER_ID);
        given(frameRunner.runToCompletion(
                        eq(transaction.gasLimit()),
                        eq(SENDER_ID),
                        eq(initialFrame),
                        eq(tracer),
                        any(),
                        eq(contractCreationProcessor)))
                .willReturn(SUCCESS_RESULT);
        given(initialFrame.getSelfDestructs()).willReturn(Set.of(NON_SYSTEM_LONG_ZERO_ADDRESS));

        final var result =
                subject.processTransaction(transaction, worldUpdater, () -> feesOnlyUpdater, context, tracer, config);

        inOrder.verify(senderAccount).incrementNonce();
        inOrder.verify(gasCharging).chargeForGas(senderAccount, relayerAccount, context, worldUpdater, transaction);
        inOrder.verify(frameBuilder)
                .buildInitialFrameWith(
                        transaction,
                        worldUpdater,
                        context,
                        config,
                        EIP_1014_ADDRESS,
                        NON_SYSTEM_LONG_ZERO_ADDRESS,
                        CHARGING_RESULT.intrinsicGas());
        inOrder.verify(frameRunner)
                .runToCompletion(
                        transaction.gasLimit(),
                        SENDER_ID,
                        initialFrame,
                        tracer,
                        messageCallProcessor,
                        contractCreationProcessor);
        inOrder.verify(gasCharging)
                .maybeRefundGiven(
                        GAS_LIMIT - SUCCESS_RESULT.gasUsed(),
                        CHARGING_RESULT.relayerAllowanceUsed(),
                        senderAccount,
                        relayerAccount,
                        context,
                        worldUpdater);
        inOrder.verify(worldUpdater).deleteAccount(NON_SYSTEM_LONG_ZERO_ADDRESS);
        inOrder.verify(worldUpdater).commit();
        assertSame(SUCCESS_RESULT, result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ethCallAsExpectedWithResourceExhaustionInCommit() {
        givenSenderAccount();
        givenRelayerAccount();
        givenReceiverAccount();
        givenFeeOnlyParties();

        final var context = wellKnownContextWith(blocks, tinybarValues);
        final var transaction = wellKnownRelayedHapiCall(0);

        given(gasCharging.chargeForGas(senderAccount, relayerAccount, context, worldUpdater, transaction))
                .willReturn(CHARGING_RESULT);
        given(senderAccount.getAddress()).willReturn(EIP_1014_ADDRESS);
        given(senderAccount.hederaId()).willReturn(SENDER_ID);
        given(receiverAccount.getAddress()).willReturn(NON_SYSTEM_LONG_ZERO_ADDRESS);
        given(frameBuilder.buildInitialFrameWith(
                        transaction,
                        worldUpdater,
                        context,
                        config,
                        EIP_1014_ADDRESS,
                        NON_SYSTEM_LONG_ZERO_ADDRESS,
                        CHARGING_RESULT.intrinsicGas()))
                .willReturn(initialFrame);
        given(frameRunner.runToCompletion(
                        eq(transaction.gasLimit()),
                        eq(SENDER_ID),
                        eq(initialFrame),
                        eq(tracer),
                        any(),
                        eq(contractCreationProcessor)))
                .willReturn(SUCCESS_RESULT);
        given(initialFrame.getSelfDestructs()).willReturn(Set.of(NON_SYSTEM_LONG_ZERO_ADDRESS));
        willThrow(new ResourceExhaustedException(INSUFFICIENT_BALANCES_FOR_RENEWAL_FEES))
                .given(worldUpdater)
                .commit();

        final var result =
                subject.processTransaction(transaction, worldUpdater, () -> feesOnlyUpdater, context, tracer, config);

        assertResourceExhaustion(INSUFFICIENT_BALANCES_FOR_RENEWAL_FEES, result);
        verify(gasCharging).chargeForGas(senderAccount, relayerAccount, context, feesOnlyUpdater, transaction);
        verify(worldUpdater).revert();
        verify(feesOnlyUpdater).commit();
    }

    @Test
    @SuppressWarnings("unchecked")
    void resourceExhaustionResultAsExpected() {
        givenSenderAccount();
        givenRelayerAccount();
        givenReceiverAccount();
        givenFeeOnlyParties();

        final var context = wellKnownContextWith(blocks, tinybarValues);
        final var transaction = wellKnownRelayedHapiCall(0);

        given(gasCharging.chargeForGas(senderAccount, relayerAccount, context, worldUpdater, transaction))
                .willReturn(CHARGING_RESULT);
        given(senderAccount.getAddress()).willReturn(EIP_1014_ADDRESS);
        given(receiverAccount.getAddress()).willReturn(NON_SYSTEM_LONG_ZERO_ADDRESS);
        given(frameBuilder.buildInitialFrameWith(
                        transaction,
                        worldUpdater,
                        context,
                        config,
                        EIP_1014_ADDRESS,
                        NON_SYSTEM_LONG_ZERO_ADDRESS,
                        CHARGING_RESULT.intrinsicGas()))
                .willReturn(initialFrame);
        given(senderAccount.hederaId()).willReturn(SENDER_ID);
        willThrow(new ResourceExhaustedException(MAX_CHILD_RECORDS_EXCEEDED))
                .given(frameRunner)
                .runToCompletion(
                        eq(transaction.gasLimit()),
                        eq(SENDER_ID),
                        eq(initialFrame),
                        eq(tracer),
                        any(),
                        eq(contractCreationProcessor));

        final var result =
                subject.processTransaction(transaction, worldUpdater, () -> feesOnlyUpdater, context, tracer, config);

        assertResourceExhaustion(MAX_CHILD_RECORDS_EXCEEDED, result);
    }

    private void assertResourceExhaustion(
            @NonNull final ResponseCodeEnum reason, @NonNull final HederaEvmTransactionResult result) {
        assertFalse(result.isSuccess());
        assertEquals(GAS_LIMIT, result.gasUsed());
        assertEquals(NETWORK_GAS_PRICE, result.gasPrice());
        assertNull(result.haltReason());
        assertEquals(Bytes.wrap(reason.name()), result.revertReason());
    }

    private void assertAbortsWith(@NonNull final ResponseCodeEnum reason) {
        assertAbortsWith(wellKnownHapiCall(), reason);
    }

    private void assertAbortsWith(
            @NonNull final HederaEvmTransaction transaction, @NonNull final ResponseCodeEnum reason) {
        assertFailsWith(
                reason,
                () -> subject.processTransaction(
                        transaction,
                        worldUpdater,
                        () -> feesOnlyUpdater,
                        wellKnownContextWith(blocks, tinybarValues),
                        tracer,
                        config));
    }

    private void givenFeeOnlyParties() {
        given(feesOnlyUpdater.getHederaAccount(SENDER_ID)).willReturn(senderAccount);
        given(feesOnlyUpdater.getHederaAccount(RELAYER_ID)).willReturn(relayerAccount);
        given(feesOnlyUpdater.getHederaAccount(CALLED_CONTRACT_ID)).willReturn(receiverAccount);
    }

    private void givenSenderAccount() {
        given(worldUpdater.getHederaAccount(SENDER_ID)).willReturn(senderAccount);
    }

    private void givenRelayerAccount() {
        given(worldUpdater.getHederaAccount(RELAYER_ID)).willReturn(relayerAccount);
    }

    private void givenReceiverAccount() {
        given(worldUpdater.getHederaAccount(CALLED_CONTRACT_ID)).willReturn(receiverAccount);
    }
}
