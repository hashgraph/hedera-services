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

import java.awt.FlowLayout;
import java.io.Serial;
import javax.swing.JPanel;

/**
 * A JPanel with an additional method prePaint(), which (possibly slowly) recalculates everything, so
 * the next repaint() will (quickly) render everything.
 */
public abstract class PrePaintableJPanel extends JPanel {
    @Serial
    private static final long serialVersionUID = 1L;

    { // by default, align everything left
        this.setLayout(new FlowLayout(FlowLayout.LEFT));
    }

    /**
     * Recalculate the contents of each Component, maybe slowly, so that the next repaint() will trigger
     * a fast render of everything. If this contains any other UpdatableJPanels, then it must call their
     * prePaint(), too.
     */
    public abstract void prePaint();
}
