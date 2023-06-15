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

package com.swirlds.gui;

import java.awt.Insets;
import javax.swing.JTextArea;

@Deprecated(forRemoval = true)
public class WindowManager {

    /**
     * the number of pixels between the edges of a window and interior region that can be used
     */
    private static Insets insets;

    /**
     * metadata about all known apps, swirlds, members, signed states
     */
    private static StateHierarchy stateHierarchy = null;

    /** the InfoMember that is currently being shown by all tabs in the browser window */
    public static volatile InfoMember memberDisplayed = null;

    /** the nickname and name of the member on local machine currently being viewed */
    public static JTextArea nameBarName;

    /**
     * Get metadata about all known apps, swirlds, members, signed states.
     */
    public static StateHierarchy getStateHierarchy() {
        return stateHierarchy;
    }

    /**
     * Set metadata about all known apps, swirlds, members, signed states.
     */
    public static void setStateHierarchy(final StateHierarchy stateHierarchy) {
        WindowManager.stateHierarchy = stateHierarchy;
    }

    /**
     * Get the number of pixels between the edges of a window and interior region that can be used.
     */
    public static Insets getInsets() {
        return insets;
    }

    /**
     * Set the number of pixels between the edges of a window and interior region that can be used.
     */
    public static void setInsets(final Insets insets) {
        WindowManager.insets = insets;
    }
}
