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

package com.swirlds.platform.wiring;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.platform.components.LinkedEventIntake;
import com.swirlds.platform.event.deduplication.EventDeduplicator;
import com.swirlds.platform.event.linking.InOrderLinker;
import com.swirlds.platform.event.orphan.OrphanBuffer;
import com.swirlds.platform.event.validation.EventSignatureValidator;
import com.swirlds.platform.event.validation.InternalEventValidator;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PlatformWiring}
 */
class PlatformWiringTests {
    @Test
    @DisplayName("Assert that all input wires are bound to something")
    void testBindings() {
        final PlatformWiring wiring =
                new PlatformWiring(TestPlatformContextBuilder.create().build(), new FakeTime());

        wiring.bind(
                mock(InternalEventValidator.class),
                mock(EventDeduplicator.class),
                mock(EventSignatureValidator.class),
                mock(OrphanBuffer.class),
                mock(InOrderLinker.class),
                mock(LinkedEventIntake.class));

        assertFalse(wiring.getModel().checkForUnboundInputWires());
    }
}
