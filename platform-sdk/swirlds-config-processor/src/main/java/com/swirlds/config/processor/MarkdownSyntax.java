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

package com.swirlds.config.processor;

public final class MarkdownSyntax {

    public static final String H2_PREFIX = "## ";
    public static final String BOLD_MARKER = "**";

    public static final String CODE_MARKER = "`";

    public static final String NEWLINE = "\n\n";

    public static final String DEFAULT_VALUE = BOLD_MARKER + "default value:" + BOLD_MARKER + " ";

    public static final String DESCRIPTION = BOLD_MARKER + "description:" + BOLD_MARKER + " ";

    public static final String RECORD = BOLD_MARKER + "record:" + BOLD_MARKER + " ";

    public static final String TYPE = BOLD_MARKER + "type:" + BOLD_MARKER + " ";
    public static final String NO_DEFAULT_VALUE = BOLD_MARKER + "no default value" + BOLD_MARKER;
    public static final String DEFAULT_VALUE_IS_NULL =
            BOLD_MARKER + "default value is " + CODE_MARKER + "null" + CODE_MARKER + BOLD_MARKER;

    /**
     * Private constructor to prevent instantiation.
     */
    private MarkdownSyntax() {}

    /**
     * Returns the given text as code in markdown.
     *
     * @param text The text to return as code in markdown.
     * @return The given text as code in markdown.
     */
    public static String asCode(final String text) {
        return CODE_MARKER + text + CODE_MARKER;
    }
}
