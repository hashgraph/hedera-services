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
import com.swirlds.platform.gui.internal.SwirldMenu;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.JFrame;
import javax.swing.WindowConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utilities for creating GUI elements.
 */
public final class SwirldsGui {

    private static final Logger logger = LogManager.getLogger(SwirldsGui.class);

    private static Map<Long, String> aboutStrings = new ConcurrentHashMap<>();

    private SwirldsGui() {}

    /**
     * Create a new window with a text console, of the recommended size and location, including the Swirlds
     * menu.
     *
     * @param platform
     * 		the platform to create the console with
     * @param visible
     * 		should the window be initially visible? If not, call setVisible(true) later.
     * @return the new window
     */
    public static Console createConsole(final Platform platform, final boolean visible) {

        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }

        final AddressBook addressBook = platform.getAddressBook();
        final long selfId = platform.getSelfId().getId();
        final int winNum = platform.getInstanceNumber();

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
     * @param visible
     * 		should the window be initially visible? If not, call setVisible(true) later.
     * @return the new window
     */
    public static JFrame createWindow(final Platform platform, final boolean visible) {

        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }

        final AddressBook addressBook = platform.getAddressBook();
        final long selfId = platform.getSelfId().getId();
        final int winNum = platform.getInstanceNumber();

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
     * The SwirldMain calls this to set the string that is shown when the user chooses "About" from the
     * Swirlds menu in the upper-right corner of the window. It is recommended that this be a short string
     * that includes the name of the app, the version number, and the year.
     *
     * @param nodeId
     * 		the ID of the node
     * @param about
     * 		wha should show in the "about" window from the menu
     */
    public static void setAbout(final long nodeId, final String about) {
        aboutStrings.put(nodeId, about);
    }

    /**
     * Get the "about" string, or an empty string if none has been set.
     *
     * @param nodeId
     * 		the ID of the node
     * @return an "about" string
     */
    public static String getAbout(final long nodeId) {
        return aboutStrings.getOrDefault(nodeId, "");
    }
}
