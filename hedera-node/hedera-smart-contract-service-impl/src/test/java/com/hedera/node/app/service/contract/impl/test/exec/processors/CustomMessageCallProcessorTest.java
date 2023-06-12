package com.hedera.node.app.service.contract.impl.test.exec.processors;

import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason;
import com.hedera.node.app.service.contract.impl.exec.processors.CustomMessageCallProcessor;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.test.exec.utils.TestHelpers;
import com.hedera.node.app.spi.state.FilteredReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static com.hedera.node.app.service.contract.impl.test.exec.utils.TestHelpers.assertSameResult;
import static com.hedera.node.app.service.contract.impl.test.exec.utils.TestHelpers.isSameResult;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomMessageCallProcessorTest {
    private static final Address HTS_PRECOMPILE_ADDRESS = Address.fromHexString("0x167");
    private static final Address NON_EVM_PRECOMPILE_SYSTEM_ADDRESS = Address.fromHexString("0x222");
    private static final Address CODE_ADDRESS = Address.fromHexString("0x111222333");
    private static final Address SENDER_ADDRESS = Address.fromHexString("0x222333444");
    private static final Address RECEIVER_ADDRESS = Address.fromHexString("0x33344455");

    @Mock
    private EVM evm;
    @Mock
    private MessageFrame frame;
    @Mock
    private AddressChecks addressChecks;
    @Mock
    private PrecompiledContract htsPrecompile;
    @Mock
    private PrecompiledContract nativePrecompile;
    @Mock
    private OperationTracer operationTracer;
    @Mock
    private PrecompileContractRegistry registry;
    @Mock
    private ProxyWorldUpdater proxyWorldUpdater;

    private CustomMessageCallProcessor subject;

    @BeforeEach
    void setUp() {
        subject = new CustomMessageCallProcessor(evm, registry, addressChecks, Map.of(HTS_PRECOMPILE_ADDRESS, htsPrecompile));
    }

    @Test
    void hederaPrecompilesNotYetSupported() {
        givenCallWithCode(HTS_PRECOMPILE_ADDRESS);
        assertThrows(UnsupportedOperationException.class, () -> subject.start(frame, operationTracer));
    }

    @Test
    void callsToNonStandardSystemContractsAreNotSupported() {
        givenCallWithCode(NON_EVM_PRECOMPILE_SYSTEM_ADDRESS);
        given(addressChecks.isSystemAccount(NON_EVM_PRECOMPILE_SYSTEM_ADDRESS)).willReturn(true);

        subject.start(frame, operationTracer);

        verifyHalt(ExceptionalHaltReason.PRECOMPILE_ERROR);
    }

    @Test
    void valueCannotBeTransferredToSystemContracts() {
        givenCallWithCode(Address.ALTBN128_ADD);
        given(addressChecks.isSystemAccount(Address.ALTBN128_ADD)).willReturn(true);
        given(registry.get(Address.ALTBN128_ADD)).willReturn(nativePrecompile);
        given(frame.getValue()).willReturn(Wei.ONE);

        subject.start(frame, operationTracer);

        verifyHalt(CustomExceptionalHaltReason.INVALID_VALUE_TRANSFER);
    }

    @Test
    void haltsIfValueTransferFails() {
        givenWellKnownUserSpaceCall();
        given(frame.getValue()).willReturn(Wei.ONE);
        given(frame.getWorldUpdater()).willReturn(proxyWorldUpdater);
        given(addressChecks.isPresent(RECEIVER_ADDRESS, frame)).willReturn(true);
        given(proxyWorldUpdater.tryTransferFromContract(
                SENDER_ADDRESS,
                RECEIVER_ADDRESS,
                Wei.ONE.toLong(),
                true)).willReturn(Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));

        subject.start(frame, operationTracer);

        verifyHalt(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);
    }

    @Test
    void triesLazyCreationBeforeValueTransferIfRecipientMissing() {
        givenWellKnownUserSpaceCall();
        given(frame.getValue()).willReturn(Wei.ONE);
        given(frame.getWorldUpdater()).willReturn(proxyWorldUpdater);
        given(proxyWorldUpdater.tryTransferFromContract(
                SENDER_ADDRESS,
                RECEIVER_ADDRESS,
                Wei.ONE.toLong(),
                true)).willReturn(Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));

        subject.start(frame, operationTracer);

        verifyHalt(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);
    }

    private void givenCallWithCode(@NonNull final Address contract) {
        given(frame.getContractAddress()).willReturn(contract);
    }

    private void givenWellKnownUserSpaceCall() {
        given(frame.getContractAddress()).willReturn(CODE_ADDRESS);
        given(frame.getRecipientAddress()).willReturn(RECEIVER_ADDRESS);
        given(frame.getSenderAddress()).willReturn(SENDER_ADDRESS);
    }

    private void verifyHalt(@NonNull final ExceptionalHaltReason reason) {
        verify(frame).setExceptionalHaltReason(Optional.of(reason));
        verify(frame).setState(MessageFrame.State.EXCEPTIONAL_HALT);
        verify(operationTracer).tracePostExecution(eq(frame), argThat(result ->
                isSameResult(new Operation.OperationResult(0, reason), result)));
    }
}