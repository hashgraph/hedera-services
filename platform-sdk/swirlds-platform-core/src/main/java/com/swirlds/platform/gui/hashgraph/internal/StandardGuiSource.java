// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.gui.hashgraph.internal;

import com.swirlds.platform.gui.GuiEventStorage;
import com.swirlds.platform.gui.hashgraph.HashgraphGuiSource;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;

/**
 * A {@link HashgraphGuiSource} that retrieves events from a stream of events
 */
public class StandardGuiSource implements HashgraphGuiSource {

    private final AddressBook addressBook;
    private final GuiEventStorage eventStorage;

    /**
     * Constructor
     *
     * @param eventStorage stores information about events
     */
    public StandardGuiSource(@NonNull final AddressBook addressBook, @NonNull final GuiEventStorage eventStorage) {

        this.addressBook = Objects.requireNonNull(addressBook);
        this.eventStorage = Objects.requireNonNull(eventStorage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getMaxGeneration() {
        return eventStorage.getMaxGeneration();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public List<EventImpl> getEvents(final long startGeneration, final int numGenerations) {
        return eventStorage.getNonAncientEvents().stream()
                .filter(e ->
                        e.getGeneration() >= startGeneration && e.getGeneration() < startGeneration + numGenerations)
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public AddressBook getAddressBook() {
        return addressBook;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReady() {
        return true;
    }
}
