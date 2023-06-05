package com.hedera.node.app.service.contract.impl.test.exec.operations;

import com.hedera.node.app.service.contract.impl.exec.operations.CustomChainIdOperation;
import com.hedera.node.app.service.contract.impl.test.exec.utils.TestHelpers;
import com.hedera.node.config.data.ContractsConfig;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;

import static com.hedera.node.app.service.contract.impl.exec.TransactionProcessor.CONFIG_CONTEXT_VARIABLE;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomChainIdOperationTest {
    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private EVM evm;
    @Mock
    private MessageFrame messageFrame;

    private CustomChainIdOperation subject;

    @BeforeEach
    void setUp() {
        given(gasCalculator.getBaseTierGasCost()).willReturn(123L);
        subject = new CustomChainIdOperation(gasCalculator);
    }

    @Test
    void usesContractsConfigFromContext() {
        final var config = new HederaTestConfigBuilder().getOrCreateConfig();
        given(messageFrame.getContextVariable(CONFIG_CONTEXT_VARIABLE)).willReturn(config);
        given(messageFrame.getRemainingGas()).willReturn(Long.MAX_VALUE);
        final var contractsConfig = config.getConfigData(ContractsConfig.class);
        final var expectedStackItem = Bytes32.fromHexStringLenient(Integer.toString(contractsConfig.chainId(), 16));
        final var expectedResult = new Operation.OperationResult(123L, null);
        final var actualResult = subject.execute(messageFrame, evm);
        TestHelpers.assertSameResult(actualResult, expectedResult);
        verify(messageFrame).pushStackItem(expectedStackItem);
    }

    @Test
    void checksForOOG() {
        final var expectedResult = new Operation.OperationResult(123L, ExceptionalHaltReason.INSUFFICIENT_GAS);
        final var actualResult = subject.execute(messageFrame, evm);
        TestHelpers.assertSameResult(actualResult, expectedResult);
    }
}