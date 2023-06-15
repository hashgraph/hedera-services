/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.swirlds.platform.gui.internal.PlatformHashgraphGuiSource;
import java.awt.GraphicsEnvironment;

/**
 * Manages static variables for the browser GUI window.
 */
public final class BrowserWindowManager {

    private BrowserWindowManager() {}

    /**
     * the primary window used by Browser
     */
    private static WinBrowser browserWindow = null;

    /**
     * Get the primary window used by Browser.
     */
    public static WinBrowser getBrowserWindow() {
        return browserWindow;
    }

    /**
     * Set the primary window used by Browser.
     */
    public static void setBrowserWindow(final WinBrowser browserWindow) {
        BrowserWindowManager.browserWindow = browserWindow;
    }

    /**
     * Make the browser window visible. If it doesn't yet exist, then create it. Then switch to the given
     * tab, with a component name of the form Browser.browserWindow.tab* such as
     * Browser.browserWindow.tabCalls to switch to the "Calls" tab.
     *
     * @param comp
     * 		the index of the tab to select
     */
    public static void showBrowserWindow(final WinBrowser.ScrollableJPanel comp) {
        showBrowserWindow();
        getBrowserWindow().goTab(comp);
    }

    /**
     * Make the browser window visible. If it doesn't yet exist, then create it.
     */
    public static void showBrowserWindow() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        if (getBrowserWindow() != null) {
            getBrowserWindow().setVisible(true);
            return;
        }
        setBrowserWindow(new WinBrowser(new PlatformHashgraphGuiSource()));
    }
}
