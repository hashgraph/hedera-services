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

package com.swirlds.platform.test.grid;

import java.io.PrintWriter;
import java.util.Arrays;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * A class which defines a single result in GridGraph.
 *
 * @param <X>
 * 		the type of the index for the x-coordinate
 * @param <Y>
 * 		the type of the index for the y-coordinate
 */
public class GridGraphResult<X extends Comparable<X>, Y extends Comparable<Y>>
        implements Comparable<GridGraphResult<X, Y>>, GridRenderer {

    /**
     * The default value string format
     */
    public static final String DEFAULT_FORMAT_SPEC = "%s";

    /**
     * Mnemonic for the indicated character
     */
    public static final char SPACE = ' ';

    /**
     * Mnemonic for the indicated character
     */
    public static final char TAB = '\t';

    /**
     * Mnemonic for the indicated character
     */
    public static final char DASH = '-';

    /**
     * Mnemonic for the indicated character
     */
    public static final char UNDERSCORE = '_';

    /**
     * Mnemonic for the indicated character
     */
    public static final char ASTERISK = '*';

    /**
     * Mnemonic for the indicated character
     */
    public static final char PIPE = '|';

    /**
     * Mnemonic for the indicated character
     */
    public static final char LESS_THAN = '<';

    /**
     * Mnemonic for the indicated character
     */
    public static final char GREATER_THAN = '>';

    /**
     * The x-coordinate of this results
     */
    private final X x;

    /**
     * The y-coordinate of this results
     */
    private final Y y;

    /**
     * The value object to be string-ized
     */
    private final Object value;

    /**
     * The format specifier for string-izing the value
     */
    private final String formatSpec;

    /**
     * Construct a result instance
     *
     * @param x
     * 		the x-coordinate
     * @param y
     * 		the y-coordinate
     * @param value
     * 		the value with the given coordinates
     */
    protected GridGraphResult(final X x, final Y y, final Object value) {
        this(x, y, value, DEFAULT_FORMAT_SPEC);
    }

    /**
     * Construct a result instance
     *
     * @param x
     * 		the x-coordinate
     * @param y
     * 		the y-coordinate
     * @param value
     * 		the value with the given coordinates
     * @param formatSpec
     * 		the string format specifier
     */
    protected GridGraphResult(final X x, final Y y, final Object value, final String formatSpec) {
        this.x = x;
        this.y = y;
        this.value = value;
        this.formatSpec = (formatSpec == null || formatSpec.isBlank()) ? DEFAULT_FORMAT_SPEC : formatSpec;
    }

    /**
     * Get the x-coordinate
     *
     * @return the x-coordinate
     */
    public X getX() {
        return x;
    }

    /**
     * Get the y-coordinate
     *
     * @return the y-coordinate
     */
    public Y getY() {
        return y;
    }

    /**
     * Get the value object at this result's coordinates
     *
     * @return the value
     */
    public Object getValue() {
        return value;
    }

    @Override
    public void render(final PrintWriter writer) {
        if (value == null) {
            writer.printf(formatSpec, SPACE);
            return;
        }

        writer.print(String.format(formatSpec, String.valueOf(value)));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final GridGraphResult<?, ?> that = (GridGraphResult<?, ?>) o;

        return new EqualsBuilder()
                .append(getX(), that.getX())
                .append(getY(), that.getY())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(getX()).append(getY()).toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(final GridGraphResult<X, Y> o) {
        if (this == o) {
            return 0;
        }

        if (o == null || getClass() != o.getClass()) {
            return 1;
        }

        return new CompareToBuilder()
                .append(getX(), o.getX())
                .append(getY(), o.getY())
                .build();
    }

    /**
     * A utility to create a string by repeating a character
     *
     * @param c
     * 		the character
     * @param count
     * 		the number of repetitions of the character
     * @return the resulting string
     */
    protected static String repeatedChar(final char c, final int count) {
        final char[] buffer = new char[count];
        Arrays.fill(buffer, c);

        return new String(buffer);
    }
}
