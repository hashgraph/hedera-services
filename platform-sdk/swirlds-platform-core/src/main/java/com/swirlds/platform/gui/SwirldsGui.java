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
import static com.swirlds.platform.gui.internal.AddressBookGuiUtils.winRect;

import com.swirlds.common.Console;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.gui.GuiAccessor;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import javax.swing.JFrame;
import javax.swing.WindowConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utilities for creating GUI elements.
 */
public final class SwirldsGui {

    private static final Logger logger = LogManager.getLogger(SwirldsGui.class);

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
        final NodeId selfId = platform.getSelfId();
        final int winNum = GuiAccessor.getInstance().getInstanceNumber(selfId);

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
        final NodeId selfId = platform.getSelfId();
        final int winNum = GuiAccessor.getInstance().getInstanceNumber(selfId);

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
}
