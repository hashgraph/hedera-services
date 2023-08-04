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

package com.swirlds.platform.gui.hashgraph.internal;

import com.swirlds.common.system.events.PlatformEvent;
import java.awt.Color;
import java.awt.Font;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Various static utils for the {@link com.swirlds.platform.gui.hashgraph.HashgraphGui}
 */
public final class HashgraphGuiUtils {
    private HashgraphGuiUtils() {}

    public static final int DEFAULT_GENERATIONS_TO_DISPLAY = 25;
    /** outline of labels */
    public static final Color LABEL_OUTLINE = new Color(255, 255, 255);
    /** unknown-fame witness, non-consensus */
    public static final Color LIGHT_RED = new Color(192, 0, 0);
    /** unknown-fame witness, consensus (which can't happen) */
    public static final Color DARK_RED = new Color(128, 0, 0);
    /** unknown-fame witness, consensus */
    public static final Color LIGHT_GREEN = new Color(0, 192, 0);
    /** famous witness, non-consensus */
    public static final Color DARK_GREEN = new Color(0, 128, 0);
    /** famous witness, consensus */
    public static final Color LIGHT_BLUE = new Color(0, 0, 192);
    /** non-famous witness, non-consensus */
    public static final Color DARK_BLUE = new Color(0, 0, 128);
    /** non-witness witness, consensus */
    public static final Color LIGHT_GRAY = new Color(160, 160, 160);
    /** non-witness, non-consensus */
    public static final Color DARK_GRAY = new Color(0, 0, 0);

    public static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("H:m:s.n").withLocale(Locale.US).withZone(ZoneId.systemDefault());
    public static final Font HASHGRAPH_PICTURE_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    /**
     * return the member number for column x, if there are m members. This is set up so each member appears
     * in multiple columns, and for any two members there will be exactly one location where they have
     * adjacent columns.
     * <p>
     * The pattern used comes from a Eulerian cycle on the complete graph of m members, formed by combining
     * floor(m/2) disjoint Eulerian paths on the complete graph of m-1 members.
     * <p>
     * This method assumes an odd number of members. If there are an even number of members, then assume
     * there is one extra member, use this method, then delete the columns of the fictitious member, and
     * combine those columns on either side of each deleted one.
     *
     * @param m
     * 		the number of members (must be odd)
     * @param x
     * 		the column (from 0 to 1+m*(m-1)/2)
     * @return the member number (from 0 to m-1)
     */
    public static int col2mem(int m, final int x) {
        m = (m / 2) * 2 + 1; // if m is even, round up to the nearest odd
        final int i = (x / m) % (m / 2); // the ith Eulerian path on the complete graph of m-1 vertices
        final int j = x % m; // position along that ith path

        if (j == m - 1) {
            return m - 1; // add the mth vertex after each Eulerian path to get a Eulerian cycle
        }

        if ((j % 2) == 0) {
            return i + j / 2; // in a given path, every other vertex counts up
        }

        return (m - 2 + i - (j - 1) / 2) % (m - 1); // and every other vertex counts down
    }

    /**
     * Return the color for an event based on calculations in the consensus algorithm A non-witness is gray,
     * and a witness has a color of green (famous), blue (not famous) or red (undecided fame). When the
     * event becomes part of the consensus, its color becomes darker.
     *
     * @param event
     * 		the event to color
     * @return its color
     */
    public static Color eventColor(final PlatformEvent event, final HashgraphPictureOptions options) {
        if (options.simpleColors()) { // if checkbox checked
            return event.isConsensus() ? LIGHT_BLUE : LIGHT_GREEN;
        }
        if (!event.isWitness()) {
            return event.isConsensus() ? DARK_GRAY : LIGHT_GRAY;
        }
        if (!event.isFameDecided()) {
            return event.isConsensus() ? DARK_RED : LIGHT_RED;
        }
        if (event.isFamous()) {
            return event.isConsensus() ? DARK_GREEN : LIGHT_GREEN;
        }
        return event.isConsensus() ? DARK_BLUE : LIGHT_BLUE;
    }
}
