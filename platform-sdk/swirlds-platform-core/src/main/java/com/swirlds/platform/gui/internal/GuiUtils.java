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

import static com.swirlds.platform.gui.internal.BrowserWindowManager.getInsets;
import static com.swirlds.platform.state.address.AddressBookUtils.getOwnHostCount;

import com.swirlds.common.system.address.AddressBook;
import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import javax.swing.JTextArea;
import javax.swing.text.DefaultCaret;
import org.apache.commons.lang3.SystemUtils;

/**
 * Miscellaneous GUI utility methods.
 */
public final class GuiUtils {

    private GuiUtils() {}

    /**
     * Insert line breaks into the given string so that each line is at most len characters long (not
     * including trailing whitespace and the line break iteslf). Line breaks are only inserted after a
     * whitespace character and before a non-whitespace character, resulting in some lines possibly being
     * shorter. If a line has no whitespace, then it will insert between two non-whitespace characters to
     * make it exactly len characters long.
     *
     * @param len
     * 		the desired length
     * @param str
     * 		the input string, where a newline is always just \n (never \n\r or \r or \r\n)
     */
    public static String wrap(final int len, final String str) {
        StringBuilder ans = new StringBuilder();
        String[] lines = str.split("\n"); // break into lines
        for (String line : lines) { // we'll add \n to end of every line, then strip off the last
            if (line.length() == 0) {
                ans.append('\n');
            }
            char[] c = line.toCharArray();
            int i = 0;
            while (i < c.length) { // repeatedly add a string starting at i, followed by a \n
                int j = i + len;
                if (j >= c.length) { // grab the rest of the characters
                    j = c.length;
                } else if (Character.isWhitespace(c[j])) { // grab more than len characters
                    while (j < c.length && Character.isWhitespace(c[j])) {
                        j++;
                    }
                } else { // grab len or fewer characters
                    while (j >= i && !Character.isWhitespace(c[j])) {
                        j--;
                    }
                    if (j < i) { // there is no whitespace before len
                        j = i + len;
                    } else { // the last whitespace before len is at j
                        j++;
                    }
                }
                ans.append(c, i, j - i); // append c[i...j-1]
                ans.append('\n');
                i = j; // continue, starting at j
            }
        }
        return ans.substring(0, ans.length() - 1); // remove the last '\n' that was added
    }

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
        final int contentWidth =
                (screenSize.width - leftGap - rightGap - getInsets().left - getInsets().right) / winCount;
        final int x = screenSize.x + leftGap + contentWidth * winNum;
        final int y = screenSize.y;
        final int width = contentWidth + getInsets().left + getInsets().right;
        final int height = screenSize.height;
        return new Rectangle(x, y, width, height);
    }

    /**
     * Instantiates and returns a JTextArea whose settings are suitable for use inside the browser window's
     * scroll area in a tab.
     */
    public static JTextArea newJTextArea(final String text) {
        JTextArea txt = new JTextArea(0, 0);
        ((DefaultCaret) txt.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        txt.setBackground(Color.WHITE);
        txt.setForeground(Color.BLACK);
        txt.setDisabledTextColor(Color.BLACK);
        txt.setFont(WinBrowser.FONT);
        txt.setEditable(false);
        txt.setEnabled(false);
        txt.setText(text);
        txt.setVisible(true);
        return txt;
    }
}
