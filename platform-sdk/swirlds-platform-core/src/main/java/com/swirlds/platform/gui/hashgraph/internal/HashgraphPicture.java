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

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.platform.gui.hashgraph.HashgraphGuiConstants.HASHGRAPH_PICTURE_FONT;

import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.gui.hashgraph.HashgraphGuiConstants;
import com.swirlds.platform.gui.hashgraph.HashgraphGuiSource;
import com.swirlds.platform.gui.hashgraph.HashgraphPictureOptions;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.address.AddressBook;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.ItemEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.Serial;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import javax.swing.JPanel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This panel has the hashgraph picture, and appears in the window to the right of all the settings.
 */
public class HashgraphPicture extends JPanel {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final Logger logger = LogManager.getLogger(HashgraphPicture.class);
    private final HashgraphGuiSource hashgraphSource;
    private final HashgraphPictureOptions options;
    private PictureMetadata pictureMetadata;
    /** used to store an image when the freeze checkbox is checked */
    private BufferedImage image = null;

    private AddressBookMetadata nonExpandedMetadata;
    private AddressBookMetadata expandedMetadata;
    private final List<Co> eventCoordinates = new ArrayList<>();
    private int d = 0;
    private EventImpl selectedEvent = null;
    private final List<EventImpl> stronglySeen = new ArrayList<>();

