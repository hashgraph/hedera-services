/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.yahcli.commands.signedstate.evminfo;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents a range of integers
 *
 * <p>A range of integers specified by inclusive index at the low end, exclusive at the high end,
 * thus an empty range is from == to. (There is no check that this range is "valid", i.e., 0 <=
 * from, from <= to.)
 *
 * @param from - inclusive
 * @param to - exclusive
 * @param <T> - just used for typing purposes, because we're using ranges of bytecodes and lines
 */
record Range<T>(int from, int to) {

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

    @Override
    public String toString() {
        return String.format("Range[from: %04X; to: %04X]", from, to);
    }
}
