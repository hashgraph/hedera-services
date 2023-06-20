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

package com.swirlds.platform.gui.internal;

import static com.swirlds.platform.state.address.AddressBookUtils.getOwnHostCount;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.gui.WindowManager;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import org.apache.commons.lang3.SystemUtils;

/**
 * Miscellaneous GUI utility methods.
 */
public final class AddressBookGuiUtils {

    private AddressBookGuiUtils() {}

    /**
     * return the rectangle of the recommended window size and location for this instance of the Platform.
     * Both consoles and windows are created to fit in this rectangle, by default.
     *
     * @param addressBook
     * 		the address book for the network
     * @param winNum
     * 		this is the Nth Platform running on this machine (N=winNum)
     * @return the recommended Rectangle for this Platform's window
     */
    public static Rectangle winRect(final AddressBook addressBook, final int winNum) {
        // the goal is to arrange windows on the screen so that the leftmost and rightmost windows just
        // touch the edge of the screen with their outermost border. But the rest of the windows overlap
        // with them and with each other such that all of the border of one window (ecept 2 pixels) overlaps
        // the content of its neighbors. This should look fine on any OS where the borders are thin (i.e.,
        // all except Windows 10), and should also look good on Windows 10, by making the invisible borders
        // overlap the adjacent window rather than looking like visible gaps.
        // In addition, extra space is added to either the left or right side of the
        // screen, whichever is likely to have the close button for the Browser window that lies behind the
        // Platform windows.

        final int leftGap = (SystemUtils.IS_OS_WINDOWS ? 0 : 25); // extra space at left screen edge
        final int rightGap = (SystemUtils.IS_OS_WINDOWS ? 50 : 0); // extra space at right screen edge
        final Rectangle screenSize =
                GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        final int winCount = getOwnHostCount(addressBook);
        final int contentWidth = (screenSize.width
                        - leftGap
                        - rightGap
                        - WindowManager.getInsets().left
                        - WindowManager.getInsets().right)
                / winCount;
        final int x = screenSize.x + leftGap + contentWidth * winNum;
        final int y = screenSize.y;
        final int width = contentWidth + WindowManager.getInsets().left + WindowManager.getInsets().right;
        final int height = screenSize.height;
        return new Rectangle(x, y, width, height);
    }
}
