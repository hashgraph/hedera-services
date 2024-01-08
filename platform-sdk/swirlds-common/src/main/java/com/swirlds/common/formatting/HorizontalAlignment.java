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

package com.swirlds.common.formatting;

import static com.swirlds.common.formatting.StringFormattingUtils.repeatedChar;
import static com.swirlds.common.formatting.TextEffect.getPrintableTextLength;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class can be used to align text.
 */
public enum HorizontalAlignment {
    ALIGNED_LEFT,
    ALIGNED_RIGHT,
    ALIGNED_CENTER;

    /**
     * Apply padding with the appropriate alignment.
     *
     * @param text            the text
     * @param padding         the padding character
     * @param width           the desired width of the resulting string. If shorter than the text then no padding is
     *                        added.
     * @param trailingPadding if true padding may be added to the end of the string, otherwise is not added to the end
     *                        of the string
     * @return a padded string with the appropriate alignment
     */
    @NonNull
    public String pad(@NonNull final String text, final char padding, final int width, final boolean trailingPadding) {

        final StringBuilder sb = new StringBuilder();
        pad(sb, text, padding, width, trailingPadding);
        return sb.toString();
    }

    /**
     * Apply padding with the appropriate alignment and write the result to a string builder.
     *
     * @param sb              the string builder to write to
     * @param text            the text
     * @param padding         the padding character
     * @param width           the desired width of the resulting string. If shorter than the text then no padding is
     *                        added.
     * @param trailingPadding if true padding may be added to the end of the string, otherwise is not added to the end
     *                        of the string
     */
    public void pad(
            @NonNull final StringBuilder sb,
            @NonNull final String text,
            final char padding,
            final int width,
            final boolean trailingPadding) {

        final int textLength = getPrintableTextLength(text);
        if (textLength >= width) {
            sb.append(text);
            return;
        }

        switch (this) {
            case ALIGNED_LEFT -> {
                sb.append(text);
                if (trailingPadding) {
                    sb.append(repeatedChar(padding, width - textLength));
                }
            }
            case ALIGNED_RIGHT -> sb.append(repeatedChar(padding, width - textLength))
                    .append(text);
            case ALIGNED_CENTER -> {
                final int leftPadding = (width - textLength) / 2;
                sb.append(repeatedChar(padding, leftPadding)).append(text);
                if (trailingPadding) {
                    final int rightPadding = width - textLength - leftPadding;
                    sb.append(repeatedChar(padding, rightPadding));
                }
            }
        }
    }
}
