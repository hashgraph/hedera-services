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

package com.swirlds.platform.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;

import com.swirlds.state.spi.QueueChangeListener;
import com.swirlds.state.test.fixtures.ListWritableQueueState;
import java.util.ConcurrentModificationException;
import java.util.LinkedList;
import java.util.Queue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

final class WritableQueueStateBaseTest<E> extends ReadableQueueStateBaseTest<E> {

    @Nested
    class AddTests {
        @Test
        void addDoesNotChangeDataSource() {
            final var backingList = new LinkedList<String>();
            final var subject = new ListWritableQueueState<>(STEAM_STATE_KEY, backingList);
            final var element = "Hydrology";

            subject.add(element);

            assertThat(backingList).isEmpty();
        }

        @Test
        void iteratorIsInvalidAfterAdd() {
            final var subject = writableSTEAMState();
            final var element = "Hydrology";

            final var iterator = subject.iterator();
            assertThat(iterator.hasNext()).isTrue();
            assertThat(iterator.next()).isEqualTo(ART);
            subject.add(element);
            assertThatThrownBy(iterator::next).isInstanceOf(ConcurrentModificationException.class);
        }

        @Test
        void iterateAfterAddGivesNewElements() {
            final var subject = writableSTEAMState();
            final var element = "Hydrology";

            subject.add(element);

            assertThat(subject.iterator())
                    .toIterable()
                    .containsExactly(ART, BIOLOGY, CHEMISTRY, DISCIPLINE, ECOLOGY, FIELDS, GEOMETRY, "Hydrology");
        }

        @Test
        void addOnEmptyIsVisibleWithPeek() {
            final var subject = ListWritableQueueState.builder("FAKE_KEY").build();
            final var element = "Hydrology";

            subject.add(element);

            assertThat(subject.peek()).isSameAs(element);
        }
    }

    @Nested
    class PeekTests {
        @Test
        void peekOnEmptyList() {
            final var subject = ListWritableQueueState.builder("FAKE_KEY").build();
            assertThat(subject.peek()).isNull();
        }

        @Test
        void peekOnPopulatedList() {
            final var subject = writableSTEAMState();
            assertThat(subject.peek()).isSameAs(ART);
        }

        @Test
        void peekAfterPoll() {
            final var subject = writableSTEAMState();
            subject.poll();
            assertThat(subject.peek()).isSameAs(BIOLOGY);
        }

        @Test
        void peekAfterRemoveIf() {
            final var subject = writableSTEAMState();
            subject.removeIf(s -> s.equals(ART));
            assertThat(subject.peek()).isSameAs(BIOLOGY);
        }

        @Test
        void peekAfterRemoveIfFails() {
            final var subject = writableSTEAMState();
            subject.removeIf(s -> false);
            assertThat(subject.peek()).isSameAs(ART);
        }

        @Test
        void peekAfterAdd() {
            final var subject = writableSTEAMState();
            subject.add("Hydrology");
            assertThat(subject.peek()).isSameAs(ART);
        }

        @Test
        void peekAfterAddOnEmptyList() {
            final var subject = ListWritableQueueState.builder("FAKE_KEY").build();
            subject.add("Hydrology");
            assertThat(subject.peek()).isSameAs("Hydrology");
        }
    }

    @Nested
    class PollTests {
        @Test
        void pollOnEmptyList() {
            final var subject = ListWritableQueueState.builder("FAKE_KEY").build();
            assertThat(subject.poll()).isNull();
        }

        @Test
        void pollOnPopulatedList() {
            final var subject = writableSTEAMState();
            assertThat(subject.poll()).isSameAs(ART);
            assertThat(subject.poll()).isSameAs(BIOLOGY);
            assertThat(subject.poll()).isSameAs(CHEMISTRY);
            assertThat(subject.poll()).isSameAs(DISCIPLINE);
            assertThat(subject.poll()).isSameAs(ECOLOGY);
            assertThat(subject.poll()).isSameAs(FIELDS);
            assertThat(subject.poll()).isSameAs(GEOMETRY);
            assertThat(subject.poll()).isNull();
        }

        @Test
        void pollAfterPeek() {
            final var subject = writableSTEAMState();
            subject.peek();
            assertThat(subject.poll()).isSameAs(ART);
        }

        @Test
        void pollAfterRemoveIf() {
            final var subject = writableSTEAMState();
            subject.removeIf(s -> s.equals(ART));
            assertThat(subject.poll()).isSameAs(BIOLOGY);
        }

