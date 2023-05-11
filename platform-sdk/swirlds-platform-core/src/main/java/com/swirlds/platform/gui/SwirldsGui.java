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

package com.swirlds.platform.gui;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.platform.gui.internal.GuiUtils.winRect;

import com.swirlds.common.Console;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gui.internal.SwirldMenu;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import javax.swing.WindowConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utilities for creating GUI elements.
 */
public final class SwirldsGui {

    private static final Logger logger = LogManager.getLogger(SwirldsGui.class);

    private static final Map<Long, String> aboutStrings = new ConcurrentHashMap<>();
    private static final Map<Long, String> platformNames = new ConcurrentHashMap<>();
    private static final Map<Long, byte[]> swirldIds = new ConcurrentHashMap<>();
    private static final Map<Long, Integer> instanceNumbers = new ConcurrentHashMap<>();
    private static final Map<Long, ShadowGraph> shadowGraphs = new ConcurrentHashMap<>();
    private static final Map<Long, StateManagementComponent> stateManagementComponents = new ConcurrentHashMap<>();
    private static final Map<Long, AtomicReference<Consensus>> consensusReferences = new ConcurrentHashMap<>();

    private SwirldsGui() {}

    /**
     * Create a new window with a text console, of the recommended size and location, including the Swirlds menu.
     *
     * @param platform the platform to create the console with
     * @param visible  should the window be initially visible? If not, call setVisible(true) later.
     * @return the new window
     */
    public static Console createConsole(final Platform platform, final boolean visible) {

        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }

        final AddressBook addressBook = platform.getAddressBook();
        final long selfId = platform.getSelfId().getId();
        final int winNum = SwirldsGui.getInstanceNumber(platform.getSelfId().getId());

