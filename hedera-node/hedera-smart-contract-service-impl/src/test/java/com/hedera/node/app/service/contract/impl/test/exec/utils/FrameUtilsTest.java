/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.utils;

import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.CONFIG_CONTEXT_VARIABLE;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.TRACKER_CONTEXT_VARIABLE;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.accessTrackerFor;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.configOf;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_CONFIG;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.operations.utils.OpUtils;
import com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils;
import com.hedera.node.app.service.contract.impl.infra.StorageAccessTracker;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.node.app.service.contract.impl.utils.OpcodeUtils;
import com.hedera.node.app.service.contract.impl.utils.SynthTxnUtils;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FrameUtilsTest {
    private static final Set<Class<?>> toBeTested = new HashSet<>(Arrays.asList(
            FrameUtils.class, ConversionUtils.class, OpUtils.class, OpcodeUtils.class, SynthTxnUtils.class));

    @Mock
    private MessageFrame frame;

    @Mock
    private MessageFrame initialFrame;

    private Deque<MessageFrame> stack = new ArrayDeque<>();

    @Test
    void throwsInConstructor() {
        for (final var clazz : toBeTested) {
            assertFor(clazz);
        }
    }

    @Test
    void getsContextVariablesFromInitialFrameIfStackEmpty() {
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(frame.getContextVariable(CONFIG_CONTEXT_VARIABLE)).willReturn(DEFAULT_CONFIG);
        given(frame.getMessageFrameStack()).willReturn(stack);
        assertSame(DEFAULT_CONFIG, configOf(frame));
    }

    @Test
    void getsContextVariablesFromBottomOfStackIfNotInitialFrame() {
        givenNonInitialFrame();
        given(initialFrame.getContextVariable(CONFIG_CONTEXT_VARIABLE)).willReturn(DEFAULT_CONFIG);
        given(frame.getMessageFrameStack()).willReturn(stack);
        assertSame(DEFAULT_CONFIG, configOf(frame));
    }

    @Test
    void checksForAccessorAsExpected() {
        givenNonInitialFrame();
        given(frame.getMessageFrameStack()).willReturn(stack);
        final var tracker = new StorageAccessTracker();
        given(initialFrame.getContextVariable(TRACKER_CONTEXT_VARIABLE)).willReturn(tracker);
        assertSame(tracker, accessTrackerFor(frame));
    }

    @Test
    void okIfFrameHasNoTracker() {
        given(frame.getMessageFrameStack()).willReturn(stack);
        assertNull(accessTrackerFor(frame));
    }

    void givenNonInitialFrame() {
        stack.push(initialFrame);
        stack.addFirst(frame);
    }

    private static final String UNEXPECTED_THROW = "Unexpected `%s` was thrown in `%s` constructor!";
    private static final String NO_THROW = "No exception was thrown in `%s` constructor!";

    private void assertFor(final Class<?> clazz) {
        try {
            final var constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);

            constructor.newInstance();
        } catch (final InvocationTargetException expected) {
            final var cause = expected.getCause();
            Assertions.assertTrue(
                    cause instanceof UnsupportedOperationException, String.format(UNEXPECTED_THROW, cause, clazz));
            return;
        } catch (final Exception e) {
            Assertions.fail(String.format(UNEXPECTED_THROW, e, clazz));
        }
        Assertions.fail(String.format(NO_THROW, clazz));
    }
}
