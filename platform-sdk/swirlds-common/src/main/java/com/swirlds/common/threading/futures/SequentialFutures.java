/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.threading.futures;

import static com.swirlds.common.threading.futures.FutureUtils.getImmediately;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.Supplier;

/**
 * This utility object allows a caller to get a {@link Future} on an object in a sequential
 * stream of objects. If two callers request a future on the same sequence element, then each caller receives
 * the same instance of the Future.
 *
 * @param <T>
 * 		the type of the object held by the future
 */
public class SequentialFutures<T> {

    /**
     * A map of indices to futures.
     */
    private final Map<Long, StandardFuture<T>> futures = new HashMap<>();

    /**
     * The total number of values to store. Old values are removed from {@link #futures} to maintain this limit.
     */
    private final long numberOfValuesToStore;

    /**
     * A method that is called when a gap in the sequence is detected.
     */
    private final GapHandler<T> gapHandler;

    /**
     * The value of the lowest index that has not yet been completed.
     */
    private long lowestUncompletedIndex;

    /**
     * The value of the lowest index currently in {@link #futures}.
     */
    private long lowestUnremovedIndex;

    /**
     * The most recent value. May be from a completed value, or a value that was a filled in gap.
     */
    private T mostRecentValueCompleted;

    /**
     * Create a new SequentialFutures object.
     *
     * @param nextIndex
     * 		the next index of the sequence that will be completed
     * @param numberOfValuesToStore
     * 		the number of previously received values to store
     * @param initialValues
     * 		a method that is used to pre-populate values that come before the next index
     * @param gapHandler
     * 		a method that is used to fill gaps in the sequence
     */
    public SequentialFutures(
            final long nextIndex,
            final int numberOfValuesToStore,
            final LongFunction<T> initialValues,
            final GapHandler<T> gapHandler) {
        this.lowestUncompletedIndex = nextIndex;
        this.numberOfValuesToStore = numberOfValuesToStore;
        this.gapHandler = gapHandler;

        lowestUnremovedIndex = nextIndex - numberOfValuesToStore;
        for (long sequence = lowestUnremovedIndex; sequence < nextIndex; sequence++) {
            final T initialValue = initialValues.apply(sequence);
            mostRecentValueCompleted = initialValue;
            this.futures.put(sequence, new StandardFuture<>(initialValue));
        }
    }

    /**
     * Get a {@link Future} for an event at a given index.
     *
     * @param index
     * 		the index of the event
     * @return a future for the event with the given index
     * @throws IllegalStateException
     * 		if an event in the past is requested
     */
    public Future<T> get(final long index) {
        final Future<T> future = getIfAvailable(index);
        if (future == null) {
            throw new IllegalStateException("requested index " + index + " is in the past and is unavailable");
        }
        return future;
    }

    /**
     * Get a {@link Future} for an event at a given index if it is available. Returns null if the future is not
     * available because the requested index is too far in the past.
     *
     * @param index
     * 		the requested sequence index
     * @return a future if available, or null if the requested index is too old
     */
    public synchronized Future<T> getIfAvailable(final long index) {
        final long lowestAllowedIndex = lowestUncompletedIndex - numberOfValuesToStore;
        if (index < lowestAllowedIndex) {
            return null;
        }

        final StandardFuture<T> future = futures.get(index);
        if (future != null) {
            return future;
        } else {
            final StandardFuture<T> newFuture = new StandardFuture<>();
            futures.put(index, newFuture);
            return newFuture;
        }
    }

    /**
     * Wait on a value. Syntactic sugar for SequentialFutures.{@link #get(long)}.{@link Future#get() get()}.
     *
     * @param index
     * 		the index of the event
     * @return an object returned by a future
     * @throws ExecutionException
     * 		if there is an exception thrown while computing the value
     * @throws InterruptedException
     * 		if this thread is interrupted before the value is returned
     * @throws IllegalStateException
     * 		if an event in the past is requested
     */
    public T getValue(final long index) throws ExecutionException, InterruptedException {
        return get(index).get();
    }

    /**
     * Wait on a value and get it if it is available, or return null if the requested index is too far in the past.
     *
     * @param index
     * 		the index of the event
     * @return an object returned by a future, or null if the future is not available or if it has been cancelled
     * @throws ExecutionException
     * 		if there is an exception thrown while computing the value
     * @throws InterruptedException
     * 		if this thread is interrupted before the value is returned
     */
    public T getValueIfAvailable(final long index) throws ExecutionException, InterruptedException {
        final Future<T> future = getIfAvailable(index);
        if (future == null) {
            return null;
        }
        try {
            return future.get();
        } catch (final CancellationException exception) {
            return null;
        }
    }

    /**
     * Fill all gaps between lowestUncompletedIndex and the given index
     *
     * @param index
     * 		the index to fill until
     */
    private void fillGaps(final long index) {
        while (index != lowestUncompletedIndex) {
            final StandardFuture<T> future =
                    futures.computeIfAbsent(lowestUncompletedIndex, i -> new StandardFuture<>());

            gapHandler.handleGap(lowestUncompletedIndex, future, mostRecentValueCompleted);

            if (!future.isCancelled() && future.isDone()) {
                mostRecentValueCompleted = getImmediately(future);
            }
            lowestUncompletedIndex++;
        }
    }

