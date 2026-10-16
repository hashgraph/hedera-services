// SPDX-License-Identifier: Apache-2.0
package org.hiero.otter.fixtures.network.simulation;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * What is in flight on one directed connection, handed out in order of arrival time whatever order it was added in.
 * Items that arrive at the same time are handed out in the order they were added.
 *
 * <p>This class is not safe to use from more than one thread.
 *
 * @param <T> what is held in flight
 */
final class InFlightQueue<T extends InTransit> {

    /**
     * An item together with the position it was added in, which breaks ties between equal arrival times.
     */
    private record Entry<T extends InTransit>(@NonNull T item, long sequence) {}

    private final PriorityQueue<Entry<T>> queue = new PriorityQueue<>(
            Comparator.<Entry<T>, Instant>comparing(entry -> entry.item().arrivalTime())
                    .thenComparingLong(Entry::sequence));

    private long nextSequence = 0;

    /**
     * Adds something to be delivered in the future.
     *
     * @param item the item to add
     */
    void add(@NonNull final T item) {
        queue.add(new Entry<>(item, nextSequence++));
    }

    /**
     * @return the next item to be delivered, or {@code null} if the queue is empty
     */
    @Nullable
    T peek() {
        final Entry<T> head = queue.peek();
        return head == null ? null : head.item();
    }

    /**
     * Removes the next item to be delivered.
     *
     * @return the removed item, or {@code null} if the queue is empty
     */
    @Nullable
    T poll() {
        final Entry<T> head = queue.poll();
        return head == null ? null : head.item();
    }

    /**
     * Discards everything in the queue.
     */
    void clear() {
        queue.clear();
    }
}
