package com.hedera.node.app.service.contract.impl.test.exec.v030;

import com.hedera.node.app.service.contract.impl.exec.v030.DisabledFeatureFlags;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DisabledFeatureFlagsTest {
    @Mock
    private MessageFrame frame;

    private DisabledFeatureFlags subject = new DisabledFeatureFlags();

    @Test
    void everythingIsDisabled() {
        assertFalse(subject.isImplicitCreationEnabled(frame));
    }
}