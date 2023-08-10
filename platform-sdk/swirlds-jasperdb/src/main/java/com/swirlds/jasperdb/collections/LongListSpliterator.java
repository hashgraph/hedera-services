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

package com.swirlds.jasperdb.collections;

import java.util.Comparator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.LongConsumer;

/**
 * A Spliterator.OfLong based on long array spliterator.
 */
final class LongListSpliterator implements Spliterator.OfLong {
    private final LongList longList;

    /**
     * One past the last usable index.
     */
    private final long fence;

    /**
     * The current index, modified on advance and split.
     */
    private long index;

    /**
     * Creates a spliterator covering the given OffHeapLongList
     *
     * @param longList
     * 		the long list
     */
    public LongListSpliterator(final LongList longList) {
        this(longList, 0, longList.size());
    }

    /**
     * Creates a spliterator covering the given OffHeapLongList and range
     *
     * @param longList
     * 		the long list
     * @param origin
     * 		the least index (inclusive) to cover
     * @param fence
     * 		one past the greatest index to cover
     */
    public LongListSpliterator(final LongList longList, final long origin, final long fence) {
        this.longList = longList;
        this.index = origin;
        this.fence = fence;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OfLong trySplit() {
        final long lo = index;
        final long mid = (lo + fence) >>> 1;
        if (lo >= mid) {
            return null;
        } else {
            index = mid;
            return new LongListSpliterator(longList, lo, index);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void forEachRemaining(final LongConsumer action) {
        Objects.requireNonNull(action);

        LongList a;
        long i;
        long hi; // hoist accesses and checks from loop
        if ((a = longList).size() >= (hi = fence) && (i = index) >= 0 && i < (index = hi)) {
            do {
                action.accept(a.get(i, 0));
            } while (++i < hi);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean tryAdvance(LongConsumer action) {
        Objects.requireNonNull(action);
        if (index >= 0 && index < fence) {
            action.accept(longList.get(index++, 0));
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long estimateSize() {
        return (fence - index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int characteristics() {
        return Spliterator.SIZED | Spliterator.SUBSIZED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Comparator<? super Long> getComparator() {
        throw new IllegalStateException();
    }
}
