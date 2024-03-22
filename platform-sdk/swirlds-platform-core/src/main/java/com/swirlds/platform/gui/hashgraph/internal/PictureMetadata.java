/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.events.PlatformEvent;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.util.List;

/**
 * Metadata that is used to aid in drawing a {@code HashgraphPicture}
 */
public class PictureMetadata {
    /**
     * the gap between left side of screen and leftmost column
     * is marginFraction times the gap between columns (and similarly for right side)
     */
    private static final double MARGIN_FRACTION = 0.5;

    private final AddressBookMetadata addressBookMetadata;
    private final int ymax;
    private final int ymin;
    private final int width;
    private final double r;
    private final long minGen;
    private final long maxGen;

    public PictureMetadata(
            final FontMetrics fm,
            final Dimension pictureDimension,
            final AddressBookMetadata addressBookMetadata,
            final List<EventImpl> events) {
        this.addressBookMetadata = addressBookMetadata;
        final int fa = fm.getMaxAscent();
        final int fd = fm.getMaxDescent();
        final int textLineHeight = fa + fd;

        width = (int) pictureDimension.getWidth();

        // where to draw next in the window, and the font height
        final int height1 = 0; // text area at the top
        final int height2 = (int) (pictureDimension.getHeight() - height1); // the main display, below the text
        ymin = (int) Math.round(height1 + 0.025 * height2);
        ymax = (int) Math.round(height1 + 0.975 * height2) - textLineHeight;

        long minGenTmp = Long.MAX_VALUE;
        long maxGenTmp = Long.MIN_VALUE;
        for (final EventImpl event : events) {
            minGenTmp = Math.min(minGenTmp, event.getGeneration());
            maxGenTmp = Math.max(maxGenTmp, event.getGeneration());
        }
        maxGenTmp = Math.max(maxGenTmp, minGenTmp + 2);
        minGen = minGenTmp;
        maxGen = maxGenTmp;

        final int n = addressBookMetadata.getNumMembers() + 1;
        final double gens = maxGen - minGen;
        final double dy = (ymax - ymin) * (gens - 1) / gens;
        r = Math.min(width / n / 4, dy / gens / 2);
    }

    /**
     * @return the gap between columns
     */
    public int getGapBetweenColumns() {
        return (int) (width / (addressBookMetadata.getNumColumns() - 1 + 2 * MARGIN_FRACTION));
    }

    /**
     * @return gap between leftmost column and left edge (and similar on right)
     */
    public int getSideGap() {
        return (int) (getGapBetweenColumns() * MARGIN_FRACTION);
    }

    /** find x position on the screen for event e2 which has an other-parent of e1 (or null if none) */
    public int xpos(final PlatformEvent e1, final PlatformEvent e2) {
        // the gap between left side of screen and leftmost column
        // is marginFraction times the gap between columns (and similarly for right side)
        final double marginFraction = 0.5;
        // gap between columns
        final int betweenGap = (int) (width / (addressBookMetadata.getNumColumns() - 1 + 2 * marginFraction));
        // gap between leftmost column and left edge (and similar on right)
        final int sideGap = (int) (betweenGap * marginFraction);

        // find the column for e2 next to the column for e1
        return sideGap + addressBookMetadata.mems2col(e1, e2) * betweenGap;
    }

    /**
     * find y position on the screen for an event
     */
    public int ypos(final PlatformEvent event) {
        return (event == null) ? -100 : (int) (ymax - r * (1 + 2 * (event.getGeneration() - minGen)));
    }

    public double getR() {
        return r;
    }

    public int getYmax() {
        return ymax;
    }

    public int getYmin() {
        return ymin;
    }

    /**
     * @return the minimum generation being displayed
     */
    public long getMinGen() {
        return minGen;
    }
}