    public HashgraphPicture(final HashgraphGuiSource hashgraphSource, final HashgraphPictureOptions options) {
        this.hashgraphSource = hashgraphSource;
        this.options = options;
        createMetadata();
        this.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(final MouseEvent e) {
                final int xClicked = e.getX();
                final int yClicked = e.getY();
                for (final Co ec : eventCoordinates) {
                    final int xEvent = ec.x();
                    final int yEvent = ec.y();
                    if(xClicked > xEvent
                            && xClicked < xEvent + d
                            && yClicked > yEvent
                            && yClicked < yEvent + d){
                        stronglySeen.clear();
                        if(selectedEvent == ec.event()){
                            selectedEvent = null;
                        }else {
                            selectedEvent = ec.event();
                            if(selectedEvent.getStronglySeeP() != null){
                                Arrays.stream(selectedEvent.getStronglySeeP())
                                        .filter(Objects::nonNull)
                                        .forEach(stronglySeen::add);
                            }
                        }
                    }
                }
            }

            @Override
            public void mousePressed(final MouseEvent e) {

            }

            @Override
            public void mouseReleased(final MouseEvent e) {

            }

            @Override
            public void mouseEntered(final MouseEvent e) {

            }

            @Override
            public void mouseExited(final MouseEvent e) {

            }
        });
    }

    private void createMetadata() {
        if ((expandedMetadata == null || nonExpandedMetadata == null) && hashgraphSource.isReady()) {
            expandedMetadata = new AddressBookMetadata(hashgraphSource.getAddressBook(), true);
            nonExpandedMetadata = new AddressBookMetadata(hashgraphSource.getAddressBook(), false);
        }
    }

    @Override
    public void paintComponent(final Graphics g) {
        super.paintComponent(g);
        try {
            if (image != null) {
                g.drawImage(image, 0, 0, null);
                return;
            }
            if (!hashgraphSource.isReady()) {
                return;
            }
            createMetadata();
            g.setFont(HASHGRAPH_PICTURE_FONT);
            final FontMetrics fm = g.getFontMetrics();
            final AddressBook addressBook = hashgraphSource.getAddressBook();
            final int numMem = addressBook.getSize();
            final AddressBookMetadata currentMetadata = options.isExpanded() ? expandedMetadata : nonExpandedMetadata;

            List<EventImpl> events;
            if (options.displayLatestEvents()) {
                final long startGen = Math.max(
                        hashgraphSource.getMaxGeneration() - options.getNumGenerationsDisplay()+1,
                        GraphGenerations.FIRST_GENERATION);
                options.setStartGeneration(startGen);
                events = hashgraphSource.getEvents(startGen, options.getNumGenerationsDisplay());
            } else {
                events = hashgraphSource.getEvents(options.getStartGeneration(), options.getNumGenerationsDisplay());
            }
            // in case the state has events from creators that don't exist, don't show them
            if (events == null) { // in case a screen refresh happens before any events
                return;
            }
            events = events.stream()
                    .filter(e -> addressBook.contains(e.getCreatorId()))
                    .filter(e -> addressBook.getIndexOfNodeId(e.getCreatorId()) < numMem)
                    .toList();

            pictureMetadata = new PictureMetadata(fm, this.getSize(), currentMetadata, events);

            g.setColor(Color.BLACK);

            for (int i = 0; i < currentMetadata.getNumColumns(); i++) {
                final String name = currentMetadata.getName(i);

                // gap between columns
                final int betweenGap = pictureMetadata.getGapBetweenColumns();
                // gap between leftmost column and left edge (and similar on right)
                final int sideGap = pictureMetadata.getSideGap();
                final int x = sideGap + (i) * betweenGap;
                g.drawLine(x, pictureMetadata.getYmin(), x, pictureMetadata.getYmax());
                final Rectangle2D rect = fm.getStringBounds(name, g);
                g.drawString(
                        name, (int) (x - rect.getWidth() / 2), (int) (pictureMetadata.getYmax() + rect.getHeight()));
            }

            d = (int) (2 * pictureMetadata.getR());

            // for each event, draw 2 downward lines to its parents
            for (final EventImpl event : events) {
                drawLinksToParents(g, event);
            }

            eventCoordinates.clear();
            // for each event, draw its circle
            for (final EventImpl event : events) {
                drawEventCircle(g, event, options, d);
            }
        } catch (final Exception e) {
            logger.error(EXCEPTION.getMarker(), "error while painting", e);
        }
    }

    private void drawLinksToParents(final Graphics g, final EventImpl event) {
        g.setColor(HashgraphGuiUtils.eventColor(event, options));
        final EventImpl e1 = event.getSelfParent();
        EventImpl e2 = event.getOtherParent();
        final AddressBook addressBook = hashgraphSource.getAddressBook();
        if (e2 != null
                && (!addressBook.contains(e2.getCreatorId())
                        || addressBook.getIndexOfNodeId(e2.getCreatorId()) >= addressBook.getSize())) {
            // if the creator of the other parent has been removed,
            // treat it as if there is no other parent
            e2 = null;
        }
        if (e1 != null && e1.getGeneration() >= pictureMetadata.getMinGen()) {
            g.drawLine(
                    pictureMetadata.xpos(e2, event),
                    pictureMetadata.ypos(event),
                    pictureMetadata.xpos(e2, event),
                    pictureMetadata.ypos(e1));
        }
        if (e2 != null && e2.getGeneration() >= pictureMetadata.getMinGen()) {
            g.drawLine(
                    pictureMetadata.xpos(e2, event),
                    pictureMetadata.ypos(event),
                    pictureMetadata.xpos(event, e2),
                    pictureMetadata.ypos(e2));
        }
    }

    private record Co(EventImpl event, int x, int y) {
    }

    private void drawEventCircle(
            final Graphics g, final EventImpl event, final HashgraphPictureOptions options, final int d) {
        final FontMetrics fm = g.getFontMetrics();
        final int fa = fm.getMaxAscent();
        final int fd = fm.getMaxDescent();
        final EventImpl e2 = event.getOtherParent() != null
                        && hashgraphSource
                                .getAddressBook()
                                .contains(event.getOtherParent().getCreatorId())
                ? event.getOtherParent()
                : null;
        final Color color;
        if(event == selectedEvent) {
            color = Color.MAGENTA;
        }else if(stronglySeen.stream().anyMatch(e->e==event)){
            color = Color.CYAN;
        }else {
            color = HashgraphGuiUtils.eventColor(event, options);
        }
        g.setColor(color);

        final int xPos = pictureMetadata.xpos(e2, event) - d / 2;
        final int yPos = pictureMetadata.ypos(event) - d / 2;
        eventCoordinates.add(new Co(event, xPos, yPos));

        g.fillOval(xPos, yPos, d, d);
        g.setFont(g.getFont().deriveFont(Font.BOLD));

        String s = "";

        if (options.writeRoundCreated()) {
            s += " " + event.getRoundCreated();
        }
        if (options.writeRoundReceived() && event.getRoundReceived() > 0) {
            s += " " + event.getRoundReceived();
        }
        // if not consensus, then there's no order yet
        if (options.writeConsensusOrder() && event.isConsensus()) {
            s += " " + event.getConsensusOrder();
        }
        if (options.writeConsensusTimeStamp()) {
            final Instant t = event.getConsensusTimestamp();
            if (t != null) {
                s += " " + HashgraphGuiConstants.FORMATTER.format(t);
            }
        }
        if (options.writeGeneration()) {
            s += " " + event.getGeneration();
        }
        if (!s.isEmpty()) {
            final Rectangle2D rect = fm.getStringBounds(s, g);
            final int x = (int) (pictureMetadata.xpos(e2, event) - rect.getWidth() / 2. - fa / 4.);
            final int y = (int) (pictureMetadata.ypos(event) + rect.getHeight() / 2. - fd / 2);
            g.setColor(HashgraphGuiConstants.LABEL_OUTLINE);
            g.drawString(s, x - 1, y - 1);
            g.drawString(s, x + 1, y - 1);
            g.drawString(s, x - 1, y + 1);
            g.drawString(s, x + 1, y + 1);
            g.setColor(color);
            g.drawString(s, x, y);
        }
    }

    public void freezeChanged(final ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
            try { // capture a bitmap of "picture" from the screen
                image = (new Robot())
                        .createScreenCapture(new Rectangle(
                                this.getLocationOnScreen(),
                                this.getVisibleRect().getSize()));
                // to write the image to disk:
                // ImageIO.write(image, "jpg", new File("image.jpg"));
            } catch (final AWTException err) {
                // ignore exception
            }
        } else if (e.getStateChange() == ItemEvent.DESELECTED) {
            image = null; // erase the saved image, stop freezing
        }
    }
}
