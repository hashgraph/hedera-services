package com.swirlds.platform.gui.hashgraph.internal;

import com.swirlds.platform.internal.EventImpl;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class EventSelector implements MouseListener {
    private EventImpl selectedEvent = null;
    private final List<EventImpl> stronglySeen = new ArrayList<>();

    private PictureMetadata metadata = null;
    private List<EventImpl> eventsInPicture = List.of();

    public void setMetadata(final PictureMetadata metadata) {
        this.metadata = metadata;
    }

    public void setEventsInPicture(final List<EventImpl> eventsInPicture) {
        this.eventsInPicture = eventsInPicture;
    }

    public boolean isSelected(final EventImpl event) {
        return event == selectedEvent;
    }

    public boolean isStronglySeen(final EventImpl event) {
        return stronglySeen.stream().anyMatch(e->e==event);
    }

    @Override
    public void mouseClicked(final MouseEvent me) {
        if(metadata == null){
            return;
        }
        final int xClicked = me.getX();
        final int yClicked = me.getY();
        final int d = metadata.getD();


        for (final EventImpl e : eventsInPicture) {
            final int xEvent = metadata.xpos(null, e);
            final int yEvent = metadata.ypos(e);
            if(xClicked > xEvent
                    && xClicked < xEvent + d
                    && yClicked > yEvent
                    && yClicked < yEvent + d){
                stronglySeen.clear();
                if(selectedEvent == e){
                    selectedEvent = null;
                }else {
                    selectedEvent = e;
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
}