        final Rectangle winRect = winRect(addressBook, winNum);
        // if SwirldMain calls createConsole, this remembers the window created
        final Console console = new Console(addressBook.getAddress(selfId).getSelfName(), winRect);
        console.getWindow().setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        SwirldMenu.addTo(platform, console.getWindow(), 40, Color.white, false);
        console.setVisible(visible);
        return console;
    }

    /**
     * Create a new window of the recommended size and location, including the Swirlds menu.
     *
     * @param visible should the window be initially visible? If not, call setVisible(true) later.
     * @return the new window
     */
    public static JFrame createWindow(final Platform platform, final boolean visible) {

        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }

        final AddressBook addressBook = platform.getAddressBook();
        final long selfId = platform.getSelfId().getId();
        final int winNum = SwirldsGui.getInstanceNumber(platform.getSelfId().getId());

        final Rectangle winRect = winRect(addressBook, winNum);

        JFrame frame = null;
        try {
            final Address addr = addressBook.getAddress(selfId);
            final String name = addr.getSelfName();
            frame = new JFrame(name); // create a new window

            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.setBackground(Color.DARK_GRAY);
            frame.setSize(winRect.width, winRect.height);
            frame.setPreferredSize(new Dimension(winRect.width, winRect.height));
            frame.setLocation(winRect.x, winRect.y);
            SwirldMenu.addTo(platform, frame, 40, Color.BLUE, false);
            frame.setFocusable(true);
            frame.requestFocusInWindow();
            frame.setVisible(visible); // show it
        } catch (final Exception e) {
            logger.error(EXCEPTION.getMarker(), "", e);
        }

        return frame;
    }

    /**
     * The SwirldMain calls this to set the string that is shown when the user chooses "About" from the Swirlds menu in
     * the upper-right corner of the window. It is recommended that this be a short string that includes the name of the
     * app, the version number, and the year.
     *
     * @param nodeId the ID of the node
     * @param about  wha should show in the "about" window from the menu
     */
    public static void setAbout(final long nodeId, final String about) {
        aboutStrings.put(nodeId, about);
    }

    /**
     * Get the "about" string, or an empty string if none has been set.
     *
     * @param nodeId the ID of the node
     * @return an "about" string
     * @deprecated this method is deprecated and will be removed in a future release
     */
    @Deprecated(forRemoval = true)
    public static String getAbout(final long nodeId) {
        return aboutStrings.getOrDefault(nodeId, "");
    }

    /**
     * Set a platform name, given the node ID.
     *
     * @param nodeId       the ID of the node
     * @param platformName a platform name
     */
    public static void setPlatformName(final long nodeId, @NonNull final String platformName) {
        platformNames.put(nodeId, platformName);
    }

    /**
     * Get a platform name, given the node ID, or an empty string if none has been set.
     *
     * @param nodeId the ID of the node
     * @return a platform name
     * @deprecated this method is deprecated and will be removed in a future release
     */
    @Deprecated(forRemoval = true)
    @NonNull
    public static String getPlatformName(final long nodeId) {
        return platformNames.getOrDefault(nodeId, "");
    }

    /**
     * Set the swirld ID for a node.
     * @param nodeId the ID of the node
     * @param swirldId the swirld ID
     */
    public static void setSwirldId(final long nodeId, @NonNull final byte[] swirldId) {
        swirldIds.put(nodeId, swirldId);
    }

    /**
     * Get the swirld ID for a node, or null if none is set.
     * @param nodeId the ID of the node
     * @return the swirld ID
     * @deprecated this method is deprecated and will be removed in a future release
     */
    @Deprecated(forRemoval = true)
    @Nullable
    public static byte[] getSwirldId(final long nodeId) {
        return swirldIds.getOrDefault(nodeId, null);
    }

    /**
     * Set the instance number for a node.
     * @param nodeId the ID of the node
     * @param instanceNumber the instance number
     */
    public static void setInstanceNumber(final long nodeId, final int instanceNumber) {
        instanceNumbers.put(nodeId, instanceNumber);
    }

    /**
     * Get the instance number for a node, or -1 if none is set.
     * @param nodeId the ID of the node
     * @return the instance number
     * @deprecated this method is deprecated and will be removed in a future release
     */
    @Deprecated(forRemoval = true)
    public static int getInstanceNumber(final long nodeId) {
        return instanceNumbers.getOrDefault(nodeId, -1);
    }

    /**
     * Set the shadow graph for a node.
     * @param nodeId the ID of the node
     * @param shadowGraph the shadow graph
     */
    public static void setShadowGraph(final long nodeId, @NonNull final ShadowGraph shadowGraph) {
        shadowGraphs.put(nodeId, shadowGraph);
    }

    /**
     * Get the shadow graph for a node, or null if none is set.
     * @param nodeId the ID of the node
     * @return the shadow graph
     * @deprecated this method is deprecated and will be removed in a future release
     */
    @Deprecated(forRemoval = true)
    @Nullable
    public static ShadowGraph getShadowGraph(final long nodeId) {
        return shadowGraphs.getOrDefault(nodeId, null);
    }

    /**
     * Set the state management component for a node.
     * @param nodeId the ID of the node
     * @param stateManagementComponent the state management component
     */
    public static void setStateManagementComponent(
            final long nodeId, @NonNull final StateManagementComponent stateManagementComponent) {
        stateManagementComponents.put(nodeId, stateManagementComponent);
    }

    /**
     * Get the state management component for a node, or null if none is set.
     * @param nodeId the ID of the node
     * @return the state management component
     * @deprecated this method is deprecated and will be removed in a future release
     */
    @Deprecated(forRemoval = true)
    @Nullable
    public static StateManagementComponent getStateManagementComponent(final long nodeId) {
        return stateManagementComponents.getOrDefault(nodeId, null);
    }

    /**
     * Set the consensus for a node.
     * @param nodeId the ID of the node
     * @param consensus the consensus
     */
    public static void setConsensusReference(final long nodeId, @NonNull final AtomicReference<Consensus> consensus) {
        consensusReferences.put(nodeId, consensus);
    }

    /**
     * Get the consensus for a node, or null if none is set.
     * @param nodeId the ID of the node
     * @return the consensus
     * @deprecated this method is deprecated and will be removed in a future release
     */
    @Deprecated(forRemoval = true)
    @Nullable
    public static Consensus getConsensus(final long nodeId) {
        final AtomicReference<Consensus> consensusReference = consensusReferences.getOrDefault(nodeId, null);
        if (consensusReference == null) {
            return null;
        }
        return consensusReference.get();
    }
}
