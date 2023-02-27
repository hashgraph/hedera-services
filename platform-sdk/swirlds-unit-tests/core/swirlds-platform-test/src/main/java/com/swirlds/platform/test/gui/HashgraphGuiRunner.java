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
package com.swirlds.platform.test.gui;

import com.swirlds.platform.gui.hashgraph.HashgraphGui;
import com.swirlds.platform.gui.hashgraph.HashgraphGuiSource;
import java.awt.BorderLayout;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JComponent;
import javax.swing.JFrame;

/** Helper class to open a {@link HashgraphGui} window */
public final class HashgraphGuiRunner {
    private HashgraphGuiRunner() {}

    public static void runHashgraphGui(final HashgraphGuiSource guiSource) {
        runHashgraphGui(guiSource, null);
    }

    /**
     * Open a {@link HashgraphGui} window that draws data from the source provided
     *
     * @param guiSource the source for the GUI
     * @param additionalControls additional controls that will be added to the bottom of the screen
     */
    public static void runHashgraphGui(
            final HashgraphGuiSource guiSource, final JComponent additionalControls) {
        final JFrame frame = new JFrame();
        final CloseDetector closeDetector = new CloseDetector();
        frame.addWindowListener(closeDetector);
        frame.setExtendedState(frame.getExtendedState() | JFrame.MAXIMIZED_BOTH);
        frame.setLayout(new BorderLayout());
        frame.setFocusable(true);
        frame.requestFocusInWindow();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        final HashgraphGui hashgraphGui = new HashgraphGui(guiSource);
        hashgraphGui.reloadSource();

        frame.add(hashgraphGui, BorderLayout.NORTH);
        if (additionalControls != null) {
            frame.add(additionalControls, BorderLayout.SOUTH);
        }

        frame.setVisible(true);

        while (!closeDetector.isClosed()) {
            // gets new events
            hashgraphGui.reloadSource();
            hashgraphGui.repaint();
            try {
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }

    private static class CloseDetector implements WindowListener {
        private final AtomicBoolean isClosed = new AtomicBoolean(false);

        public boolean isClosed() {
            return isClosed.get();
        }

        @Override
        public void windowOpened(final WindowEvent e) {
            // no-op
        }

        @Override
        public void windowClosing(final WindowEvent e) {
            isClosed.set(true);
        }

        @Override
        public void windowClosed(final WindowEvent e) {
            isClosed.set(true);
        }

        @Override
        public void windowIconified(final WindowEvent e) {
            // no-op
        }

        @Override
        public void windowDeiconified(final WindowEvent e) {
            // no-op
        }

        @Override
        public void windowActivated(final WindowEvent e) {
            // no-op
        }

        @Override
        public void windowDeactivated(final WindowEvent e) {
            // no-op
        }
    }
}
