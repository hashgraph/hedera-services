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

package com.swirlds.platform.gui.hashgraph.internal;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.JPanel;

/**
 * Used to create a {@link JPanel} that holds a {@link HashgraphPicture} and {@link HashgraphGuiControls} to change the
 * picture
 */
public final class PairPanel {
    private PairPanel() {}

    public static JPanel create(final JPanel controls, final JPanel picture) {
        final JPanel pairPanel = new JPanel();
        pairPanel.setLayout(new GridBagLayout());
        pairPanel.setBackground(Color.WHITE);
        pairPanel.setVisible(true);
        final GridBagConstraints c3 = new GridBagConstraints();
        c3.anchor = GridBagConstraints.FIRST_LINE_START; // left align each component in its cell
        c3.gridx = 0;
        c3.gridy = 0;
        c3.gridwidth = GridBagConstraints.RELATIVE;
        c3.gridheight = GridBagConstraints.REMAINDER;
        c3.fill = GridBagConstraints.BOTH;
        c3.weightx = 0; // don't put extra space in the checkbox side
        c3.weighty = 0;
        pairPanel.add(controls, c3);
        c3.gridx = 1;
        c3.gridwidth = GridBagConstraints.REMAINDER;
        c3.gridheight = GridBagConstraints.REMAINDER;
        c3.weightx = 1.0f;
        c3.weighty = 1.0f;
        c3.fill = GridBagConstraints.BOTH;
        pairPanel.add(picture, c3);

        /////////////////// create spacer ///////////////////
        final JPanel spacer = new JPanel();
        spacer.setBackground(Color.YELLOW);
        spacer.setVisible(true);

        picture.setVisible(true);

        return pairPanel;
    }
}
