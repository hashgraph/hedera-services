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

package com.swirlds.cli.utility;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * ANSI and HTML color codes
 */
public enum Color {
    RED("91", "red"),
    TEAL("96", "teal"),
    YELLOW("93", "yellow"),
    GREEN("92", "green"),
    BRIGHT_BLUE("94", "bright-blue"),
    GRAY("90", "gray"),
    PURPLE("95", "purple"),
    WHITE("37", "white"),
    BRIGHT_WHITE("97", "bright-white");

    /**
     * ANSI code representing this color
     */
    private final String ansiCode;

    /**
     * HTML code representing this color
     */
    private final String htmlCode;

    /**
     * Constructor
     *
     * @param ansiCode the ANSI color code
     * @param htmlCode the HTML color code
     */
    Color(@NonNull final String ansiCode, @NonNull final String htmlCode) {
        this.ansiCode = ansiCode;
        this.htmlCode = htmlCode;
    }

    /**
     * Get the ANSI code for this color
     *
     * @return the ANSI code for this color
     */
    public String getAnsiCode() {
        return ansiCode;
    }

    /**
     * Get the HTML code for this color
     *
     * @return the HTML code for this color
     */
    public String getHtmlCode() {
        return htmlCode;
    }
}
