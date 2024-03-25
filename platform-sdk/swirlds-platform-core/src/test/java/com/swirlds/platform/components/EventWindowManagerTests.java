/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.time.Time;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.wiring.component.ComponentWiring;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.internal.ConsensusRound;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class EventWindowManagerTests {

    @Test
    void WiringInputTest() {
        final EventWindowManager eventWindowManager = new DefaultEventWindowManager();
        final WiringModel model = WiringModel.create(
                TestPlatformContextBuilder.create().build(), Time.getCurrent(), ForkJoinPool.commonPool());
        final ComponentWiring<EventWindowManager, NonAncientEventWindow> wiring = new ComponentWiring<>(
                model,
                EventWindowManager.class,
                model.schedulerBuilder("eventWindowManager")
                        .withType(TaskSchedulerType.DIRECT_THREADSAFE)
                        .build()
                        .cast());
        wiring.bind(eventWindowManager);

        final AtomicReference<NonAncientEventWindow> output = new AtomicReference<>(null);

        wiring.getOutputWire().solderTo("output", "event window", output::set);

        final NonAncientEventWindow eventWindow1 = mock(NonAncientEventWindow.class);
        wiring.getInputWire(EventWindowManager::updateEventWindow).inject(eventWindow1);
        assertSame(eventWindow1, output.get());

        final NonAncientEventWindow eventWindow2 = mock(NonAncientEventWindow.class);
        final ConsensusRound round = mock(ConsensusRound.class);
        when(round.getNonAncientEventWindow()).thenReturn(eventWindow2);
        wiring.getInputWire(EventWindowManager::extractEventWindow).inject(round);
        assertSame(eventWindow2, output.get());
        assertNotSame(eventWindow1, eventWindow2);
    }
}