        @Test
        void pollAfterRemoveIfFails() {
            final var subject = writableSTEAMState();
            subject.removeIf(s -> false);
            assertThat(subject.poll()).isSameAs(ART);
        }

        @Test
        void pollAfterAdd() {
            final var subject = writableSTEAMState();
            subject.add("Hydrology");
            assertThat(subject.poll()).isSameAs(ART);
            assertThat(subject.poll()).isSameAs(BIOLOGY);
            assertThat(subject.poll()).isSameAs(CHEMISTRY);
            assertThat(subject.poll()).isSameAs(DISCIPLINE);
            assertThat(subject.poll()).isSameAs(ECOLOGY);
            assertThat(subject.poll()).isSameAs(FIELDS);
            assertThat(subject.poll()).isSameAs(GEOMETRY);
            assertThat(subject.poll()).isSameAs("Hydrology");
            assertThat(subject.poll()).isNull();
        }

        @Test
        void pollAfterAddOnEmptyList() {
            final var subject = ListWritableQueueState.builder("FAKE_KEY").build();
            subject.add("Hydrology");
            assertThat(subject.poll()).isSameAs("Hydrology");
        }
    }

    @Nested
    class RemoveIfTests {
        @Test
        void removeIfOnEmptyList() {
            final var subject = ListWritableQueueState.builder("FAKE_KEY").build();
            assertThat(subject.removeIf(s -> true)).isNull();
        }

        @Test
        void removeIfFalsePredicate() {
            final var subject = writableSTEAMState();
            assertThat(subject.removeIf(s -> false)).isNull();
        }

        @Test
        void removeIfAfterAdd() {
            final var subject = ListWritableQueueState.builder("FAKE_KEY").build();
            subject.add("Hydrology");
            assertThat(subject.removeIf(s -> true)).isSameAs("Hydrology");
        }

        @Test
        void removeIfOnPopulatedList() {
            final var subject = writableSTEAMState();
            assertThat(subject.removeIf(s -> true)).isSameAs(ART);
            assertThat(subject.removeIf(s -> true)).isSameAs(BIOLOGY);
            assertThat(subject.removeIf(s -> true)).isSameAs(CHEMISTRY);
            assertThat(subject.removeIf(s -> true)).isSameAs(DISCIPLINE);
            assertThat(subject.removeIf(s -> true)).isSameAs(ECOLOGY);
            assertThat(subject.removeIf(s -> true)).isSameAs(FIELDS);
            assertThat(subject.removeIf(s -> true)).isSameAs(GEOMETRY);
            assertThat(subject.removeIf(s -> true)).isNull();
        }

        @Test
        void removeIfAfterPeek() {
            final var subject = writableSTEAMState();
            subject.peek();
            assertThat(subject.removeIf(s -> true)).isSameAs(ART);
        }

        @Test
        void removeIfAfterPoll() {
            final var subject = writableSTEAMState();
            subject.poll();
            assertThat(subject.removeIf(s -> true)).isSameAs(BIOLOGY);
        }

        @Test
        void removeIfAfterRemoveIfFails() {
            final var subject = writableSTEAMState();
            subject.removeIf(s -> false);
            assertThat(subject.removeIf(s -> true)).isSameAs(ART);
        }
    }

    @Nested
    class CommitTests {
        @Test
        void commitOnEmptyList() {
            final var backingList = new LinkedList<String>();
            final var subject = new ListWritableQueueState<>(STEAM_STATE_KEY, backingList);
            subject.commit();
            assertThat(backingList).isEmpty();
        }

        @Test
        void commitAfterAddOnEmptyList() {
            final var backingList = new LinkedList<String>();
            final var subject = new ListWritableQueueState<>(STEAM_STATE_KEY, backingList);
            final var element = "Hydrology";

            subject.add(element);
            subject.commit();

            assertThat(backingList).containsExactly(element);
        }

        @Test
        void commitTwice() {
            final var backingList = new LinkedList<String>();
            final var subject = new ListWritableQueueState<>(STEAM_STATE_KEY, backingList);
            final var element = "Hydrology";

            subject.add(element);
            subject.commit();
            subject.commit();

            assertThat(backingList).containsExactly(element);
        }

