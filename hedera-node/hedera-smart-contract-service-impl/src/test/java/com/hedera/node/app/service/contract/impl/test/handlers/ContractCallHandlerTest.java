/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CALLED_CONTRACT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.HALT_RESULT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SUCCESS_RESULT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.assertFailsWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.ContractServiceComponent;
import com.hedera.node.app.service.contract.impl.exec.CallOutcome;
import com.hedera.node.app.service.contract.impl.exec.ContextTransactionProcessor;
import com.hedera.node.app.service.contract.impl.exec.TransactionComponent;
import com.hedera.node.app.service.contract.impl.exec.metrics.ContractMetrics;
import com.hedera.node.app.service.contract.impl.exec.utils.SystemContractMethodRegistry;
import com.hedera.node.app.service.contract.impl.handlers.ContractCallHandler;
import com.hedera.node.app.service.contract.impl.records.ContractCallStreamBuilder;
import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fees.FeeCalculatorFactory;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.metrics.api.Metrics;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractCallHandlerTest extends ContractHandlerTestBase {
    public static final long INTRINSIC_GAS_FOR_0_ARG_METHOD = 21064L;

    @Mock
    private TransactionComponent component;

    @Mock
    private HandleContext handleContext;

    @Mock
    private TransactionComponent.Factory factory;

    @Mock
    private ContextTransactionProcessor processor;

    @Mock
    private ContractCallStreamBuilder recordBuilder;

    @Mock
    private HandleContext.SavepointStack stack;

    @Mock
    private RootProxyWorldUpdater baseProxyWorldUpdater;

    @Mock
    private GasCalculator gasCalculator;

    @Mock(strictness = Strictness.LENIENT)
    private ContractServiceComponent contractServiceComponent;

    @Mock
    private ContractsConfig contractsConfig;

    private final SystemContractMethodRegistry systemContractMethodRegistry = new SystemContractMethodRegistry();

    private final Metrics metrics = new NoOpMetrics();
    private final ContractMetrics contractMetrics =
            new ContractMetrics(metrics, () -> contractsConfig, systemContractMethodRegistry);

    private ContractCallHandler subject;

    @BeforeEach
    void setUp() {
        contractMetrics.createContractPrimaryMetrics();
        given(contractServiceComponent.contractMetrics()).willReturn(contractMetrics);
        subject = new ContractCallHandler(() -> factory, gasCalculator, contractServiceComponent);
    }

    @Test
    void delegatesToCreatedComponentAndExposesSuccess() {
        given(factory.create(handleContext, HederaFunctionality.CONTRACT_CALL)).willReturn(component);
        given(component.contextTransactionProcessor()).willReturn(processor);
        given(handleContext.savepointStack()).willReturn(stack);
        given(stack.getBaseBuilder(ContractCallStreamBuilder.class)).willReturn(recordBuilder);
        final var expectedResult = SUCCESS_RESULT.asProtoResultOf(baseProxyWorldUpdater);
        final var expectedOutcome = new CallOutcome(
                expectedResult,
                SUCCESS_RESULT.finalStatus(),
                CALLED_CONTRACT_ID,
                SUCCESS_RESULT.gasPrice(),
                null,
                null);
        given(processor.call()).willReturn(expectedOutcome);

        given(recordBuilder.contractID(CALLED_CONTRACT_ID)).willReturn(recordBuilder);
        given(recordBuilder.contractCallResult(expectedResult)).willReturn(recordBuilder);
        given(recordBuilder.withCommonFieldsSetFrom(expectedOutcome)).willReturn(recordBuilder);

        assertDoesNotThrow(() -> subject.handle(handleContext));
    }

    @Test
    void delegatesToCreatedComponentAndThrowsOnFailure() {
        given(factory.create(handleContext, HederaFunctionality.CONTRACT_CALL)).willReturn(component);
        given(component.contextTransactionProcessor()).willReturn(processor);
        given(handleContext.savepointStack()).willReturn(stack);
        given(stack.getBaseBuilder(ContractCallStreamBuilder.class)).willReturn(recordBuilder);
        final var expectedResult = HALT_RESULT.asProtoResultOf(baseProxyWorldUpdater);
        final var expectedOutcome =
                new CallOutcome(expectedResult, HALT_RESULT.finalStatus(), null, HALT_RESULT.gasPrice(), null, null);
        given(processor.call()).willReturn(expectedOutcome);

        given(recordBuilder.contractID(null)).willReturn(recordBuilder);
        given(recordBuilder.contractCallResult(expectedResult)).willReturn(recordBuilder);
        given(recordBuilder.withCommonFieldsSetFrom(expectedOutcome)).willReturn(recordBuilder);

        assertFailsWith(INVALID_SIGNATURE, () -> subject.handle(handleContext));
    }

    @Test
    @DisplayName("Succeeds for valid payer account")
    void validPayer() throws PreCheckException {
        final var txn = contractCallTransaction();
        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);
        basicMetaAssertions(context, 0);
        assertThat(context.payerKey()).isEqualTo(payerKey);
    }

    @Test
    void validatePureChecks() {
        // check null contact id
        final var txn1 = contractCallTransactionWithNoContractId();
        assertThrows(PreCheckException.class, () -> subject.pureChecks(txn1));

        // check at least intrinsic gas
        final var txn2 = contractCallTransactionWithInsufficientGas();
        given(gasCalculator.transactionIntrinsicGasCost(org.apache.tuweni.bytes.Bytes.wrap(new byte[0]), false))
                .willReturn(INTRINSIC_GAS_FOR_0_ARG_METHOD);
        assertThrows(PreCheckException.class, () -> subject.pureChecks(txn2));

        // check that invalid contract id is rejected
        final var txn3 = contractCallTransactionWithInvalidContractId();
        assertThrows(PreCheckException.class, () -> subject.pureChecks(txn3));
    }

    @Test
    void testCalculateFeesWithoutCallBody() {
        final var txn = TransactionBody.newBuilder().build();
        final var feeCtx = mock(FeeContext.class);
        given(feeCtx.body()).willReturn(txn);

        final var feeCalcFactory = mock(FeeCalculatorFactory.class);
        final var feeCalc = mock(FeeCalculator.class);
        given(feeCtx.feeCalculatorFactory()).willReturn(feeCalcFactory);
        given(feeCalcFactory.feeCalculator(notNull())).willReturn(feeCalc);

        assertDoesNotThrow(() -> subject.calculateFees(feeCtx));
    }

    private TransactionBody contractCallTransaction() {
        final var transactionID = TransactionID.newBuilder().accountID(payer).transactionValidStart(consensusTimestamp);
        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .contractCall(ContractCallTransactionBody.newBuilder()
                        .gas(1_234)
                        .amount(1_234L)
                        .contractID(targetContract))
                .build();
    }

    private TransactionBody contractCallTransactionWithNoContractId() {
        final var transactionID = TransactionID.newBuilder().accountID(payer).transactionValidStart(consensusTimestamp);
        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .contractCall(ContractCallTransactionBody.newBuilder())
                .build();
    }

    private TransactionBody contractCallTransactionWithInvalidContractId() {
        final var transactionID = TransactionID.newBuilder().accountID(payer).transactionValidStart(consensusTimestamp);
        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .contractCall(ContractCallTransactionBody.newBuilder()
                        .gas(INTRINSIC_GAS_FOR_0_ARG_METHOD - 1)
                        .contractID(invalidContract))
                .build();
    }

    private TransactionBody contractCallTransactionWithInsufficientGas() {
        final var transactionID = TransactionID.newBuilder().accountID(payer).transactionValidStart(consensusTimestamp);
        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .contractCall(ContractCallTransactionBody.newBuilder()
                        .gas(INTRINSIC_GAS_FOR_0_ARG_METHOD - 1)
                        .contractID(targetContract))
                .build();
    }
}
