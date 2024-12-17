/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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
import edu.umd.cs.findbugs.annotations.NonNull;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * This class is responsible for selecting events by clicking in the GUI.
 */
public class EventSelector implements MouseListener {
    private EventImpl selectedEvent = null;
    private final List<EventImpl> stronglySeen = new ArrayList<>();

    private PictureMetadata metadata = null;
    private List<EventImpl> eventsInPicture = List.of();

    /**
     * Set the metadata needed to locate the position of an event in the picture.
     *
     * @param metadata the metadata
     */
    public void setMetadata(@NonNull final PictureMetadata metadata) {
        Objects.requireNonNull(metadata);
        this.metadata = metadata;
    }

    /**
     * Set the events that are in the picture.
     *
     * @param eventsInPicture the events
     */
    public void setEventsInPicture(@NonNull final List<EventImpl> eventsInPicture) {
        Objects.requireNonNull(eventsInPicture);
        this.eventsInPicture = eventsInPicture;
    }

    /**
     * Checks if the supplied event is selected.
     *
     * @param event the event to check
     * @return true if the event is selected, false otherwise
     */
    public boolean isSelected(@NonNull final EventImpl event) {
        Objects.requireNonNull(event);
        return event == selectedEvent;
    }

    /**
     * Checks if the supplied event is strongly seen by the selected event.
     *
     * @param event the event to check
     * @return true if the event is strongly seen, false otherwise
     */
    public boolean isStronglySeen(@NonNull final EventImpl event) {
        Objects.requireNonNull(event);
        return stronglySeen.stream().anyMatch(e -> e == event);
    }

    @Override
    public void mouseClicked(final MouseEvent me) {
        if (metadata == null) {
            return;
        }
        final int xClicked = me.getX();
        final int yClicked = me.getY();
        final int d = metadata.getD();

        for (final EventImpl e : eventsInPicture) {
            final int xEvent = metadata.xpos(null, e);
            final int yEvent = metadata.ypos(e);
            if (xClicked > xEvent && xClicked < xEvent + d && yClicked > yEvent && yClicked < yEvent + d) {
                stronglySeen.clear();
                if (selectedEvent == e) {
                    selectedEvent = null;
                } else {
                    selectedEvent = e;
                    if (selectedEvent.getStronglySeeP() != null) {
                        Arrays.stream(selectedEvent.getStronglySeeP())
                                .filter(Objects::nonNull)
                                .forEach(stronglySeen::add);
                    }
                }
            }
        }
    }

    @Override
    public void mousePressed(final MouseEvent e) {}

    @Override
    public void mouseReleased(final MouseEvent e) {}

    @Override
    public void mouseEntered(final MouseEvent e) {}

    @Override
    public void mouseExited(final MouseEvent e) {}
}
