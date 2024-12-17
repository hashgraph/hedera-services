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

package com.hedera.node.app.service.contract.impl.test.exec;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ETH_DATA_WITHOUT_TO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ETH_DATA_WITH_TO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.HEVM_CREATION;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.HEVM_Exception;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SUCCESS_RESULT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SUCCESS_RESULT_WITH_SIGNER_NONCE;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.assertFailsWith;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.processorsForAllCurrentEvmVersions;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.wellKnownRelayedHapiCallWithGasLimit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.CallOutcome;
import com.hedera.node.app.service.contract.impl.exec.ContextTransactionProcessor;
import com.hedera.node.app.service.contract.impl.exec.TransactionProcessor;
import com.hedera.node.app.service.contract.impl.exec.gas.CustomGasCharging;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.tracers.EvmActionTracer;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.hevm.HydratedEthTxData;
import com.hedera.node.app.service.contract.impl.infra.HevmTransactionFactory;
import com.hedera.node.app.service.contract.impl.state.HederaEvmAccount;
import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import com.hedera.node.app.spi.fees.ExchangeRateInfo;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContextTransactionProcessorTest {
    private static final Configuration CONFIGURATION = HederaTestConfigBuilder.create()
            .withValue("contracts.evm.ethTransaction.zeroHapiFees.enabled", true)
            .getOrCreateConfig();

    private static final Configuration CONFIG_NO_CHARGE_ON_EXCEPTION = HederaTestConfigBuilder.create()
            .withValue("contracts.evm.chargeGasOnEvmHandleException", false)
            .getOrCreateConfig();

    @Mock
    private HandleContext context;

    @Mock
    private HederaEvmContext hederaEvmContext;

    @Mock
    private EvmActionTracer tracer;

    @Mock
    private HevmTransactionFactory hevmTransactionFactory;

    @Mock
    private TransactionProcessor processor;

    @Mock
    private RootProxyWorldUpdater rootProxyWorldUpdater;

    @Mock
    private Supplier<HederaWorldUpdater> feesOnlyUpdater;

    @Mock
    private CustomGasCharging customGasCharging;

    @Mock
    private TransactionID transactionID;

    @Mock
    private TransactionBody transactionBody;

    @Mock
    private HederaEvmAccount senderAccount;

    @Mock
    private SystemContractGasCalculator systemContractGasCalculator;

    @Mock
    private ExchangeRateInfo exchangeRateInfo;

    @Test
    void callsComponentInfraAsExpectedForValidEthTx() {
        final var contractsConfig = CONFIGURATION.getConfigData(ContractsConfig.class);
        final var hydratedEthTxData = HydratedEthTxData.successFrom(ETH_DATA_WITH_TO_ADDRESS);
        final var subject = new ContextTransactionProcessor(
                hydratedEthTxData,
                context,
                contractsConfig,
                CONFIGURATION,
                hederaEvmContext,
                null,
                tracer,
                rootProxyWorldUpdater,
                hevmTransactionFactory,
                feesOnlyUpdater,
                processor,
                customGasCharging);

        givenSenderAccount();
        givenBodyWithTxnIdWillReturnHEVM();
        given(processor.processTransaction(
                        HEVM_CREATION, rootProxyWorldUpdater, feesOnlyUpdater, hederaEvmContext, tracer, CONFIGURATION))
                .willReturn(SUCCESS_RESULT_WITH_SIGNER_NONCE);

        final var protoResult =
                SUCCESS_RESULT_WITH_SIGNER_NONCE.asProtoResultOf(ETH_DATA_WITH_TO_ADDRESS, rootProxyWorldUpdater);
        final var expectedResult = new CallOutcome(
                protoResult,
                SUCCESS,
                HEVM_CREATION.contractId(),
                SUCCESS_RESULT_WITH_SIGNER_NONCE.gasPrice(),
                null,
                null);
        verify(rootProxyWorldUpdater, never()).collectFee(any(), anyLong());
        assertEquals(expectedResult, subject.call());
    }

    @Test
    void callsComponentInfraAsExpectedForValidEthTxWithoutTo() {
        final var contractsConfig = CONFIGURATION.getConfigData(ContractsConfig.class);
        final var hydratedEthTxData = HydratedEthTxData.successFrom(ETH_DATA_WITHOUT_TO_ADDRESS);
        final var subject = new ContextTransactionProcessor(
                hydratedEthTxData,
                context,
                contractsConfig,
                CONFIGURATION,
                hederaEvmContext,
                null,
                tracer,
                rootProxyWorldUpdater,
                hevmTransactionFactory,
                feesOnlyUpdater,
                processor,
                customGasCharging);

        givenSenderAccount();
        givenBodyWithTxnIdWillReturnHEVM();
        given(processor.processTransaction(
                        HEVM_CREATION, rootProxyWorldUpdater, feesOnlyUpdater, hederaEvmContext, tracer, CONFIGURATION))
                .willReturn(SUCCESS_RESULT_WITH_SIGNER_NONCE);

        final var protoResult =
                SUCCESS_RESULT_WITH_SIGNER_NONCE.asProtoResultOf(ETH_DATA_WITHOUT_TO_ADDRESS, rootProxyWorldUpdater);
        final var expectedResult = new CallOutcome(
                protoResult,
                SUCCESS,
                HEVM_CREATION.contractId(),
                SUCCESS_RESULT_WITH_SIGNER_NONCE.gasPrice(),
                null,
                null);
        assertEquals(expectedResult, subject.call());
        verify(rootProxyWorldUpdater, never()).collectFee(any(), anyLong());
    }

    @Test
    void callsComponentInfraAsExpectedForNonEthTx() {
        final var contractsConfig = CONFIGURATION.getConfigData(ContractsConfig.class);
        final var subject = new ContextTransactionProcessor(
                null,
                context,
                contractsConfig,
                CONFIGURATION,
                hederaEvmContext,
                null,
                tracer,
                rootProxyWorldUpdater,
                hevmTransactionFactory,
                feesOnlyUpdater,
                processor,
                customGasCharging);

        givenBodyWithTxnIdWillReturnHEVM();
        given(processor.processTransaction(
                        HEVM_CREATION, rootProxyWorldUpdater, feesOnlyUpdater, hederaEvmContext, tracer, CONFIGURATION))
                .willReturn(SUCCESS_RESULT);

        final var protoResult = SUCCESS_RESULT.asProtoResultOf(null, rootProxyWorldUpdater);
        final var expectedResult = new CallOutcome(
                protoResult, SUCCESS, HEVM_CREATION.contractId(), SUCCESS_RESULT.gasPrice(), null, null);
        assertEquals(expectedResult, subject.call());
        verify(rootProxyWorldUpdater, never()).collectFee(any(), anyLong());
    }

    @Test
    void stillChargesHapiFeesOnAbort() {
        final var contractsConfig = CONFIGURATION.getConfigData(ContractsConfig.class);
        final var processors = processorsForAllCurrentEvmVersions(processor);
        final var subject = new ContextTransactionProcessor(
                null,
                context,
                contractsConfig,
                CONFIGURATION,
                hederaEvmContext,
                null,
                tracer,
                rootProxyWorldUpdater,
                hevmTransactionFactory,
                feesOnlyUpdater,
                processor,
                customGasCharging);

        givenBodyWithTxnIdWillReturnHEVM();
        given(processor.processTransaction(
                        HEVM_CREATION, rootProxyWorldUpdater, feesOnlyUpdater, hederaEvmContext, tracer, CONFIGURATION))
                .willThrow(new HandleException(INVALID_CONTRACT_ID));

        subject.call();

        verify(customGasCharging).chargeGasForAbortedTransaction(any(), any(), any(), any());
        verify(rootProxyWorldUpdater).commit();
    }

    @Test
    void chargeHapiFeeOnFailedEthTransaction() {
        final var contractsConfig = CONFIGURATION.getConfigData(ContractsConfig.class);
        final var processors = processorsForAllCurrentEvmVersions(processor);
        final var subject = new ContextTransactionProcessor(
                null,
                context,
                contractsConfig,
                CONFIGURATION,
                hederaEvmContext,
                null,
                tracer,
                rootProxyWorldUpdater,
                hevmTransactionFactory,
                feesOnlyUpdater,
                processor,
                customGasCharging);

        given(context.body()).willReturn(TransactionBody.DEFAULT);
        given(context.payer()).willReturn(AccountID.DEFAULT);
        final var ethTx = wellKnownRelayedHapiCallWithGasLimit(1_000_000L);
        given(hevmTransactionFactory.fromHapiTransaction(TransactionBody.DEFAULT, context.payer()))
                .willReturn(ethTx);
        given(processor.processTransaction(
                        ethTx, rootProxyWorldUpdater, feesOnlyUpdater, hederaEvmContext, tracer, CONFIGURATION))
                .willThrow(new HandleException(INVALID_CONTRACT_ID));

        given(hederaEvmContext.systemContractGasCalculator()).willReturn(systemContractGasCalculator);
        given(systemContractGasCalculator.canonicalPriceInTinycents(any())).willReturn(1000L);
        given(context.exchangeRateInfo()).willReturn(exchangeRateInfo);
        final ExchangeRate exchangeRate = new ExchangeRate(1, 2, TimestampSeconds.DEFAULT);
        given(exchangeRateInfo.activeRate(any())).willReturn(exchangeRate);

        subject.call();
        verify(rootProxyWorldUpdater).commit();
        verify(rootProxyWorldUpdater).collectFee(any(), anyLong());
    }

    @Test
    void stillChargesGasFeesOnHevmException() {
        final var contractsConfig = CONFIGURATION.getConfigData(ContractsConfig.class);
        final var subject = new ContextTransactionProcessor(
                null,
                context,
                contractsConfig,
                CONFIGURATION,
                hederaEvmContext,
                null,
                tracer,
                rootProxyWorldUpdater,
                hevmTransactionFactory,
                feesOnlyUpdater,
                processor,
                customGasCharging);

        given(context.body()).willReturn(transactionBody);
        final var payer = AccountID.DEFAULT;
        given(context.payer()).willReturn(payer);
        given(hevmTransactionFactory.fromHapiTransaction(transactionBody, payer))
                .willReturn(HEVM_Exception);
        given(transactionBody.transactionIDOrThrow()).willReturn(transactionID);
        given(transactionID.accountIDOrThrow()).willReturn(SENDER_ID);

        final var outcome = subject.call();

        verify(customGasCharging).chargeGasForAbortedTransaction(any(), any(), any(), any());
        verify(rootProxyWorldUpdater).commit();
        assertEquals(INVALID_CONTRACT_ID, outcome.status());
    }

    @Test
    void doesNotChargeGasFeesOnHevmExceptionIfSoConfigured() {
        final var contractsConfig = CONFIG_NO_CHARGE_ON_EXCEPTION.getConfigData(ContractsConfig.class);
        final var subject = new ContextTransactionProcessor(
                null,
                context,
                contractsConfig,
                CONFIG_NO_CHARGE_ON_EXCEPTION,
                hederaEvmContext,
                null,
                tracer,
                rootProxyWorldUpdater,
                hevmTransactionFactory,
                feesOnlyUpdater,
                processor,
                customGasCharging);

        given(context.body()).willReturn(transactionBody);
        final var payer = AccountID.DEFAULT;
        given(context.payer()).willReturn(payer);
        given(hevmTransactionFactory.fromHapiTransaction(transactionBody, payer))
                .willReturn(HEVM_Exception);
        given(transactionBody.transactionIDOrThrow()).willReturn(transactionID);
        given(transactionID.accountIDOrThrow()).willReturn(SENDER_ID);

        final var outcome = subject.call();

        verify(customGasCharging, never()).chargeGasForAbortedTransaction(any(), any(), any(), any());
        verify(rootProxyWorldUpdater).commit();
        assertEquals(INVALID_CONTRACT_ID, outcome.status());
    }

    @Test
    void stillChargesGasFeesOnExceptionThrown() {
        final var contractsConfig = CONFIGURATION.getConfigData(ContractsConfig.class);
        final var subject = new ContextTransactionProcessor(
                null,
                context,
                contractsConfig,
                CONFIGURATION,
                hederaEvmContext,
                null,
                tracer,
                rootProxyWorldUpdater,
                hevmTransactionFactory,
                feesOnlyUpdater,
                processor,
                customGasCharging);

        given(context.body()).willReturn(transactionBody);
        final var payer = AccountID.DEFAULT;
        given(context.payer()).willReturn(payer);
        given(hevmTransactionFactory.fromHapiTransaction(transactionBody, payer))
                .willThrow(new HandleException(INVALID_CONTRACT_ID));
        given(hevmTransactionFactory.fromContractTxException(any(), any())).willReturn(HEVM_Exception);
        given(transactionBody.transactionIDOrThrow()).willReturn(transactionID);
        given(transactionID.accountIDOrThrow()).willReturn(SENDER_ID);

        final var outcome = subject.call();

        verify(customGasCharging).chargeGasForAbortedTransaction(any(), any(), any(), any());
        // Verify that disabled zeroHapi fees flag won't charge on error
        verify(rootProxyWorldUpdater, never()).collectFee(any(), anyLong());
        verify(rootProxyWorldUpdater).commit();
        assertEquals(INVALID_CONTRACT_ID, outcome.status());
    }

    @Test
    void doesNotChargeGasAndHapiFeesOnExceptionThrownIfSoConfigured() {
        final var contractsConfig = CONFIG_NO_CHARGE_ON_EXCEPTION.getConfigData(ContractsConfig.class);
        final var subject = new ContextTransactionProcessor(
                null,
                context,
                contractsConfig,
                CONFIG_NO_CHARGE_ON_EXCEPTION,
                hederaEvmContext,
                null,
                tracer,
                rootProxyWorldUpdater,
                hevmTransactionFactory,
                feesOnlyUpdater,
                processor,
                customGasCharging);

        given(context.body()).willReturn(transactionBody);
        final var payer = AccountID.DEFAULT;
        given(context.payer()).willReturn(payer);
        given(hevmTransactionFactory.fromHapiTransaction(transactionBody, payer))
                .willThrow(new HandleException(INVALID_CONTRACT_ID));
        given(hevmTransactionFactory.fromContractTxException(any(), any())).willReturn(HEVM_Exception);
        given(transactionBody.transactionIDOrThrow()).willReturn(transactionID);
        given(transactionID.accountIDOrThrow()).willReturn(SENDER_ID);

        final var outcome = subject.call();

        verify(customGasCharging, never()).chargeGasForAbortedTransaction(any(), any(), any(), any());
        // Verify that disabled zeroHapi fees flag won't charge on error
        verify(rootProxyWorldUpdater, never()).collectFee(any(), anyLong());
        verify(rootProxyWorldUpdater).commit();
        assertEquals(INVALID_CONTRACT_ID, outcome.status());
    }

    @Test
    void reThrowsExceptionWhenNotContractCall() {
        final var contractsConfig = CONFIGURATION.getConfigData(ContractsConfig.class);
        final var subject = new ContextTransactionProcessor(
                null,
                context,
                contractsConfig,
                CONFIGURATION,
                hederaEvmContext,
                null,
                tracer,
                rootProxyWorldUpdater,
                hevmTransactionFactory,
                feesOnlyUpdater,
                processor,
                customGasCharging);

        given(context.body()).willReturn(transactionBody);
        given(context.payer()).willReturn(SENDER_ID);
        given(transactionBody.transactionIDOrThrow()).willReturn(transactionID);
        given(transactionID.accountIDOrThrow()).willReturn(SENDER_ID);
        given(hevmTransactionFactory.fromHapiTransaction(transactionBody, SENDER_ID))
                .willThrow(new HandleException(INVALID_CONTRACT_ID));
        given(hevmTransactionFactory.fromContractTxException(any(), any())).willReturn(HEVM_Exception);

        final var outcome = subject.call();
        verify(rootProxyWorldUpdater).commit();
        assertEquals(INVALID_CONTRACT_ID, outcome.status());
    }

    @Test
    void failsImmediatelyIfEthTxInvalid() {
        final var contractsConfig = CONFIGURATION.getConfigData(ContractsConfig.class);
        final var subject = new ContextTransactionProcessor(
                HydratedEthTxData.failureFrom(INVALID_ETHEREUM_TRANSACTION),
                context,
                contractsConfig,
                CONFIGURATION,
                hederaEvmContext,
                null,
                tracer,
                rootProxyWorldUpdater,
                hevmTransactionFactory,
                feesOnlyUpdater,
                processor,
                customGasCharging);

        assertFailsWith(INVALID_ETHEREUM_TRANSACTION, subject::call);
    }

    void givenSenderAccount() {
        given(rootProxyWorldUpdater.getHederaAccount(SENDER_ID)).willReturn(senderAccount);
        given(senderAccount.getNonce()).willReturn(1L);
    }

    void givenBodyWithTxnIdWillReturnHEVM() {
        final var body = TransactionBody.newBuilder()
                .transactionID(TransactionID.DEFAULT)
                .build();
        final var payer = AccountID.DEFAULT;
        given(context.body()).willReturn(body);
        given(context.payer()).willReturn(payer);
        given(hevmTransactionFactory.fromHapiTransaction(body, payer)).willReturn(HEVM_CREATION);
    }
}
