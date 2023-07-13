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

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.contract.impl.exec.TransactionProcessor;
import com.hedera.node.app.service.contract.impl.exec.failure.ResourceExhaustedException;
import com.hedera.node.app.service.contract.impl.exec.gas.CustomGasCharging;
import com.hedera.node.app.service.contract.impl.exec.processors.CustomMessageCallProcessor;
import com.hedera.node.app.service.contract.impl.exec.utils.FrameBuilder;
import com.hedera.node.app.service.contract.impl.exec.utils.FrameRunner;
import com.hedera.node.app.service.contract.impl.hevm.*;
import com.hedera.node.app.service.contract.impl.state.HederaEvmAccount;
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
    private HederaEvmCode code;

    @Mock
    private HederaEvmBlocks blocks;

    @Mock
    private HederaWorldUpdater worldUpdater;

    @Mock
    private HederaWorldUpdater feesOnlyUpdater;

    @Mock
    private HederaTracer tracer;

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
                MAX_GAS_ALLOWANCE);
        given(messageCallProcessor.isImplicitCreationEnabled(config)).willReturn(true);
        assertAbortsWith(invalidCreation, INVALID_CONTRACT_ID);
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

        final var context = wellKnownContextWith(code, blocks);
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
        given(frameRunner.runToCompletion(
                        transaction.gasLimit(), initialFrame, tracer, messageCallProcessor, contractCreationProcessor))
                .willReturn(SUCCESS_RESULT);
        given(initialFrame.getSelfDestructs()).willReturn(Set.of(NON_SYSTEM_LONG_ZERO_ADDRESS));

        final var result =
                subject.processTransaction(transaction, worldUpdater, () -> feesOnlyUpdater, context, tracer, config);

        inOrder.verify(worldUpdater).setupAliasedCreate(Address.ZERO, expectedToAddress);
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
                        transaction.gasLimit(), initialFrame, tracer, messageCallProcessor, contractCreationProcessor);
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

        final var context = wellKnownContextWith(code, blocks);
        final var transaction = wellKnownHapiCreate();

        given(gasCharging.chargeForGas(senderAccount, null, context, worldUpdater, transaction))
                .willReturn(NO_ALLOWANCE_CHARGING_RESULT);
        given(senderAccount.getAddress()).willReturn(EIP_1014_ADDRESS);
        given(worldUpdater.setupCreate(Address.ZERO)).willReturn(NON_SYSTEM_LONG_ZERO_ADDRESS);
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
                        transaction.gasLimit(), initialFrame, tracer, messageCallProcessor, contractCreationProcessor))
                .willReturn(SUCCESS_RESULT);
        given(initialFrame.getSelfDestructs()).willReturn(Set.of(NON_SYSTEM_LONG_ZERO_ADDRESS));
        given(worldUpdater.setupCreate(Address.ZERO)).willReturn(NON_SYSTEM_LONG_ZERO_ADDRESS);

        final var result =
                subject.processTransaction(transaction, worldUpdater, () -> feesOnlyUpdater, context, tracer, config);

        inOrder.verify(worldUpdater).setupCreate(Address.ZERO);
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
                        transaction.gasLimit(), initialFrame, tracer, messageCallProcessor, contractCreationProcessor);
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

        final var context = wellKnownContextWith(code, blocks);
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
        given(frameRunner.runToCompletion(
                        eq(transaction.gasLimit()), eq(initialFrame), eq(tracer), any(), eq(contractCreationProcessor)))
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
                        transaction.gasLimit(), initialFrame, tracer, messageCallProcessor, contractCreationProcessor);
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

        final var context = wellKnownContextWith(code, blocks);
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
        given(frameRunner.runToCompletion(
                        eq(transaction.gasLimit()), eq(initialFrame), eq(tracer), any(), eq(contractCreationProcessor)))
                .willReturn(SUCCESS_RESULT);
        given(initialFrame.getSelfDestructs()).willReturn(Set.of(NON_SYSTEM_LONG_ZERO_ADDRESS));
        willThrow(new ResourceExhaustedException(INSUFFICIENT_BALANCES_FOR_RENEWAL_FEES))
                .given(worldUpdater)
                .commit();

        final var result =
                subject.processTransaction(transaction, worldUpdater, () -> feesOnlyUpdater, context, tracer, config);

        // TODO - verify sender nonce is incremented and fees are re-charged with the fees-only updater

        assertResourceExhaustion(INSUFFICIENT_BALANCES_FOR_RENEWAL_FEES, result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void resourceExhaustionResultAsExpected() {
        givenSenderAccount();
        givenRelayerAccount();
        givenReceiverAccount();

        final var context = wellKnownContextWith(code, blocks);
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
        willThrow(new ResourceExhaustedException(MAX_CHILD_RECORDS_EXCEEDED))
                .given(frameRunner)
                .runToCompletion(
                        eq(transaction.gasLimit()), eq(initialFrame), eq(tracer), any(), eq(contractCreationProcessor));

        final var result =
                subject.processTransaction(transaction, worldUpdater, () -> feesOnlyUpdater, context, tracer, config);

        assertResourceExhaustion(MAX_CHILD_RECORDS_EXCEEDED, result);
    }

    private void assertResourceExhaustion(
            @NonNull final ResponseCodeEnum reason, @NonNull final HederaEvmTransactionResult result) {
        assertFalse(result.isSuccess());
        assertEquals(GAS_LIMIT, result.gasUsed());
        assertEquals(NETWORK_GAS_PRICE, result.gasPrice());
        assertNull(result.abortReason());
        assertNull(result.haltReason());
        assertEquals(Bytes.wrap(reason.name()), result.revertReason());
    }

    private void assertAbortsWith(@NonNull final ResponseCodeEnum reason) {
        assertAbortsWith(wellKnownHapiCall(), reason);
    }

    private void assertAbortsWith(
            @NonNull final HederaEvmTransaction transaction, @NonNull final ResponseCodeEnum reason) {
        final var result = subject.processTransaction(
                transaction, worldUpdater, () -> feesOnlyUpdater, wellKnownContextWith(code, blocks), tracer, config);
        assertEquals(reason, result.abortReason());
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
