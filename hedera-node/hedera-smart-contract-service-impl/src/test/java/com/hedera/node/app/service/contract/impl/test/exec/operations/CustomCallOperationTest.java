package com.hedera.node.app.service.contract.impl.test.exec.operations;

import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import com.hedera.node.app.service.contract.impl.exec.operations.CustomCallOperation;
import com.hedera.node.app.service.contract.impl.test.exec.utils.TestHelpers;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.CallOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CustomCallOperationTest {
    @Mock
    private FeatureFlags featureFlags;
    @Mock
    private GasCalculator gasCalculator;
    @Mock
    private AddressChecks addressChecks;
    @Mock
    private MessageFrame frame;
    @Mock
    private EVM evm;

    private CustomCallOperation subject;

    @BeforeEach
    void setUp() {
        subject = new CustomCallOperation(featureFlags, gasCalculator, addressChecks);
    }

    @Test
    void withImplicitCreationEnabledDoesNoFurtherChecks() {
        givenStaticFrameWith(1L, TestHelpers.EIP_1014_ADDRESS);
        given(featureFlags.isImplicitCreationEnabled(frame)).willReturn(true);
    }

    /**
     * Return a frame with the given value and address as the top two stack items, and with
     * {@link MessageFrame#isStatic()} returning true. This lets us detect when we fall
     * through to the base {@link CallOperation} implementation, since
     *
     * @param value
     * @param address
     */
    private void givenStaticFrameWith(final long value, final Address address) {
        given(frame.getStackItem(1)).willReturn(address);
        given(frame.getStackItem(2)).willReturn(Bytes32.leftPad(Bytes.ofUnsignedLong(value)));
        given(frame.isStatic()).willReturn(true);
    }
}