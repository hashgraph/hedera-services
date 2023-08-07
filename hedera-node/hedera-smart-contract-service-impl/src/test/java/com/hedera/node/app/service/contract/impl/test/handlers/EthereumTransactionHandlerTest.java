package com.hedera.node.app.service.contract.impl.test.handlers;

import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.CallOutcome;
import com.hedera.node.app.service.contract.impl.exec.ContextTransactionProcessor;
import com.hedera.node.app.service.contract.impl.exec.TransactionComponent;
import com.hedera.node.app.service.contract.impl.exec.TransactionModule;
import com.hedera.node.app.service.contract.impl.handlers.ContractCallHandler;
import com.hedera.node.app.service.contract.impl.handlers.EthereumTransactionHandler;
import com.hedera.node.app.service.contract.impl.infra.EthereumSignatures;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import com.hedera.node.app.service.contract.impl.records.EthereumTransactionRecordBuilder;
import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.test.TestHelpers;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CALLED_CONTRACT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ETH_DATA_WITHOUT_TO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ETH_DATA_WITH_TO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SUCCESS_RESULT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EthereumTransactionHandlerTest {
    @Mock
    private EthereumSignatures ethereumSignatures;
    @Mock
    private TransactionComponent component;

    @Mock
    private HandleContext handleContext;
    @Mock
    private PreHandleContext preHandleContext;

    @Mock
    private TransactionComponent.Factory factory;

    @Mock
    private ContextTransactionProcessor processor;

    @Mock
    private EthereumTransactionRecordBuilder recordBuilder;

    @Mock
    private RootProxyWorldUpdater baseProxyWorldUpdater;

    private EthereumTransactionHandler subject;

    @BeforeEach
    void setUp() {
        subject = new EthereumTransactionHandler(ethereumSignatures, () -> factory);
    }

    @Test
    void delegatesToCreatedComponentAndExposesEthTxDataCallWithToAddress() {
        given(factory.create(handleContext)).willReturn(component);
        given(component.ethTxData()).willReturn(ETH_DATA_WITH_TO_ADDRESS);
        given(component.contextTransactionProcessor()).willReturn(processor);
        given(handleContext.recordBuilder(EthereumTransactionRecordBuilder.class)).willReturn(recordBuilder);
        final var expectedResult = SUCCESS_RESULT.asProtoResultOf(ETH_DATA_WITH_TO_ADDRESS, baseProxyWorldUpdater);
        final var expectedOutcome = new CallOutcome(expectedResult, SUCCESS_RESULT.finalStatus());
        given(processor.call()).willReturn(expectedOutcome);

        given(recordBuilder.status(SUCCESS)).willReturn(recordBuilder);
        given(recordBuilder.contractID(CALLED_CONTRACT_ID)).willReturn(recordBuilder);
        given(recordBuilder.contractCallResult(expectedResult)).willReturn(recordBuilder);
        given(recordBuilder.ethereumHash(Bytes.wrap(ETH_DATA_WITH_TO_ADDRESS.getEthereumHash()))).willReturn(recordBuilder);

        assertDoesNotThrow(() -> subject.handle(handleContext));
    }

    @Test
    void delegatesToCreatedComponentAndExposesEthTxDataCreateWithoutToAddress() {
        given(factory.create(handleContext)).willReturn(component);
        given(component.ethTxData()).willReturn(ETH_DATA_WITHOUT_TO_ADDRESS);
        given(component.contextTransactionProcessor()).willReturn(processor);
        given(handleContext.recordBuilder(EthereumTransactionRecordBuilder.class)).willReturn(recordBuilder);
        given(baseProxyWorldUpdater.getCreatedContractIds()).willReturn(List.of(CALLED_CONTRACT_ID));
        final var expectedResult = SUCCESS_RESULT.asProtoResultOf(ETH_DATA_WITHOUT_TO_ADDRESS, baseProxyWorldUpdater);
        final var expectedOutcome = new CallOutcome(expectedResult, SUCCESS_RESULT.finalStatus());
        given(processor.call()).willReturn(expectedOutcome);

        given(recordBuilder.status(SUCCESS)).willReturn(recordBuilder);
        given(recordBuilder.contractID(CALLED_CONTRACT_ID)).willReturn(recordBuilder);
        given(recordBuilder.contractCreateResult(expectedResult)).willReturn(recordBuilder);
        given(recordBuilder.ethereumHash(Bytes.wrap(ETH_DATA_WITHOUT_TO_ADDRESS.getEthereumHash()))).willReturn(recordBuilder);

        assertDoesNotThrow(() -> subject.handle(handleContext));
    }

    @Test
    void preHandleCachesTheSignatures() {
        final var ethTxn = EthereumTransactionBody.newBuilder()
                .ethereumData(TestHelpers.ETH_WITH_TO_ADDRESS)
                .build();
        final var body = TransactionBody.newBuilder().ethereumTransaction(ethTxn).build();
        given(preHandleContext.body()).willReturn(body);
        subject.preHandle(preHandleContext);
        verify(ethereumSignatures).impliedBy(ETH_DATA_WITH_TO_ADDRESS);
    }

    @Test
    void preHandleIgnoresUnparseable() {
        final var ethTxn = EthereumTransactionBody.newBuilder()
                .ethereumData(Bytes.EMPTY)
                .build();
        final var body = TransactionBody.newBuilder().ethereumTransaction(ethTxn).build();
        given(preHandleContext.body()).willReturn(body);
        subject.preHandle(preHandleContext);
        verifyNoInteractions(ethereumSignatures);
    }
}