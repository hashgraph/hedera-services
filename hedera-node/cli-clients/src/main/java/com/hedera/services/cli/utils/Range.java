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

package com.hedera.services.cli.utils;

import static com.google.common.collect.Range.closedOpen;

import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.TreeRangeSet;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Represents a range of integers
 *
 * <p>A range of integers specified by inclusive index at the low end, exclusive at the high end,
 * thus an empty range is from == to. (There is no check _on construction_ that this range is
 * "valid", i.e., 0 &#60;= from, from &#60;= to.)
 *
 * @param from - inclusive
 * @param to - exclusive
 * @param <T> - just used for typing purposes, because we're using ranges of bytecodes and lines
 */
public record Range<T>(int from, int to) {

    /** Get length of range */
    public int length() {
        return Math.max(0, to - from);
    }

    /** Returns an empty range */
    public static <T> @NonNull Range<T> empty() {
        return new Range<>(0, 0);
    }

    /** Predicate: is this range empty? */
    public boolean isEmpty() {
        return 0 == length();
    }

    /** Returns index _at_ this range's end (beware of empty) */
    public int last() {
        return isEmpty() ? 0 : to - 1;
    }

    /** Returns index _following_ this range's end (beware of empty) */
    public int onePastEnd() {
        return to;
    }

    /** Predicate returns true iff a range contains a point */
    public boolean contains(int point) {
        return point >= from && point < to;
    }

    /** Predicate returns true iff the given point is at one end or the other of the range */
    public boolean atAnEnd(int point) {
        return point == from || point == to - 1;
    }

    /** Predicate returns true iff the given point is _within_ the range (_not_ at either end) */
    public boolean properlyWithin(int point) {
        return contains(point) && !atAnEnd(point);
    }

    /**
     * Predicate returns true iff none of the ranges given _overlap_ (though they may be adjacent).
     *
     * <p>N.B.: Major limitation of this method is that it _ignores_ all empty ranges given in the
     * argument collection. Because the underlying Guava `RangeSet` does. This is fine w.r.t.
     * current uses of this method but may not match _your_ use case.
     *
     * <p>(SuppressWarnings is due to use of Guava's `RangeSet` which is marked `@Beta` (though it
     * has in fact been fairly stable for quite some time).
     */
    @SuppressWarnings("UnstableApiUsage")
    public static <T> boolean hasOverlappingRanges(@NonNull Collection<Range<T>> ranges) {
        Objects.requireNonNull(ranges);
        var set = TreeRangeSet.<Integer>create();
        for (final var r : ranges) {
            // Convert to Guava `Range`
            final var gr = closedOpen(r.from, r.to).canonical(DiscreteDomain.integers());
            if (set.intersects(gr)) return true;
            set.add(gr);
        }
        return false;
    }

    /** Validate a range is in proper format - throw if it isn't */
    public void validate() {
        if (from < 0) throw new IllegalArgumentException("from < 0");
        if (from > to) throw new IllegalArgumentException("from > to");
    }

    /** Test validity + canonicalize */
    public @NonNull Range<T> canonicalize() {
        validate();
        if (from == 0 && from == to) return this;
        if (from >= to) return new Range<>(0, 0);
        return this;
    }

    @Override
    @NonNull
    public String toString() {
        return "Range[0x%04X,0x%04X)".formatted(from, to);
    }

    private static final HexFormat hexer = HexFormat.of().withUpperCase().withPrefix("0x");

    public String toString(@NonNull final String type) {
        Objects.requireNonNull(type);
        return toString(type, 10);
    }

    public String toString(@NonNull final String type, int base) {
        Objects.requireNonNull(type);
        return "Range<%s>[%s,%s)"
                .formatted(
                        type,
                        base != 16 ? Integer.toString(from, base) : hexer.toHexDigits(from, 4),
                        base != 16 ? Integer.toString(to, base) : hexer.toHexDigits(to, 4));
    }
}
