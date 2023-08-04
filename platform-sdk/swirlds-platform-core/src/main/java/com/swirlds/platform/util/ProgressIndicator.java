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

package com.swirlds.platform.util;

import static com.swirlds.common.formatting.TextEffect.BLUE;
import static com.swirlds.common.formatting.TextEffect.BRIGHT_BLUE;
import static com.swirlds.common.formatting.TextEffect.BRIGHT_CYAN;
import static com.swirlds.common.formatting.TextEffect.BRIGHT_GREEN;
import static com.swirlds.common.formatting.TextEffect.BRIGHT_PURPLE;
import static com.swirlds.common.formatting.TextEffect.BRIGHT_RED;
import static com.swirlds.common.formatting.TextEffect.BRIGHT_WHITE;
import static com.swirlds.common.formatting.TextEffect.BRIGHT_YELLOW;
import static com.swirlds.common.formatting.TextEffect.CYAN;
import static com.swirlds.common.formatting.TextEffect.GRAY;
import static com.swirlds.common.formatting.TextEffect.GREEN;
import static com.swirlds.common.formatting.TextEffect.PURPLE;
import static com.swirlds.common.formatting.TextEffect.RED;
import static com.swirlds.common.formatting.TextEffect.WHITE;
import static com.swirlds.common.formatting.TextEffect.YELLOW;

import com.swirlds.common.formatting.TextEffect;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A tool used to convey progress to the user.
 */
public class ProgressIndicator {

    private static final String[] LOADING_CHARACTERS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final int CHARACTERS_PER_LINE = LOADING_CHARACTERS.length * 10;
    private static TextEffect[] COLORS = {
            RED,
            GREEN,
            YELLOW,
            BLUE,
            PURPLE,
            CYAN,
            WHITE,
            GRAY,
            BRIGHT_RED,
            BRIGHT_GREEN,
            BRIGHT_YELLOW,
            BRIGHT_BLUE,
            BRIGHT_PURPLE,
            BRIGHT_CYAN,
            BRIGHT_WHITE};

    private int count = 0;
    private boolean colorEnabled = true;

    public ProgressIndicator() {}

    /**
     * Enable or disable color.
     *
     * @param colorEnabled true to enable color, false to disable
     */
    public synchronized void setColorEnabled(final boolean colorEnabled) {
        this.colorEnabled = colorEnabled;
    }

    /**
     * Increment the progress indicator.
     */
    public synchronized void increment() {
        final int index = count % LOADING_CHARACTERS.length;

        final String string;

        if (colorEnabled) {
            string = COLORS[count % COLORS.length].apply(LOADING_CHARACTERS[index]);
        } else {
            string = LOADING_CHARACTERS[index];
        }
        System.out.print(string);

        count++;
        if (count % CHARACTERS_PER_LINE == 0) {
            System.out.println();
        } else {
            System.out.flush();
        }
    }

    /**
     * Write a message to the console. Resets the progress indicator.
     *
     * @param message the message to write
     */
    public synchronized void writeMessage(@NonNull final String message) {
        System.out.println("\n" + message);
        count = 0;
    }
}
