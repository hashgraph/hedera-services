/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.formatting;

import static com.swirlds.common.formatting.StringFormattingUtils.repeatedChar;
import static com.swirlds.common.formatting.TextEffect.getPrintableTextLength;

/**
 * This class can be used to align text.
 */
public enum HorizontalAlignment {
    ALIGNED_LEFT,
    ALIGNED_RIGHT,
    ALIGNED_CENTER;

    public static final char DEFAULT_PADDING = ' ';

    /**
     * Apply padding with the appropriate alignment.
     *
     * @param text
     * 		the text
     * @param width
     * 		the desired width of the resulting string. If shorter than the text then no padding is added.
     * @return a padded string with the appropriate alignment
     */
    public String pad(final String text, final int width) {
        final StringBuilder sb = new StringBuilder();
        pad(sb, text, DEFAULT_PADDING, width);
        return sb.toString();
    }

    /**
     * Apply padding with the appropriate alignment and write the result to a string builder.
     *
     * @param sb
     * 		the string builder to write to
     * @param text
     * 		the text
     * @param width
     * 		the desired width of the resulting string. If shorter than the text then no padding is added.
     */
    public void pad(final StringBuilder sb, final String text, final int width) {
        pad(sb, text, DEFAULT_PADDING, width);
    }

    /**
     * Apply padding with the appropriate alignment.
     *
     * @param text
     * 		the text
     * @param padding
     * 		the padding character
     * @param width
     * 		the desired width of the resulting string. If shorter than the text then no padding is added.
     * @return a padded string with the appropriate alignment
     */
    public String pad(final String text, final char padding, final int width) {
        final StringBuilder sb = new StringBuilder();
        pad(sb, text, padding, width);
        return sb.toString();
    }

    /**
     * Apply padding with the appropriate alignment and write the result to a string builder.
     *
     * @param sb
     * 		the string builder to write to
     * @param text
     * 		the text
     * @param padding
     * 		the padding character
     * @param width
     * 		the desired width of the resulting string. If shorter than the text then no padding is added.
     */
    public void pad(final StringBuilder sb, final String text, final char padding, final int width) {
        final int textLength = getPrintableTextLength(text);
        if (textLength >= width) {
            sb.append(text);
            return;
        }

        switch (this) {
            case ALIGNED_LEFT -> sb.append(text).append(repeatedChar(padding, width - textLength));
            case ALIGNED_RIGHT -> sb.append(repeatedChar(padding, width - textLength))
                    .append(text);
            case ALIGNED_CENTER -> {
                final int leftPadding = (width - textLength) / 2;
                final int rightPadding = width - textLength - leftPadding;

                sb.append(repeatedChar(padding, leftPadding)).append(text).append(repeatedChar(padding, rightPadding));
            }
        }
    }
}
