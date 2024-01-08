/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gui.model;

import javax.swing.JPanel;

/**
 * The class that all 4 levels of the hierarchy inherit from: InfoApp, InfoSwirld, InfoMember, InfoState. This holds
 * information common to all of them, such as the name, and the GUI component that represents it in the browser window.
 */
public class InfoEntity {

    /** name of this entity */
    private final String name;

    /** the JPanel that shows this entity in the browser window (Swirlds tab), or null if none */
    private JPanel panel;

    public InfoEntity(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public JPanel getPanel() {
        return panel;
    }

    public void setPanel(JPanel panel) {
        this.panel = panel;
    }
}
