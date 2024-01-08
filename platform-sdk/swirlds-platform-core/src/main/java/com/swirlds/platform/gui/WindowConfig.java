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

package com.swirlds.platform.gui;

import java.awt.Insets;

public class WindowConfig {

    /**
     * the number of pixels between the edges of a window and interior region that can be used
     */
    private static Insets insets;

    private WindowConfig() {}

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
        WindowConfig.insets = insets;
    }
}
