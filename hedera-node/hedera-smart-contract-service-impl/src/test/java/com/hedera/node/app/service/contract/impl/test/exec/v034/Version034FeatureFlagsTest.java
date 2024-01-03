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

package com.hedera.node.app.service.contract.impl.test.exec.v034;

import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.CONFIG_CONTEXT_VARIABLE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.v034.Version034FeatureFlags;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import java.util.Deque;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Version034FeatureFlagsTest {
    @Mock
    private MessageFrame frame;

    @Mock
    private Deque<MessageFrame> stack;

    private Version034FeatureFlags subject = new Version034FeatureFlags();

    @Test
    void implicitCreationEnabledIfLazyAndAutoCreationBothEnabled() {
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.isEmpty()).willReturn(true);
        final var config = HederaTestConfigBuilder.create().getOrCreateConfig();
        given(frame.getContextVariable(CONFIG_CONTEXT_VARIABLE)).willReturn(config);
        assertTrue(subject.isImplicitCreationEnabled(frame));
    }

    @Test
    void implicitCreationNotEnabledIfLazyCreationNotEnabled() {
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.isEmpty()).willReturn(true);
        final var config = HederaTestConfigBuilder.create()
                .withValue("lazyCreation.enabled", false)
                .getOrCreateConfig();
        given(frame.getContextVariable(CONFIG_CONTEXT_VARIABLE)).willReturn(config);
        assertFalse(subject.isImplicitCreationEnabled(frame));
    }

    @Test
    void implicitCreationNotEnabledIfAutoCreationNotEnabled() {
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.isEmpty()).willReturn(true);
        final var config = HederaTestConfigBuilder.create()
                .withValue("autoCreation.enabled", false)
                .getOrCreateConfig();
        given(frame.getContextVariable(CONFIG_CONTEXT_VARIABLE)).willReturn(config);
        assertFalse(subject.isImplicitCreationEnabled(frame));
    }
}
