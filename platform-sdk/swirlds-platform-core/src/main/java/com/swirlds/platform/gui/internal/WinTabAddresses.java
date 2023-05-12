/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gui.internal;

import static com.swirlds.platform.gui.internal.BrowserWindowManager.getPlatforms;
import static com.swirlds.platform.gui.internal.GuiUtils.wrap;

import com.swirlds.common.system.Platform;
import com.swirlds.common.system.address.Address;
import java.util.Arrays;
import javax.swing.JTextArea;

/**
 * The tab in the Browser window that shows available apps, running swirlds, and saved swirlds.
 */
class WinTabAddresses extends PrePaintableJPanel {
    private static final long serialVersionUID = 1L;
    /** the entire table is in this single Component */
    private JTextArea text;
    /** should the entire window be rebuilt? */
    private boolean redoWindow = true;

    /**
     * Instantiate and initialize content of this tab.
     */
    public WinTabAddresses() {
        text = WinBrowser.newJTextArea();
        add(text);
    }

    /** {@inheritDoc} */
    public void prePaint() {
        if (!redoWindow) {
            return;
        }
        redoWindow = false;
        String s = "";
        synchronized (getPlatforms()) {
            for (Platform p : getPlatforms()) {
                final Address address = p.getSelfAddress();
                s += "\n" + address.getId() + "   " + address.getNickname()
                        + "   " + address.getSelfName()
                        + "   " + Arrays.toString(address.getAddressInternalIpv4())
                        + "   " + address.getPortInternalIpv4()
                        + "   " + Arrays.toString(address.getAddressExternalIpv4())
                        + "   " + address.getPortExternalIpv4();
            }
        }
        s += wrap(
                70,
                "\n\n"
                        + "The above are all the member addresses. "
                        + "Each address includes the nickname, name, "
                        + "internal IP address/port and external IP address/port.");

        text.setText(s);
    }
}
