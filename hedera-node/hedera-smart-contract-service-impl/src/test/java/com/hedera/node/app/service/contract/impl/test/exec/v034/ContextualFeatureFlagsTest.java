package com.hedera.node.app.service.contract.impl.test.exec.v034;

import com.hedera.node.app.service.contract.impl.exec.v034.ContextualFeatureFlags;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.node.app.service.contract.impl.exec.TransactionProcessor.CONFIG_CONTEXT_VARIABLE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ContextualFeatureFlagsTest {
    @Mock
    private MessageFrame frame;
    
    private ContextualFeatureFlags subject = new ContextualFeatureFlags();

    @Test
    void implicitCreationEnabledIfLazyAndAutoCreationBothEnabled() {
        final var config = new HederaTestConfigBuilder().getOrCreateConfig();
        given(frame.getContextVariable(CONFIG_CONTEXT_VARIABLE)).willReturn(config);
        assertTrue(subject.isImplicitCreationEnabled(frame));
    }

    @Test
    void implicitCreationNotEnabledIfLazyCreationNotEnabled() {
        final var config = new HederaTestConfigBuilder()
                .withValue("lazyCreation.enabled", false)
                .getOrCreateConfig();
        given(frame.getContextVariable(CONFIG_CONTEXT_VARIABLE)).willReturn(config);
        assertFalse(subject.isImplicitCreationEnabled(frame));
    }

    @Test
    void implicitCreationNotEnabledIfAutoCreationNotEnabled() {
        final var config = new HederaTestConfigBuilder()
                .withValue("autoCreation.enabled", false)
                .getOrCreateConfig();
        given(frame.getContextVariable(CONFIG_CONTEXT_VARIABLE)).willReturn(config);
        assertFalse(subject.isImplicitCreationEnabled(frame));
    }
}