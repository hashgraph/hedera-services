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

package com.swirlds.state.test.fixtures;

import com.swirlds.state.spi.WritableQueueStateBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;

/** Useful class for testing {@link WritableQueueStateBase} */
public class ListWritableQueueState<E> extends WritableQueueStateBase<E> {
    /** Represents the backing storage for this state */
    private final Queue<E> backingStore;

    /**
     * Create an instance using the given Queue as the backing store. This is useful when you want to
     * pre-populate the queue, or if you want to use Mockito to mock it or cause it to throw
     * exceptions when certain keys are accessed, etc.
     *
     * @param stateKey The state key for this state
     * @param backingStore The backing store to use
     */
    public ListWritableQueueState(@NonNull final String stateKey, @NonNull final Queue<E> backingStore) {
        super(stateKey);
        this.backingStore = Objects.requireNonNull(backingStore);
    }

    @Override
    protected void addToDataSource(@NonNull E element) {
        backingStore.add(element);
    }

    @Override
    protected void removeFromDataSource() {
        backingStore.remove();
    }

    @NonNull
    @Override
    protected Iterator<E> iterateOnDataSource() {
        return backingStore.iterator();
    }

    /**
     * Create a new {@link ListWritableQueueState.Builder} for building a {@link ListWritableQueueState}. The builder has
     * convenience methods for pre-populating the queue.
     *
     * @param stateKey The state key
     * @return A {@link ListWritableQueueState.Builder} to be used for creating a {@link ListWritableQueueState}.
     * @param <E> The element type
     */
    @NonNull
    public static <E> ListWritableQueueState.Builder<E> builder(@NonNull final String stateKey) {
        return new ListWritableQueueState.Builder<>(stateKey);
    }

    /**
     * A convenient builder for creating instances of {@link ListWritableQueueState}.
     */
    public static final class Builder<E> {
        private final Queue<E> backingStore = new LinkedList<>();
        private final String stateKey;

        Builder(@NonNull final String stateKey) {
            this.stateKey = stateKey;
        }

        /**
         * Add an element to the state's backing map. This is used to pre-initialize the backing map. The created state
         * will be "clean" with no modifications.
         *
         * @param element The element
         * @return a reference to this builder
         */
        @NonNull
        public ListWritableQueueState.Builder<E> value(@NonNull E element) {
            backingStore.add(element);
            return this;
        }

        /**
         * Builds the state.
         *
         * @return an instance of the state, preloaded with whatever elements were defined.
         */
        @NonNull
        public ListWritableQueueState<E> build() {
            return new ListWritableQueueState<>(stateKey, new LinkedList<>(backingStore));
        }
    }
}