        @Test
        void commitAfterAddOnNonEmptyList() {
            final var backingList = new LinkedList<String>();
            backingList.add(ART);
            final var subject = new ListWritableQueueState<>(STEAM_STATE_KEY, backingList);

            subject.add(BIOLOGY);
            subject.commit();

            assertThat(backingList).containsExactly(ART, BIOLOGY);
        }

        @Test
        void commitAfterRemoving() {
            final var backingList = new LinkedList<String>();
            backingList.add(ART);
            final var subject = new ListWritableQueueState<>(STEAM_STATE_KEY, backingList);

            assertThat(subject.poll()).isEqualTo(ART);
            assertThat(backingList).containsExactly(ART);
            subject.commit();

            assertThat(backingList).isEmpty();
        }

        @Test
        void commitAfterRemovingAllAddedElementsDoesNotThrow() {
            final var backingList = new LinkedList<String>();
            final var subject = new ListWritableQueueState<>(STEAM_STATE_KEY, backingList);
            subject.add(ART);
            subject.add(BIOLOGY);
            subject.removeIf(s -> true);
            subject.removeIf(s -> true);
            assertThatCode(subject::commit).doesNotThrowAnyException();
        }

        @Test
        void commitAfterRemovingSomeAddedElementsOnlyIncludesAdded() {
            final var backingList = new LinkedList<String>();
            final var subject = new ListWritableQueueState<>(STEAM_STATE_KEY, backingList);
            subject.add(ART);
            subject.add(BIOLOGY);
            subject.add(CHEMISTRY);
            subject.removeIf(s -> true);
            subject.peek();
            subject.removeIf(s -> true);
            subject.peek();
            subject.commit();
            assertThat(backingList).containsExactly(CHEMISTRY);
        }

        @Test
        void commitAfterPeekingAndAddingStillAddsEverything() {
            final var backingList = new LinkedList<String>();
            backingList.add(ART);
            final var subject = new ListWritableQueueState<>(STEAM_STATE_KEY, backingList);
            subject.peek();
            subject.add(BIOLOGY);
            subject.commit();
            assertThat(backingList).containsExactly(ART, BIOLOGY);
        }
    }

    @Nested
    class ResetTests {
        @Test
        void resetOnEmptyList() {
            final var backingList = new LinkedList<String>();
            final var subject = new ListWritableQueueState<>(STEAM_STATE_KEY, backingList);
            subject.reset();
            assertThat(backingList).isEmpty();
        }

        @Test
        void resetBeforeCommit() {
            final var backingList = new LinkedList<String>();
            final var subject = new ListWritableQueueState<>(STEAM_STATE_KEY, backingList);

            subject.add(ART);
            subject.reset();
            subject.commit();

            assertThat(backingList).isEmpty();
        }

        @Test
        void resetAfterCommit() {
            final var backingList = new LinkedList<String>();
            final var subject = new ListWritableQueueState<>(STEAM_STATE_KEY, backingList);

            subject.add(ART);
            subject.commit();
            subject.reset();

            assertThat(backingList).containsExactly(ART);
        }
    }

    @Nested
    @DisplayName("with registered listeners")
    @ExtendWith(MockitoExtension.class)
    final class WithRegisteredListeners {
        @Mock
        private QueueChangeListener<String> firstListener;

        @Mock
        private QueueChangeListener<String> secondListener;

        private final Queue<String> backingStore = new LinkedList<>();
        private final ListWritableQueueState<String> subject =
                new ListWritableQueueState<>(STEAM_STATE_KEY, backingStore);

        @BeforeEach
        void setUp() {
            subject.registerListener(firstListener);
            subject.registerListener(secondListener);
        }

        @Test
        @DisplayName("all listeners are notified of pops and adds in order")
        void allAreNotifiedOfPopsAndAddsInOrder() {
            final var inOrder = inOrder(firstListener, secondListener);
            backingStore.add("Apple");
            backingStore.add("Banana");
            backingStore.add("Cantaloupe");

            subject.removeIf(s -> true);
            subject.removeIf(s -> true);
            subject.add("Dragonfruit");
            subject.commit();

            inOrder.verify(firstListener).queuePopChange();
            inOrder.verify(secondListener).queuePopChange();
            inOrder.verify(firstListener).queuePopChange();
            inOrder.verify(secondListener).queuePopChange();
            inOrder.verify(firstListener).queuePushChange("Dragonfruit");
            inOrder.verify(secondListener).queuePushChange("Dragonfruit");
        }
    }
}
