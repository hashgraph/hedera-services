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

package com.swirlds.common.utility;

/**
 * Contains utility methods for comparing values.
 */
public final class CompareTo {

    private CompareTo() {}

    /**
     * Compare two values. Syntactic sugar for {@link Comparable#compareTo(Object)}.
     *
     * @param a
     * 		a value
     * @param b
     * 		a value
     * @param <A>
     * 		the type of value a
     * @param <B>
     * 		the type of value b
     * @return a &lt; b
     */
    public static <A extends Comparable<B>, B> boolean isLessThan(final A a, final B b) {
        return a.compareTo(b) < 0;
    }

    /**
     * Compare two values. Syntactic sugar for {@link Comparable#compareTo(Object)}.
     *
     * @param a
     * 		a value
     * @param b
     * 		a value
     * @param <A>
     * 		the type of value a
     * @param <B>
     * 		the type of value b
     * @return a &lt;= b
     */
    public static <A extends Comparable<B>, B> boolean isLessThanOrEqualTo(final A a, final B b) {
        return a.compareTo(b) <= 0;
    }

    /**
     * Compare two values. Syntactic sugar for {@link Comparable#compareTo(Object)}.
     *
     * @param a
     * 		a value
     * @param b
     * 		a value
     * @param <A>
     * 		the type of value a
     * @param <B>
     * 		the type of value b
     * @return a &gt; b
     */
    public static <A extends Comparable<B>, B> boolean isGreaterThan(final A a, final B b) {
        return a.compareTo(b) > 0;
    }

    /**
     * Compare two values. Syntactic sugar for {@link Comparable#compareTo(Object)}.
     *
     * @param a
     * 		a value
     * @param b
     * 		a value
     * @param <A>
     * 		the type of value a
     * @param <B>
     * 		the type of value b
     * @return a &gt;= b
     */
    public static <A extends Comparable<B>, B> boolean isGreaterThanOrEqualTo(final A a, final B b) {
        return a.compareTo(b) >= 0;
    }

    /**
     * Return the maximum of two values. If the values are equal, returns the first value.
     *
     * @param a
     * 		a value
     * @param b
     * 		a value
     * @param <T>
     * 		the type of the values
     * @return the maximum value, or the first value if equal
     */
    public static <T extends Comparable<T>> T max(final T a, final T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    /**
     * Return the minimum of two values. If the values are equal, returns the first value.
     *
     * @param a
     * 		a value
     * @param b
     * 		a value
     * @param <T>
     * 		the type of the values
     * @return the minimum value, or the first value if equal
     */
    public static <T extends Comparable<T>> T min(final T a, final T b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
