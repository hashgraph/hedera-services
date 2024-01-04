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

package com.swirlds.platform.gui.components;

import javax.swing.JScrollPane;

/**
 * A JScrollPane containing the given UpdatableJPanel.
 *
 * @author Leemon
 */
public class ScrollableJPanel extends JScrollPane {
    private static final long serialVersionUID = 1L;
    private final PrePaintableJPanel contents;

    /**
     * Wrap the given panel in scroll bars, and remember it so that calls to prePaint() can be passed on to it.
     */
    public ScrollableJPanel(PrePaintableJPanel contents) {
        super(contents);
        this.contents = contents;
        contents.setVisible(true);
    }

    /**
     * Recalculate the contents of each Component, maybe slowly, so that the next repaint() will trigger a fast render
     * of everything.
     */
    public void prePaint() {
        if (contents != null) {
            contents.prePaint();
        }
    }

    public PrePaintableJPanel getContents() {
        return contents;
    }
}