    /**
     * Remove old completed/cancelled futures if there are too many.
     */
    private void pruneOldFutures() {
        while (lowestUncompletedIndex - lowestUnremovedIndex > numberOfValuesToStore) {
            futures.remove(lowestUnremovedIndex);
            lowestUnremovedIndex++;
        }
    }

    /**
     * Make sure the next completion/cancellation index is valid (that is, it is greater or equal to the lowest
     * uncompleted index).
     *
     * @param index
     * 		the index that is about to be completed/cancelled
     */
    private void assertCompletionIndexIsValid(final long index) {
        if (index < lowestUncompletedIndex) {
            throw new IllegalStateException("futures must be completed in increasing order, expected index >= "
                    + lowestUncompletedIndex + ", provided index = " + index);
        }
    }

    /**
     * Complete/cancel the future for a given index
     *
     * @param index
     * 		the index of the future
     * @param buildNewFuture
     * 		a method called to build the future if it doesn't exist
     * @param updateExistingFuture
     * 		a method called to update the future if it does exist
     */
    private void completeFuture(
            final long index,
            final Supplier<StandardFuture<T>> buildNewFuture,
            final Consumer<StandardFuture<T>> updateExistingFuture) {

        assertCompletionIndexIsValid(index);
        fillGaps(index);

        final StandardFuture<T> future = futures.get(lowestUncompletedIndex);
        if (future == null) {
            futures.put(index, buildNewFuture.get());
        } else {
            updateExistingFuture.accept(future);
        }

        lowestUncompletedIndex++;

        pruneOldFutures();
    }

    /**
     * Signals the completion of the future. Intended for internal use by the background process
     * completing the work. This method will have no effect if the future is already completed or cancelled.
     *
     * Futures must be handled in a strictly increasing order. Skipping indices causes the gap handler to be called.
     *
     * @param index
     * 		the index of the future to complete
     * @param value
     * 		the value of the future
     * @throws IllegalStateException
     * 		if the futures are completed in an incorrect order
     */
    public synchronized void complete(final long index, final T value) {
        completeFuture(index, () -> new StandardFuture<>(value), future -> future.complete(value));
        mostRecentValueCompleted = value;
    }

    /**
     * Signals the cancellation of the future. Intended for internal use by the background process
     * completing the work. This method will have no effect if the future is already completed or cancelled.
     *
     * Futures must be handled in order without skipping any indices.
     *
     * @param index
     * 		the index of the future to cancel
     * @throws IllegalStateException
     * 		if the futures are completed in an incorrect order
     */
    public synchronized void cancel(final long index) {
        completeFuture(
                index,
                () -> {
                    final StandardFuture<T> future = new StandardFuture<>();
                    future.cancel();
                    return future;
                },
                StandardFuture::cancel);
    }

    /**
     * Signals the cancellation of the future with an associated exception. Intended for internal use by the background
     * process completing the work. This method will have no effect if the future is already completed or cancelled.
     *
     * Futures must be handled in order without skipping any indices.
     *
     * @param index
     * 		the index of the future to cancel
     * @param error
     * 		an exception that caused computation of the future to fail
     * @throws IllegalStateException
     * 		if the futures are completed in an incorrect order
     */
    public synchronized void cancelWithError(final long index, final Throwable error) {
        completeFuture(
                index,
                () -> {
                    final StandardFuture<T> future = new StandardFuture<>();
                    future.cancelWithError(error);
                    return future;
                },
                future -> future.cancelWithError(error));
    }

    /**
     * This method causes the next index that is expected to be completed to jump to a higher value.
     * All futures from uncompleted indices prior to the next index are cancelled.
     *
     * @param nextIndex
     * 		the next index expected to be completed after the reset.
     * @param initialValues
     * 		a method that is used to generate values that come before the next index
     */
    public synchronized void fastForwardIndex(final long nextIndex, final LongFunction<T> initialValues) {
        // Cancel and purge really old futures
        final List<Long> futuresToRemove = new LinkedList<>();
        futures.forEach((final Long round, final StandardFuture<T> future) -> {
            if (round < nextIndex - numberOfValuesToStore) {
                future.cancel();
                futuresToRemove.add(round);
            }
        });
        futuresToRemove.forEach(futures::remove);

        // Generate futures for values that come before nextIndex, skipping indices that already have values.
        for (long index = nextIndex - numberOfValuesToStore; index < nextIndex; index++) {
            final StandardFuture<T> existingFuture = futures.get(index);
            final T initialValue = initialValues.apply(index);
            if (existingFuture == null) {
                futures.put(index, new StandardFuture<>(initialValue));
                mostRecentValueCompleted = initialValue;
            } else {
                if (!existingFuture.isDone() && !existingFuture.isCancelled()) {
                    existingFuture.complete(initialValue);
                    mostRecentValueCompleted = initialValue;
                }
            }
        }

        lowestUncompletedIndex = nextIndex;
        lowestUnremovedIndex = nextIndex - numberOfValuesToStore;
    }
}
