package com.hedera.node.app.service.contract.impl.test.exec.operations;

import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import com.hedera.node.app.service.contract.impl.exec.operations.CustomCreate2Operation;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static com.hedera.node.app.service.contract.impl.test.exec.utils.TestHelpers.assertSameResult;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

class CustomCreate2OperationTest extends CreateOperationTestBase {
    private static final Bytes SALT = Bytes.fromHexString("0x2a");

    @Mock
    private FeatureFlags featureFlags;

    private CustomCreate2Operation subject;

    @BeforeEach
    void setUp() {
        subject = new CustomCreate2Operation(gasCalculator, featureFlags);
    }

    @Test
    void returnsInvalidWhenDisabled() {
        final var expected = new Operation.OperationResult(0L, ExceptionalHaltReason.INVALID_OPERATION);
        assertSameResult(expected, subject.execute(frame, evm));
    }

    @Test
    void failsWhenMatchingHollowAccountExistsAndLazyCreationDisabled() {
        given(featureFlags.isCreate2Enabled(frame)).willReturn(true);
        given(frame.stackSize()).willReturn(4);
        given(frame.getRemainingGas()).willReturn(GAS_COST);
        given(gasCalculator.create2OperationGasCost(frame)).willReturn(GAS_COST);
        given(frame.getStackItem(0)).willReturn(Bytes.ofUnsignedLong(VALUE));
        given(frame.getRecipientAddress()).willReturn(RECIEVER_ADDRESS);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.getAccount(RECIEVER_ADDRESS)).willReturn(receiver);
        given(receiver.getMutable()).willReturn(mutableReceiver);
        given(mutableReceiver.getBalance()).willReturn(Wei.of(VALUE));
        given(frame.getMessageStackDepth()).willReturn(1023);
        given(frame.getStackItem(anyInt())).willReturn(Bytes.ofUnsignedLong(1));

//        given(worldUpdater.setupCreate2(RECIEVER_ADDRESS);

        final var expected = new Operation.OperationResult(GAS_COST, null);
        assertSameResult(expected, subject.execute(frame, evm));

        verify(frame).readMutableMemory(1L, 1L);
        verify(frame).popStackItems(3);
        verify(frame).pushStackItem(UInt256.ZERO);
    }
}