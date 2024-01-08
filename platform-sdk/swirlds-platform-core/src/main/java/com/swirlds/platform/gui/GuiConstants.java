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

import java.awt.Color;
import java.awt.Font;

public class GuiConstants {

    /** use this font for all text in the browser window */
    public static final Font FONT = new Font("SansSerif", Font.PLAIN, 16);

    /** light blue used to highlight which member all the tabs are currently displaying */
    public static final Color MEMBER_HIGHLIGHT_COLOR = new Color(0.8f, 0.9f, 1.0f);

    private GuiConstants() {}
}
