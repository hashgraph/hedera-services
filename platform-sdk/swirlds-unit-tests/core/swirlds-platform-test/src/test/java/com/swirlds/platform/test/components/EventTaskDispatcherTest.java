/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.components;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EventTaskDispatcherTest {
    // TODO
//    @Test
//    @Tag(TestTypeTags.FUNCTIONAL)
//    @Tag(TestComponentTags.PLATFORM)
//    @DisplayName("Test dispatching")
//    void test() {
//        final EventCreator creator = mock(EventCreator.class);
//        final EventValidator validator = mock(EventValidator.class);
//        @SuppressWarnings("unchecked")
//        final Consumer<GossipEvent> intake = (Consumer<GossipEvent>) mock(Consumer.class);
//
//        final EventTaskDispatcher dispatcher = new EventTaskDispatcher(
//                Time.getCurrent(),
//                validator,
//                creator,
//                intake,
//                mock(EventIntakeMetrics.class),
//                mock(IntakeCycleStats.class));
//
//        // create event
//        final NodeId otherId = new NodeId(5);
//        final CreateEventTask createTask = new CreateEventTask(otherId);
//        dispatcher.dispatchTask(createTask);
//        verify(creator).createEvent(otherId);
//
//        // validate event
//        final GossipEvent validateTask = mock(GossipEvent.class);
//        final BaseEventHashedData hashedData = mock(BaseEventHashedData.class);
//        final BaseEventUnhashedData unhashedData = mock(BaseEventUnhashedData.class);
//        when(validateTask.getHashedData()).thenReturn(hashedData);
//        when(validateTask.getUnhashedData()).thenReturn(unhashedData);
//        dispatcher.dispatchTask(validateTask);
//        verify(validator).validateEvent(validateTask);
//
//        // valid event
//        final GossipEvent event = mock(GossipEvent.class);
//        final ValidEvent validEvent = new ValidEvent(event);
//        dispatcher.dispatchTask(validEvent);
//        verify(intake).accept(event);
//    }
}
